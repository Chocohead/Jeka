package org.jake.java.build;

import java.io.File;

import org.jake.JakeBuildBase;
import org.jake.JakeClasspath;
import org.jake.JakeDirSet;
import org.jake.JakeDoc;
import org.jake.JakeFileFilter;
import org.jake.JakeJavaCompiler;
import org.jake.JakeLog;
import org.jake.JakeOption;
import org.jake.depmanagement.JakeDependencyResolver;
import org.jake.depmanagement.JakeLocalDependencyResolver;
import org.jake.depmanagement.JakeScope;
import org.jake.java.JakeJavadoc;
import org.jake.java.JakeResourceProcessor;
import org.jake.java.JakeUtilsJdk;
import org.jake.java.test.jacoco.Jakeoco;
import org.jake.java.test.junit.JakeUnit;
import org.jake.java.test.junit.JakeUnit.JunitReportDetail;
import org.jake.utils.JakeUtilsFile;
import org.jake.verify.sonar.JakeSonar;

public class JakeBuildJava extends JakeBuildBase {

	/**
	 * Default path for the non managed dependencies. This path is relative to {@link #baseDir()}.
	 */
	protected static final String STD_LIB_PATH = "build/libs";

	/**
	 * Filter to excludes everything in a java source directory which are not resources.
	 */
	protected static final JakeFileFilter RESOURCE_FILTER = JakeFileFilter
			.exclude("**/*.java").andExcludeAll("**/package.html")
			.andExcludeAll("**/doc-files");

