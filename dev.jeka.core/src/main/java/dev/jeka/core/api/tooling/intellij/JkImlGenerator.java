package dev.jeka.core.api.tooling.intellij;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkPathTreeSet;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.java.project.JkJavaProjectIde;
import dev.jeka.core.api.java.project.JkProjectSourceLayout;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.*;
import dev.jeka.core.tool.JkConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Provides method to generate and read Eclipse metadata files.
 */
public final class JkImlGenerator {

    private static final String ENCODING = "UTF-8";

    private static final String T1 = "  ";

    private static final String T2 = T1 + T1;

    private static final String T3 = T2 + T1;

    private static final String T4 = T3 + T1;

    private static final String T5 = T4 + T1;

    private JkProjectSourceLayout sourceLayout;

    private final Path baseDir;

    /** Used to generate JRE container */
    private JkJavaVersion sourceJavaVersion;

    /** Dependency resolver to fetch module dependencies */
    private JkDependencyResolver projectDependencyResolver;

    private JkDependencySet projectDependencies;

    /** Dependency resolver to fetch module dependencies for build classes */
    private JkDependencyResolver runDependencyResolver;

    private JkDependencySet runDependencies;

    /** Can be empty but not null */
    private Iterable<Path> importedProjects = JkUtilsIterable.listOf();

    private boolean forceJdkVersion;

    /* When true, path will be mentioned with $JEKA_HOME$ and $JEKA_REPO$ instead of explicit absolute path. */
    private boolean useVarPath;

    private final Set<String> paths = new HashSet<>();

    private XMLStreamWriter writer;

    private JkImlGenerator(Path baseDir) {
        this.baseDir = baseDir;
        this.projectDependencies = JkDependencySet.of();
        this.projectDependencyResolver = JkDependencyResolver.of();
    }

    /**
     * Constructs a {@link JkImlGenerator} to the project base directory
     */
    public static JkImlGenerator of(JkJavaProjectIde projectIde) {
        JkImlGenerator result = new JkImlGenerator(projectIde.getSourceLayout().getBaseDir());
        result.sourceLayout = projectIde.getSourceLayout();
        result.projectDependencies = projectIde.getDependencies();
        result.projectDependencyResolver = projectIde.getDependencyResolver();
        return result;
    }

    public static JkImlGenerator of(Path baseDir) {
        return new JkImlGenerator(baseDir);
    }

    /** Generate the .classpath file */
    public String generate() {
        try {
            return _generate();
        } catch (final Exception e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    private String _generate() throws IOException, XMLStreamException, FactoryConfigurationError {
        final ByteArrayOutputStream fos = new ByteArrayOutputStream();
        writer = createWriter(fos);
        writeHead();
        writeOutput();
        writeJdk();
        writeContent();
        writeOrderEntrySourceFolder();
        final Set<Path> allPaths = new HashSet<>();
        final Set<Path> allModules = new HashSet<>();
        if (this.projectDependencyResolver != null) {
            writeDependencies(projectDependencies, this.projectDependencyResolver, allPaths, allModules, false);
        }
        if (this.runDependencyResolver != null) {
            writeDependencies(this.runDependencies, this.runDependencyResolver, allPaths, allModules, true);
        }
        writeProjectImportDependencies(allModules);

        writeFoot();
        writer.close();
        return fos.toString(ENCODING);
    }

    private void writeHead() throws XMLStreamException {
        Path pluginXml = findPluginXml();
        boolean pluginModule = pluginXml != null;
        writer.writeStartDocument(ENCODING, "1.0");
        writer.writeCharacters("\n");
        writer.writeStartElement("module");
        writer.writeAttribute("type", pluginModule ? "PLUGIN_MODULE" : "JAVA_MODULE");
        writer.writeAttribute("version", "4");
        writer.writeCharacters("\n" + T1);
        if (pluginModule) {
            writer.writeEmptyElement("component");
            writer.writeAttribute("name", "DevKit.ModuleBuildProperties");
            writer.writeAttribute("url", "file://$MODULE_DIR$/" + this.baseDir.relativize(pluginXml)
                    .toString().replace("\\", "/"));
            writer.writeCharacters("\n"  + T1);
        }
        writer.writeStartElement("component");
        writer.writeAttribute("name", "NewModuleRootManager");
        writer.writeAttribute("inherit-compileRunner-output", "false");
        writer.writeCharacters("\n");
    }



    private void writeFoot() throws XMLStreamException {
        writer.writeCharacters(T1);
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
        writer.close();
    }

    private void writeOutput() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("output");
        writer.writeAttribute("url", "file://$MODULE_DIR$/.idea/output/production");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);
        writer.writeEmptyElement("output-test");
        writer.writeAttribute("url", "file://$MODULE_DIR$/.idea/output/test");
        writer.writeCharacters("\n");

        writer.writeCharacters(T2);
        writer.writeEmptyElement("exclude-output");
        writer.writeCharacters("\n");
    }

