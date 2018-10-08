package org.jerkar.tool;

import org.jerkar.api.depmanagement.*;
import org.jerkar.api.file.JkPathMatcher;
import org.jerkar.api.file.JkPathSequence;
import org.jerkar.api.file.JkPathTree;
import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaCompileSpec;
import org.jerkar.api.java.JkJavaCompiler;
import org.jerkar.api.system.JkException;
import org.jerkar.api.system.JkLocator;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.*;
import org.jerkar.tool.CommandLine.MethodInvocation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Engine having responsibility of compiling build classes, instantiate and run them.<br/>
 * Build class sources are expected to lie in [project base dir]/build/def <br/>
 * Classes having simple name starting with '_' are ignored.
 *
 * Build classes can have dependencies on jars : <ul>
 *     <li>located in [base dir]/build/boot directory</li>
 *     <li>declared in {@link JkImport} annotation</li>
 * </ul>
 *
 */
final class Engine {

    private final JkPathMatcher BUILD_SOURCE_MATCHER = JkPathMatcher.accept("**.java").andRefuse("**/_*", "_*");

    private final Path projectBaseDir;

    private JkDependencySet runDependencies;

    private JkRepoSet runRepos;

    private List<Path> rootsOfImportedRuns = new LinkedList<>();

    private final BuildResolver resolver;

    /**
     * Constructs an engine for the specified base directory.
     */
    Engine(Path baseDir) {
        super();
        JkUtilsAssert.isTrue(baseDir.isAbsolute(), baseDir + " is not absolute.");
        JkUtilsAssert.isTrue(Files.isDirectory(baseDir), baseDir + " is not directory.");
        this.projectBaseDir = baseDir.normalize();
        runRepos = repos();
        this.runDependencies = JkDependencySet.of();
        this.resolver = new BuildResolver(baseDir);
    }

    <T extends JkRun> T getBuild(Class<T> baseClass) {
        if (resolver.needCompile()) {
            this.compile();
        }
        return resolver.resolve(baseClass);
    }

    /**
     * Pre-compile and compile build classes (if needed) then execute methods mentioned in command line
     */
    void execute(CommandLine commandLine, String buildClassHint, JkLog.Verbosity verbosityToRestore) {
        runDependencies = runDependencies.andUnscoped(commandLine.dependencies());
        long start = System.nanoTime();
        JkLog.startTask("Compile and initialise run classes");
        JkRun build = null;
        JkPathSequence path = JkPathSequence.of();
        if (!commandLine.dependencies().isEmpty()) {
            final JkPathSequence cmdPath = pathOf(commandLine.dependencies());
            path = path.andManyFirst(cmdPath);
            JkLog.trace("Command line extra path : " + cmdPath);
        }
        if (!JkUtilsString.isBlank(buildClassHint)) {  // First find a class in the existing classpath without compiling
            build = getBuildInstance(buildClassHint, path);
        }
        if (build == null) {
            path = compile().andMany(path);
            build = getBuildInstance(buildClassHint, path);
        }
        if (build == null) {
            throw new JkException("Can't find or guess any run class for project hosted in " + this.projectBaseDir
                    + " .\nAre you sure this directory is a Jerkar project ?");
        }
        JkLog.endTask("Done in " + JkUtilsTime.durationInMillis(start) + " milliseconds.");
        JkLog.info("Jerkar run is ready to start.");
        JkLog.setVerbosity(verbosityToRestore);
        try {
            this.launch(build, commandLine);
        } catch (final RuntimeException e) {
            JkLog.error("Engine " + projectBaseDir + " failed");
            throw e;
        }
    }

    private JkPathSequence pathOf(List<? extends JkDependency> dependencies) {
        JkDependencySet deps = JkDependencySet.of();
        for (JkDependency dependency : dependencies) {
            deps = deps.and(dependency);
        }
        return JkDependencyResolver.of(this.runRepos).get(deps);
    }