	@JakeOption({
		"Mention if you want to add extra lib in your 'compile' scope but not in your 'runtime' scope. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile path but won't be embedded in war files or fat jars.",
	"Example : -extraProvidedPath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraProvidedPath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'runtime' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the runtime path.",
	"Example : -extraRuntimePath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraRuntimePath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'compile' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile and runtime path.",
	"Example : -extraCompilePath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraCompilePath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'test' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile and runtime path.",
	"Example : -extraTestPath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraTestPath;

	@JakeOption("Turn it on to skip tests.")
	protected boolean skipTests;

	public boolean skipTests() {
		return skipTests;
	}

	@JakeOption({
		"You can force the dependencyResolver to use by specifying a class name. This class must be in Jake classpath.",
	"You can either use a fully qulified class name or just its simple name." })
	protected String dependencyResolver;

	@JakeOption({"The more details the longer tests take to be processed.",
		"BASIC mention the total time elapsed along detail on failed tests.",
		"FULL detailed report displays additionally the time to run each tests.",
	"Example : -junitReportDetail=NONE"})
	protected JunitReportDetail junitReportDetail = JunitReportDetail.BASIC;

	// --------------------------- Project settings -----------------------

	public String sourceEncoding() {
		return "UTF-8";
	}

	public String sourceJavaVersion() {
		return JakeUtilsJdk.runningJavaVersion();
	}

	public String targetJavaVersion() {
		return sourceJavaVersion();
	}

	/**
	 * Returns the location of production source code that has not been edited manually (not generated).
	 */
	public JakeDirSet editedSourceDirs() {
		return JakeDirSet.of(baseDir("src/main/java"));
	}

	/**
	 * Returns location of production source code.
	 */
	public JakeDirSet sourceDirs() {
		return editedSourceDirs().and(generatedSourceDir());
	}

	/**
	 * Returns location of production resources.
	 */
	public JakeDirSet resourceDirs() {
		return sourceDirs().withFilter(RESOURCE_FILTER).and(
				baseDir("src/main/resources"), generatedSourceDir());
	}

	/**
	 * Returns location of test source code.
	 */
	public JakeDirSet testSourceDirs() {
		return JakeDirSet.of(baseDir().sub("src/test/java"));
	}

	/**
	 * Returns location of test resources.
	 */
	public JakeDirSet testResourceDirs() {
		return JakeDirSet.of(baseDir("src/test/resources")).and(
				testSourceDirs().withFilter(RESOURCE_FILTER));
	}

	/**
	 * Returns location of generated sources.
	 */
	public File generatedSourceDir() {
		return ouputDir("generated-sources/java");
	}

	/**
	 * Returns location of generated resources.
	 */
	public File generatedResourceDir() {
		return ouputDir("generated-resources");
	}

	/**
	 * Returns location of generated resources for tests.
	 */
	public File generatedTestResourceDir() {
		return ouputDir("generated-test-resources");
	}

	/**
	 * Returns location where the java production classes are compiled.
	 */
	public File classDir() {
		return ouputDir().sub("classes").createIfNotExist().root();
	}

	/**
	 * Returns location where the test reports are written.
	 */
	public File testReportDir() {
		return ouputDir("test-reports");
	}

	/**
	 * Returns location where the java production classes are compiled.
	 */
	public File testClassDir() {
		return ouputDir().sub("testClasses").createIfNotExist().root();
	}

	// --------------------------- Configurer -----------------------------

	public JakeJavaCompiler productionCompiler() {
		return JakeJavaCompiler.ofOutput(classDir())
				.andSources(sourceDirs())
				.withClasspath(deps(JakeScope.COMPILE))
				.withSourceVersion(this.sourceJavaVersion())
				.withTargetVersion(this.targetJavaVersion());
	}

	public JakeJavaCompiler unitTestCompiler() {
		return JakeJavaCompiler.ofOutput(testClassDir())
				.andSources(testSourceDirs())
				.withClasspath(this.deps(JakeScope.TEST).andHead(classDir()))
				.withSourceVersion(this.sourceJavaVersion())
				.withTargetVersion(this.targetJavaVersion());
	}

	public JakeUnit unitTester() {
		final JakeClasspath classpath = JakeClasspath.of(this.testClassDir(), this.classDir()).and(this.deps(JakeScope.TEST));
		final File junitReport = new File(this.testReportDir(), "junit");
		return JakeUnit.of(classpath)
				.withReportDir(junitReport)
				.withReport(this.junitReportDetail)
				.withClassesToTest(this.testClassDir());
	}

	public JakeJavadoc javadoc() {
		final File outputDir = ouputDir(projectName() + "-javadoc");
		final File zip =  ouputDir(projectName() + "-javadoc.zip");
		return JakeJavadoc.of(sourceDirs(), outputDir, zip)
				.withClasspath(deps(JakeScope.COMPILE));
	}

	public JakeJarPacker jarPacker() {
		return JakeJarPacker.of(this);
	}

	public Jakeoco jacoco() {
		final File agent = baseDir("build/libs/jacoco-agent/jacocoagent.jar");
		final File agentFile = agent.exists() ? agent : Jakeoco.defaultAgentFile();
		if (!agentFile.exists()) {
			throw new IllegalStateException("No jacocoagent.jar found neither in "
					+ Jakeoco.defaultAgentFile().getAbsolutePath()
					+ " nor in " + agent.getAbsolutePath() );
		}
		return Jakeoco.of(new File(testReportDir(), "jacoco/jacoco.exec")).withAgent(agentFile);
	}

	public JakeSonar jakeSonar() {
		final File baseDir = baseDir().root();
		JakeLog.warnIf(this.junitReportDetail != JunitReportDetail.FULL,"*  You need to use junitReportDetail=FULL " +
				"to get complete sonar test report but you are currently using " + this.junitReportDetail.name() + ".");
		return JakeSonar.of(projectFullName(), projectName(), version())
				.withProjectBaseDir(baseDir)
				.withBinaries(classDir())
				.withLibraries(deps(JakeScope.COMPILE))
				.withSources(editedSourceDirs().listRoots())
				.withTest(testSourceDirs().listRoots())
				.withProperty(JakeSonar.JUNIT_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(testReportDir(), "junit")))
				.withProperty(JakeSonar.SUREFIRE_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(testReportDir(), "junit")))
				.withProperty(JakeSonar.DYNAMIC_ANALYSIS, "reuseReports")
				.withProperty(JakeSonar.JACOCO_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(testReportDir(), "jacoco/jacoco.exec")));
	}

	// --------------------------- Callable Methods -----------------------

	@JakeDoc("Generate sources and resources, compile production sources and process production resources to the classes directory.")
	public void compile() {
		JakeLog.startAndNextLine("Processing production code and resources");
		generateSources();
		productionCompiler().compile();
		generateResources();
		processResources();
		JakeLog.done();
	}

	@JakeDoc("Compile and run all unit tests.")
	public void unitTest() {
		if (!checkProcessTests(testSourceDirs())) {
			return;
		}
		JakeLog.startAndNextLine("Process unit tests");
		unitTestCompiler().compile();
		processUnitTestResources();
		unitTester().run();
		JakeLog.done();
	}

	@JakeDoc("Produce documents for this project (javadoc, Html site, ...)")
	public void doc() {
		javadoc().process();
	}

	@JakeDoc({	"Create many jar files containing respectively binaries, sources, test binaries and test sources.",
	"The jar containing the binary is the one that will be used as a depe,dence for other project."})
	public void pack() {
		jarPacker().pack();
	}

	@JakeDoc("Compile production code and resources, compile test code and resources then launch the unit tests.")
	@Override
	public void base() {
		super.base();
		compile();
		unitTest();
	}

	// ----------------------- Overridable sub-methods ---------------------




	private JakeDependencyResolver cachedResolver;

	/**
	 * Returns the base dependency resolver.
	 * 
	 * @see #dependencyResolver().
	 */
	protected JakeDependencyResolver baseDependencyResolver() {
		final File folder = baseDir(STD_LIB_PATH);
		final JakeDependencyResolver resolver;
		if (folder.exists()) {
			resolver = JakeLocalDependencyResolver
					.standard(baseDir(STD_LIB_PATH));
		} else {
			resolver = JakeLocalDependencyResolver.empty();
		}
		return resolver;
	}

	/**
	 * Returns the resolver finally used in this build. Depending od the passed
	 * options, It is made of the {@link #baseDependencyResolver()} augmented
	 * with extra-libs mentioned in options <code>extraXxxxPath</code>.
	 */
	public final JakeClasspath deps(JakeScope scope) {
		if (cachedResolver == null) {
			JakeLog.startAndNextLine("Resolving Dependencies ");
			final JakeDependencyResolver resolver = JakeDependencyResolver
					.findByClassNameOrDfault(dependencyResolver, baseDependencyResolver());
			final JakeDependencyResolver extraResolver = computeExtraPath();
			if (!extraResolver.isEmpty()) {
				JakeLog.info("Using extra libs : ", extraResolver.toStrings());
				cachedResolver = resolver.merge(extraResolver);
			} else {
				cachedResolver = resolver;
			}
			JakeLog.info("Effective resolver : ", cachedResolver.toStrings());
			JakeLog.done();
		}
		return JakeClasspath.of(cachedResolver.get(scope));
	}

	protected void generateSources() {
		// Do nothing by default
	}

	@JakeDoc("Generate files to be taken as resources.  Do nothing by default.")
	protected void generateResources() {
		// Do Nothing
	}

	protected void processResources() {
		JakeResourceProcessor.of(resourceDirs()).andIfExist(generatedResourceDir()).generateTo(classDir());
	}

	protected void processUnitTestResources() {
		JakeResourceProcessor.of(testResourceDirs()).andIfExist(generatedTestResourceDir()).generateTo(testClassDir());
	}

	protected boolean checkProcessTests(JakeDirSet testSourceDirs) {
		if (skipTests) {
			return false;
		}
		if (testSourceDirs == null || testSourceDirs.listJakeDirs().isEmpty()) {
			JakeLog.info("No test source declared. Skip tests.");
			return false;
		}
		if (!testSourceDirs().exist()) {
			JakeLog.info("No existing test source directory found : " + testSourceDirs +". Skip tests.");
			return false;
		}
		return true;
	}

	// ------------------------------------

	public static void main(String[] args) {
		new JakeBuildJava().base();
	}

	private JakeLocalDependencyResolver computeExtraPath() {
		return JakeLocalDependencyResolver.empty()
				.with(JakeScope.COMPILE, toPath(extraCompilePath))
				.with(JakeScope.RUNTIME, toPath(extraRuntimePath))
				.with(JakeScope.TEST, toPath(extraTestPath))
				.with(JakeScope.PROVIDED, toPath(extraProvidedPath));
	}

	private final JakeClasspath toPath(String pathAsString) {
		if (pathAsString == null) {
			return JakeClasspath.of();
		}
		return JakeClasspath.of(JakeUtilsFile.toPath(pathAsString, ";", baseDir().root()));
	}

}