    private void writeContent() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeStartElement("content");
        writer.writeAttribute("url", "file://$MODULE_DIR$");
        writer.writeCharacters("\n");

        // Write build sources
        writer.writeCharacters(T3);
        writer.writeEmptyElement("sourceFolder");
        writer.writeAttribute("url", "file://$MODULE_DIR$/" + JkConstants.DEF_DIR);
        writer.writeAttribute("isTestSource", "true");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);

        if (sourceLayout != null) {

            // Write test sources
            final Path projectDir = this.sourceLayout.getBaseDir();
            for (final JkPathTree fileTree : this.sourceLayout.getTests().getPathTrees()) {
                if (fileTree.exists()) {
                    writer.writeCharacters(T1);
                    writer.writeEmptyElement("sourceFolder");

                    final String path = projectDir.relativize(fileTree.getRoot()).normalize().toString().replace('\\', '/');
                    writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                    writer.writeAttribute("isTestSource", "true");
                    writer.writeCharacters("\n");
                }
            }

            // write test resources
            for (final JkPathTree fileTree : this.sourceLayout.getTestResources().getPathTrees()) {
                if (fileTree.exists() && !contains(this.sourceLayout.getTests(), fileTree.getRootDirOrZipFile())) {
                    writer.writeCharacters(T3);
                    writer.writeEmptyElement("sourceFolder");
                    final String path = projectDir.relativize(fileTree.getRoot()).normalize().toString().replace('\\', '/');
                    writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                    writer.writeAttribute("type", "java-test-resource");
                    writer.writeCharacters("\n");
                }
            }

            // Write production sources

            for (final JkPathTree fileTree : this.sourceLayout.getSources().getPathTrees()) {
                if (fileTree.exists()) {
                    writer.writeCharacters(T3);
                    writer.writeEmptyElement("sourceFolder");
                    final String path = projectDir.relativize(fileTree.getRoot()).normalize().toString().replace('\\', '/');
                    writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                    writer.writeAttribute("isTestSource", "false");
                    writer.writeCharacters("\n");
                }
            }

            // Write production test resources
            for (final JkPathTree fileTree : this.sourceLayout.getResources().getPathTrees()) {
                if (fileTree.exists() && !contains(this.sourceLayout.getSources(), fileTree.getRootDirOrZipFile())) {
                    writer.writeCharacters(T3);
                    writer.writeEmptyElement("sourceFolder");
                    final String path = projectDir.relativize(fileTree.getRoot()).normalize().toString().replace('\\', '/');
                    writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                    writer.writeAttribute("type", "java-resource");
                    writer.writeCharacters("\n");
                }
            }

        }

        writer.writeCharacters(T3);
        writer.writeEmptyElement("excludeFolder");
        final String path = JkConstants.OUTPUT_PATH;
        writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
        writer.writeCharacters("\n");
        writer.writeCharacters(T3);
        writer.writeEmptyElement("excludeFolder");
        final String workPath = JkConstants.WORK_PATH;
        writer.writeAttribute("url", "file://$MODULE_DIR$/" + workPath);
        writer.writeCharacters("\n");
        writer.writeCharacters(T3);
        writer.writeEmptyElement("excludeFolder");
        writer.writeAttribute("url", "file://$MODULE_DIR$/.idea/output");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static boolean contains(JkPathTreeSet treeSet, Path path) {
        for (JkPathTree tree : treeSet.getPathTrees()) {
            if (JkUtilsPath.isSameFile(tree.getRoot(), path)) {
                return true;
            }
        }
        return false;
    }

    private void writeProjectImportDependencies(Set<Path> allModules) throws XMLStreamException {
        for (final Path rootFolder : this.importedProjects) {
            if (!allModules.contains(rootFolder)) {
                writeOrderEntryForModule(rootFolder.getFileName().toString(), "COMPILE");
                allModules.add(rootFolder);
            }
        }
    }

    private void writeDependencies(JkDependencySet dependencies, JkDependencyResolver resolver, Set<Path> allPaths, Set<Path> allModules,
                                   boolean forceTest) throws XMLStreamException {
        final JkResolveResult resolveResult = resolver.resolve(dependencies);
        if (resolveResult.getErrorReport().hasErrors()) {
            JkLog.warn(resolveResult.getErrorReport().toString());
            JkLog.warn("The generated iml file won't take in account missing files.");
        }
        final JkDependencyNode tree = resolveResult.getDependencyTree();
        for (final JkDependencyNode node : tree.toFlattenList()) {

            // Maven dependency
            if (node.isModuleNode()) {
                final String ideScope = forceTest ? "TEST" : ideScope(node.getModuleInfo().getResolvedScopes());
                final List<LibPath> paths = toLibPath(node.getModuleInfo(), resolver.getRepos(), ideScope);
                for (final LibPath libPath : paths) {
                    if (!allPaths.contains(libPath.bin)) {
                        writeOrderEntryForLib(libPath);
                        allPaths.add(libPath.bin);
                    }
                }

                // File dependencies (file ofSystem + computed)
            } else {
                final String ideScope = forceTest ? "TEST" : ideScope(node.getNodeInfo().getDeclaredScopes());
                final JkDependencyNode.JkFileNodeInfo fileNodeInfo = (JkDependencyNode.JkFileNodeInfo) node.getNodeInfo();
                if (fileNodeInfo.isComputed()) {
                    final Path projectDir = fileNodeInfo.computationOrigin().getIdeProjectBaseDir();
                    if (projectDir != null && !allModules.contains(projectDir)) {
                        writeOrderEntryForModule(projectDir.getFileName().toString(), ideScope);
                        allModules.add(projectDir);
                    }
                } else {
                    writeFileEntries(fileNodeInfo.getFiles(), paths, ideScope);
                }
            }
        }
    }

    private void writeFileEntries(Iterable<Path> files, Set<String> paths, String ideScope) throws XMLStreamException {
        for (final Path file : files) {
            final LibPath libPath = new LibPath();
            libPath.bin = file;
            libPath.scope = ideScope;
            libPath.source = lookForSources(file);
            libPath.javadoc = lookForJavadoc(file);
            writeOrderEntryForLib(libPath);
            paths.add(file.toString());
        }
    }

    private List<LibPath> toLibPath(JkDependencyNode.JkModuleNodeInfo moduleInfo, JkRepoSet repos,
                                    String scope) {
        final List<LibPath> result = new LinkedList<>();
        final JkModuleId moduleId = moduleInfo.getModuleId();
        final JkVersion version = moduleInfo.getResolvedVersion();
        final JkVersionedModule versionedModule = JkVersionedModule.of(moduleId, version);
        final List<Path> files = moduleInfo.getFiles();
        for (final Path file : files) {
            final LibPath libPath = new LibPath();
            libPath.bin = file;
            libPath.scope = scope;
            libPath.source = repos.get(JkModuleDependency.of(versionedModule).withClassifier("sources"));
            libPath.javadoc = repos.get(JkModuleDependency.of(versionedModule).withClassifier("javadoc"));
            result.add(libPath);
        }
        return result;
    }

    private static Set<String> toStringScopes(Set<JkScope> scopes) {
        final Set<String> result = new HashSet<>();
        for (final JkScope scope : scopes) {
            result.add(scope.getName());
        }
        return result;
    }

    private static String ideScope(Set<JkScope> scopesArg) {
        final Set<String> scopes = toStringScopes(scopesArg);
        if (scopes.contains(JkJavaDepScopes.COMPILE.getName())) {
            return "COMPILE";
        }
        if (scopes.contains(JkJavaDepScopes.PROVIDED.getName())) {
            return "PROVIDED";
        }
        if (scopes.contains(JkJavaDepScopes.RUNTIME.getName())) {
            return "RUNTIME";
        }
        if (scopes.contains(JkJavaDepScopes.TEST.getName())) {
            return "TEST";
        }
        return "COMPILE";
    }

    private void writeJdk() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        if (this.forceJdkVersion  && this.sourceJavaVersion != null) {
            writer.writeAttribute("type", "jdk");
            final String jdkVersion = jdkVersion(this.sourceJavaVersion);
            writer.writeAttribute("jdkName", jdkVersion);
            writer.writeAttribute("jdkType", "JavaSDK");
        } else {
            writer.writeAttribute("type", "inheritedJdk");
        }
        writer.writeCharacters("\n");
    }

    private void writeOrderEntrySourceFolder() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        writer.writeAttribute("type", "sourceFolder");
        writer.writeAttribute("forTests", "false");
        writer.writeCharacters("\n");
    }

    private void writeOrderEntryForLib(LibPath libPath) throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeStartElement("orderEntry");
        writer.writeAttribute("type", "module-library");
        if (libPath.scope != null) {
            writer.writeAttribute("scope", libPath.scope);
        }
        writer.writeAttribute("exported", "");
        writer.writeCharacters("\n");
        writer.writeCharacters(T3);
        writer.writeStartElement("library");
        writer.writeCharacters("\n");
        writeLibType("CLASSES", libPath.bin);
        writer.writeCharacters("\n");
        writeLibType("JAVADOC", libPath.javadoc);
        writer.writeCharacters("\n");
        writeLibType("SOURCES", libPath.source);
        writer.writeCharacters("\n" + T3);
        writer.writeEndElement();
        writer.writeCharacters("\n" + T2);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeOrderEntryForModule(String ideaModuleName, String scope) throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        writer.writeAttribute("type", "module");
        if (scope != null) {
            writer.writeAttribute("scope", scope);
        }
        writer.writeAttribute("module-name", ideaModuleName);
        writer.writeAttribute("exported", "");
        writer.writeCharacters("\n");
    }

    private void writeLibType(String type, Path file) throws XMLStreamException {
        writer.writeCharacters(T4);
        if (file != null) {
            writer.writeStartElement(type);
            writer.writeCharacters("\n");
            writer.writeCharacters(T5);
            writer.writeEmptyElement("root");
            writer.writeAttribute("url", ideaPath(baseDir, file));
            writer.writeCharacters("\n" + T4);
            writer.writeEndElement();
        } else {
            writer.writeEmptyElement(type);
        }
    }

    private String ideaPath(Path projectDir, Path file) {
        boolean jarFile = file.getFileName().toString().toLowerCase().endsWith(".jar");
        String type = jarFile ? "jar" : "file";
        Path basePath  = projectDir;
        String varName = "MODULE_DIR";
        if (useVarPath && file.toAbsolutePath().startsWith(JkLocator.getJekaUserHomeDir())) {
            basePath = JkLocator.getJekaUserHomeDir();
            varName = "JEKA_USER_HOME";
        } else if (useVarPath && file.toAbsolutePath().startsWith(JkLocator.getJekaHomeDir())) {
            basePath = JkLocator.getJekaHomeDir();
            varName = "JEKA_HOME";
        }
        String result;
        if (file.startsWith(basePath)) {
            final String relPath = basePath.relativize(file).normalize().toString();
            result = type + "://$" + varName + "$/" + replacePathWithVar(relPath).replace('\\', '/');
        } else {
            if (file.isAbsolute()) {
                result = type + "://" + file.normalize().toString().replace('\\', '/');
            } else {
                result = type + "://$MODULE_DIR$/" + file.normalize().toString().replace('\\', '/');
            }
        }
        if (jarFile) {
            result = result + "!/";
        }
        return result;
    }

    private static String jdkVersion(JkJavaVersion javaVersion) {
        if (JkJavaVersion.V1_4.equals(javaVersion)) {
            return "1.4";
        }
        if (JkJavaVersion.V5.equals(javaVersion)) {
            return "1.5";
        }
        if (JkJavaVersion.V6.equals(javaVersion)) {
            return "1.6";
        }
        if (JkJavaVersion.V7.equals(javaVersion)) {
            return "1.7";
        }
        if (JkJavaVersion.V8.equals(javaVersion)) {
            return "1.8";
        }
        return javaVersion.get();
    }

    private static class LibPath {
        Path bin;
        Path source;
        Path javadoc;
        String scope;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final LibPath libPath = (LibPath) o;

            return bin.equals(libPath.bin);
        }

        @Override
        public int hashCode() {
            return bin.hashCode();
        }
    }

    private String replacePathWithVar(String path) {
        if (!useVarPath) {
            return path;
        }
        final String userHome = JkLocator.getJekaUserHomeDir().toAbsolutePath().normalize().toString().replace('\\', '/');
        final String home = JkLocator.getJekaHomeDir().toAbsolutePath().normalize().toString().replace('\\', '/');
        final String result = path.replace(userHome, "$JEKA_USER_HOME$");
        if (!result.equals(path)) {
            return result;
        }
        return path.replace(home, "$JEKA_HOME$");
    }

    private static XMLStreamWriter createWriter(ByteArrayOutputStream fos) {
        try {
            return XMLOutputFactory.newInstance().createXMLStreamWriter(fos, ENCODING);
        } catch (final XMLStreamException e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    private Path lookForSources(Path binary) {
        final String name = binary.getFileName().toString();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-sources." + ext;
        final List<Path> folders = JkUtilsIterable.listOf(
                binary.resolve(".."),
                binary.resolve("../../../libs-sources"),
                binary.resolve("../../libs-sources"),
                binary.resolve("../libs-sources"));
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-sources.zip");
        return lookFileHere(folders, names);
    }

    private Path lookForJavadoc(Path binary) {
        final String name = binary.getFileName().toString();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-javadoc." + ext;
        final List<Path> folders = JkUtilsIterable.listOf(
                binary.resolve(".."),
                binary.resolve("../../../libs-javadoc"),
                binary.resolve("../../libs-javadoc"),
                binary.resolve("../libs-javadoc"));
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-javadoc.zip");
        return lookFileHere(folders, names);
    }

    private Path lookFileHere(Iterable<Path> folders, Iterable<String> names) {
        for (final Path folder : folders) {
            for (final String name : names) {
                final Path candidate = folder.resolve(name);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // --------------------------- setters ------------------------------------------------


    public JkImlGenerator setSourceLayout(JkProjectSourceLayout sourceLayout) {
        this.sourceLayout = sourceLayout;
        return this;
    }

    public JkImlGenerator setSourceJavaVersion(JkJavaVersion sourceJavaVersion) {
        this.sourceJavaVersion = sourceJavaVersion;
        return this;
    }

    public JkImlGenerator setDependencies(JkDependencyResolver dependencyResolver, JkDependencySet dependencies) {
        this.projectDependencyResolver = dependencyResolver;
        this.projectDependencies = dependencies;
        return this;
    }

    public JkImlGenerator setRunDependencies(JkDependencyResolver buildDependencyResolver, JkDependencySet dependencies) {
        this.runDependencyResolver = buildDependencyResolver;
        this.runDependencies = dependencies;
        return this;
    }

    public JkImlGenerator setImportedProjects(Iterable<Path> importedProjects) {
        this.importedProjects = importedProjects;
        return this;
    }

    public JkImlGenerator setForceJdkVersion(boolean forceJdkVersion) {
        this.forceJdkVersion = forceJdkVersion;
        return this;
    }

    public JkImlGenerator setUseVarPath(boolean useVarPath) {
        this.useVarPath = useVarPath;
        return this;
    }

    public JkImlGenerator setWriter(XMLStreamWriter writer) {
        this.writer = writer;
        return this;
    }

    private Path findPluginXml() {
        if (sourceLayout == null) {
            return null;
        }
        List<Path> candidates = this.sourceLayout.getResources().getExistingFiles("META-INF/plugin.xml");
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream().filter(JkImlGenerator::isPlateformPlugin).findFirst().orElse(null);
    }

    private static boolean isPlateformPlugin(Path pluginXmlFile) {
        try {
            Document doc = JkUtilsXml.documentFrom(pluginXmlFile);
            Element root = JkUtilsXml.directChild(doc, "idea-plugin");
            return  root != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