    private void preCompile() {
        List<Path> sourceFiles = JkPathTree.of(resolver.runSourceDir).andMatcher(BUILD_SOURCE_MATCHER).files();
        final SourceParser parser = SourceParser.of(this.projectBaseDir, sourceFiles);
        this.runDependencies = this.runDependencies.and(parser.dependencies());
        this.runRepos = parser.importRepos().and(runRepos);
        this.rootsOfImportedRuns = parser.projects();
    }

    // Compiles and returns the runtime classpath
    private JkPathSequence compile() {
        final LinkedHashSet<Path> entries = new LinkedHashSet<>();
        compile(new HashSet<>(), entries);
        return JkPathSequence.ofMany(entries).withoutDuplicates();
    }

    private void compile(Set<Path>  yetCompiledProjects, LinkedHashSet<Path>  path) {
        if (!this.resolver.hasBuildSource() || yetCompiledProjects.contains(this.projectBaseDir)) {
            return;
        }
        yetCompiledProjects.add(this.projectBaseDir);
        preCompile(); // This enrich dependencies
        String msg = "Compiling run classes for project " + this.projectBaseDir.getFileName().toString();
        long start = System.nanoTime();
        JkLog.startTask(msg);
        final JkDependencyResolver runDependencyResolver = getRunDependencyResolver();
        final JkPathSequence runPath = runDependencyResolver.get(this.computeRunDependencies());
        path.addAll(runPath.entries());
        path.addAll(compileDependentProjects(yetCompiledProjects, path).entries());
        this.compileBuild(JkPathSequence.ofMany(path));
        path.add(this.resolver.runClassDir);
        JkLog.endTask("Done in " + JkUtilsTime.durationInMillis(start) + " milliseconds.");
    }

    private JkRun getBuildInstance(String buildClassHint, JkPathSequence runtimePath) {
        final JkClassLoader classLoader = JkClassLoader.current();
        classLoader.addEntries(runtimePath);
        JkLog.trace("Setting run execution classpath to : " + classLoader.childClasspath());
        final JkRun run = resolver.resolve(buildClassHint);
        if (run == null) {
            return null;
        }
        try {
            run.setBuildDefDependencyResolver(this.computeRunDependencies(), getRunDependencyResolver());
            return run;
        } catch (final RuntimeException e) {
            JkLog.error("Engine " + projectBaseDir + " failed");
            throw e;
        }
    }

    private JkDependencySet computeRunDependencies() {

        // If true, we assume Jerkar is provided by IDE (development mode)
        final boolean devMode = Files.isDirectory(JkLocator.jerkarJarPath());
        return JkDependencySet.of(runDependencies
                .andFiles(localBuildPath())
                .andFiles(JkClasspath.current()).onlyIf(devMode)
                .andFiles(jerkarLibs()).onlyIf(!devMode)
                .withDefaultScope(JkScopeMapping.ALL_TO_DEFAULT));
    }

    private JkPathSequence localBuildPath() {
        final List<Path>  extraLibs = new LinkedList<>();
        final Path localDefLibDir = this.projectBaseDir.resolve(JkConstants.BOOT_DIR);
        if (Files.exists(localDefLibDir)) {
            extraLibs.addAll(JkPathTree.of(localDefLibDir).andAccept("**.jar").files());
        }
        return JkPathSequence.ofMany(extraLibs).withoutDuplicates();
    }

    private JkPathSequence compileDependentProjects(Set<Path> yetCompiledProjects, LinkedHashSet<Path>  pathEntries) {
        JkPathSequence pathSequence = JkPathSequence.of();
        if (!this.rootsOfImportedRuns.isEmpty()) {
            JkLog.info("Compile run classes of dependent projects : "
                        + toRelativePaths(this.projectBaseDir, this.rootsOfImportedRuns));
        }
        for (final Path file : this.rootsOfImportedRuns) {
            final Engine engine = new Engine(file.toAbsolutePath().normalize());
            engine.compile(yetCompiledProjects, pathEntries);
            pathSequence = pathSequence.and(file);
        }
        return pathSequence;
    }

    private void compileBuild(JkPathSequence buildPath) {
        JkJavaCompileSpec compileSpec = buildCompileSpec().setClasspath(buildPath);
        JkJavaCompiler.of().compile(compileSpec);
        JkPathTree.of(this.resolver.runSourceDir).andRefuse("**/*.java").copyTo(this.resolver.runClassDir,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void launch(JkRun build, CommandLine commandLine) {
        if (!commandLine.getSubProjectMethods().isEmpty()) {
            for (final JkRun subBuild : build.importedRuns().all()) {
                runProject(subBuild, commandLine.getSubProjectMethods());
            }
            runProject(build, commandLine.getSubProjectMethods());
        }
        runProject(build, commandLine.getMasterMethods());
    }

    private JkJavaCompileSpec buildCompileSpec() {
        final JkPathTree buildSource = JkPathTree.of(resolver.runSourceDir).andMatcher(BUILD_SOURCE_MATCHER);
        JkUtilsPath.createDirectories(resolver.runClassDir);
        return new JkJavaCompileSpec().setOutputDir(resolver.runClassDir)
                .addSources(buildSource.files());
    }

    private JkDependencyResolver getRunDependencyResolver() {
        if (this.computeRunDependencies().containsModules()) {
            return JkDependencyResolver.of(this.runRepos);
        }
        return JkDependencyResolver.of();
    }

    private static JkPathSequence jerkarLibs() {
        final List<Path>  extraLibs = new LinkedList<>();
        extraLibs.add(JkLocator.jerkarJarPath());
        return JkPathSequence.ofMany(extraLibs).withoutDuplicates();
    }

    private static void runProject(JkRun build, List<MethodInvocation> invokes) {
        for (MethodInvocation methodInvocation : invokes) {
            invokeMethodOnBuildClassOrPlugin(build, methodInvocation);
        }
    }

    private static void invokeMethodOnBuildClassOrPlugin(JkRun build, MethodInvocation methodInvocation) {
        if (methodInvocation.pluginName != null) {
            final JkPlugin plugin = build.plugins().get(methodInvocation.pluginName);
            invokeMethodOnBuildOrPlugin(plugin, methodInvocation.methodName);
        } else {
            invokeMethodOnBuildOrPlugin(build, methodInvocation.methodName);
        }
    }

    /**
     * Invokes the specified method in this build.
     */
    private static void invokeMethodOnBuildOrPlugin(Object build, String methodName) {
        final Method method;
        try {
            method = build.getClass().getMethod(methodName);
        } catch (final NoSuchMethodException e) {
            throw new JkException("No public zero-arg method '" + methodName + "' found in class '" + build.getClass());
        }
        if (Environment.standardOptions.logHeaders) {
            JkLog.info("Method : " + methodName + " on " + build.getClass().getName());
        }
        final long time = System.nanoTime();
        try {
            JkUtilsReflect.invoke(build, method);
            if (Environment.standardOptions.logHeaders) {
                JkLog.info("Method " + methodName + " succeeded in "
                        + JkUtilsTime.durationInMillis(time) + " milliseconds.");
            }
        } catch (final RuntimeException e) {
            JkLog.info("Method " + methodName + " failed in " + JkUtilsTime.durationInMillis(time)
                        + " milliseconds.");
            throw e;
        }
    }

    private static JkRepoSet repos() {
        return JkRepoSet.of(JkRepoConfigOptionLoader.buildRepository(), JkRepo.local());
    }

    private static List<String> toRelativePaths(Path from, List<Path>  files) {
        final List<String> result = new LinkedList<>();
        for (final Path file : files) {
            final String relPath = from.relativize(file).toString();
            result.add(relPath);
        }
        return result;
    }

    @Override
    public String toString() {
        return this.projectBaseDir.getFileName().toString();
    }

}
