package org.jerkar.api.java.junit;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.jerkar.api.file.JkFileTreeSet;
import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaProcess;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsIO;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsReflect;
import org.jerkar.api.utils.JkUtilsString;

/**
 * Convenient class to launch Junit tests.
 *
 * @author Jerome Angibaud
 */
public final class JkUnit {

    /**
     * Detail level for the junit report.
     */
    public enum JunitReportDetail {

        /** No report at all */
        NONE,

        /** Only detail abut the failed test and overall statistics */
        BASIC,

        /** Complete report as the ones generated by surefire.*/
        FULL
    }

    private static final String JUNIT4_RUNNER_CLASS_NAME = "org.junit.runner.JUnitCore";

    private static final String JUNIT3_RUNNER_CLASS_NAME = "junit.textui.TestRunner";

    private static final String JUNIT4_TEST_ANNOTATION_CLASS_NAME = "org.junit.Test";

    private static final String JUNIT3_TEST_CASE_CLASS_NAME = "junit.framework.TestCase";

    private static final String JUNIT3_TEST_SUITE_CLASS_NAME = "junit.framework.TestSuite";

    private static final String JUNIT3_TEST_RESULT_CLASS_NAME = "junit.framework.TestResult";

    private final JunitReportDetail reportDetail;

    private final File reportDir;

    private final JkJavaProcess forkedProcess;

    private final List<Runnable> postActions;

    private final boolean breakOnFailure;

    private final boolean printOutputOnConsole;

    private JkUnit(JunitReportDetail reportDetail, File reportDir,
            JkJavaProcess fork, List<Runnable> runnables,
            boolean crashOnFailed, boolean printOutputOnConsole) {
        this.reportDetail = reportDetail;
        this.reportDir = reportDir;
        this.forkedProcess = fork;
        this.postActions = Collections.unmodifiableList(runnables);
        this.breakOnFailure = crashOnFailed;
        this.printOutputOnConsole = printOutputOnConsole;
    }

    @SuppressWarnings("unchecked")
    private JkUnit(JunitReportDetail reportDetail, File reportDir,
            JkJavaProcess fork, boolean crashOnFailed,
            boolean printOutputOnConsole) {
        this(reportDetail, reportDir, fork, Collections.EMPTY_LIST,
                crashOnFailed, printOutputOnConsole);
    }

    /**
     * Returns an empty junit launcher launcher without classpath set on.
     */
    public static JkUnit of() {
        return new JkUnit(JunitReportDetail.NONE, null, null,
                true, true);
    }


    /**
     * Returns a copy ofMany this launcher but with the specified report detail.
     */
    public JkUnit withReport(JunitReportDetail reportDetail) {
        return new JkUnit(reportDetail, reportDir, this.forkedProcess,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy ofMany this launcher but with the specified report directory output.
     */
    public JkUnit withReportDir(File reportDir) {
        return new JkUnit(reportDetail, reportDir, this.forkedProcess,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy ofMany this launcher but with the specified report directory output.
     */
    public JkUnit withReportDir(Path reportDir) {
        return new JkUnit(reportDetail, reportDir.toFile(), this.forkedProcess,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy ofMany this launcher but that fail fast on the first failure.
     */
    public JkUnit withBreakOnFailure(boolean crashOnFailure) {
        return new JkUnit(reportDetail, reportDir, this.forkedProcess,
                crashOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy ofMany this launcher but specifying an action to run at the end ofMany execution.
     */
    public JkUnit withPostAction(Runnable runnable) {
        final List<Runnable> list = new LinkedList<>(this.postActions);
        list.add(runnable);
        return new JkUnit(reportDetail, reportDir, forkedProcess, list,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Creates a forked JkUnit from this one but using the specified process. If
     * <code>appendClasspath</code> is <code>true</code> then the classpath
     * already defined in this object is appended to the specified process
     * classpath.
     */
    public JkUnit forked(JkJavaProcess process) {
        return new JkUnit(reportDetail, reportDir, process, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Creates an identical JkUnit to this one but specifying the forked mode.
     * If the forked mode is <code>true<code> then the specified
     * {@link JkJavaProcess} is used to run the tests..
     */
    public JkUnit forked(boolean fork, JkJavaProcess process) {
        if (fork && !isForked()) {
            return forked(process);
        }
        if (!fork && isForked()) {
            return new JkUnit(reportDetail, reportDir, null,
                    this.breakOnFailure, this.printOutputOnConsole);
        }
        return this;
    }

    /**
     * Short-hand to #forked(true)
     */
    public JkUnit forked() {
        return forked(true);
    }

    /**
     * Creates an identical JkUnit to this one but specifying the forked mode.
     * If the forked mode is <code>true<code> then default {@link JkJavaProcess}
     * is used to run the tests (java process launched without any option).
     */
    public JkUnit forked(boolean fork) {
        return forked(fork, JkJavaProcess.of());
    }

    /**
     * Returns an enhanced copy ofMany this launcher but specifying if the output should be displayed on console.
     */
    public JkUnit withOutputOnConsole(boolean outputOnConsole) {
        return new JkUnit(reportDetail, reportDir, forkedProcess, breakOnFailure, outputOnConsole);
    }

    /**
     * Returns <code>true</code> if this launcher is forked.
     */
    public boolean isForked() {
        return this.forkedProcess != null;
    }

    /**
     * Returns the report detail level for this launcher.
     */
    public JunitReportDetail reportDetail() {
        return reportDetail;
    }

    /**
     * Returns the output report dir.
     */
    public File reportDir() {
        return reportDir;
    }

    /**
     * Returns the process description if this launcher is forked.
     */
    public JkJavaProcess forkedProcess() {
        return forkedProcess;
    }

    /**
     * Runs the test suite and return the result.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public JkTestSuiteResult run(JkJavaTestSpec testSpec) {
        final Collection<Class> classes = getClassesToTest(testSpec);
        final String name = getSuiteName(classes);

        if (!classes.iterator().hasNext()) {
            JkLog.warn("No test class found.");
            return JkTestSuiteResult.empty((Properties) System.getProperties().clone(), name, 0);
        }
        final long start = System.nanoTime();
        final JkClassLoader classLoader = JkClassLoader.of(classes.iterator().next());


        final JkTestSuiteResult result;

        if (classLoader.isDefined(JUNIT4_RUNNER_CLASS_NAME)) {
            if (this.forkedProcess != null) {
                JkLog.startln("Run JUnit tests in forked mode");
                result = JUnit4TestLauncher.launchInFork(forkedProcess.withClasspaths(testSpec.classpath()),
                        printOutputOnConsole,
                        reportDetail, classes, reportDir);
            } else {
                JkLog.startln("Run JUnit tests");
                result = JUnit4TestLauncher.launchInClassLoader(classes, printOutputOnConsole,
                        reportDetail, reportDir);
            }
        } else if (classLoader.isDefined(JUNIT3_RUNNER_CLASS_NAME)) {
            JkLog.startln("Run JUnit tests");
            final Object suite = createJunit3TestSuite(classLoader, classes);
            final Class testResultClass = classLoader.load(JUNIT3_TEST_RESULT_CLASS_NAME);
            final Object testResult = JkUtilsReflect.newInstance(testResultClass);
            final Method runMethod = JkUtilsReflect.getMethod(suite.getClass(), "run",
                    testResultClass);
            final Properties properties = (Properties) System.getProperties().clone();
            JkUtilsReflect.invoke(suite, runMethod, testResult);
            final long end = System.nanoTime();
            final long duration = (end - start) / 1000000;
            result = fromJunit3Result(properties, name, testResult, duration);
        } else {
            JkUtilsIO.closeQuietly(classLoader.classloader());
            throw new IllegalStateException("No Junit found on test classpath.");

        }

        if (result.failureCount() > 0) {
            if (breakOnFailure) {
                JkLog.error(result.toStrings(JkLog.verbose()));
                JkUtilsIO.closeQuietly(classLoader.classloader());
                throw new IllegalStateException("Test failed : " + result.toString());
            } else {
                JkLog.warn(result.toStrings(JkLog.verbose()));
            }
        } else {
            JkLog.info(result.toStrings(JkLog.verbose()));
        }
        if (!JkLog.verbose() && result.failureCount() > 0) {
            JkLog.info("Launch Jerkar in verbose mode to display failure stack traces in console.");
        }
        if (reportDetail.equals(JunitReportDetail.BASIC)) {
            TestReportBuilder.of(result).writeToFileSystem(reportDir);
        }
        for (final Runnable runnable : this.postActions) {
            runnable.run(); // NOSONAR
        }
        JkLog.done("Tests run");
        JkUtilsIO.closeQuietly(classLoader.classloader());
        return result;
    }

    @SuppressWarnings("rawtypes")
    private Collection<Class> getClassesToTest(JkJavaTestSpec testSpec) {
        final JkClasspath classpath = testSpec.classpath().andManyFirst(testSpec.classesToTest().rootFiles());
        final JkClassLoader classLoader = JkClassLoader.system().parent().child(classpath)
                .loadAllServices();
        final Collection<Class> result = getJunitTestClassesInClassLoader(classLoader, testSpec.classesToTest());
        if (result.isEmpty()) {
            JkUtilsIO.closeOrFail(classLoader.classloader());
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Collection<Class> getJunitTestClassesInClassLoader(JkClassLoader classloader,
            JkFileTreeSet jkFileTreeSet) {
        final Iterable<Class<?>> classes = classloader.loadClassesIn(jkFileTreeSet);
        final List<Class> testClasses = new LinkedList<>();
        if (classloader.isDefined(JUNIT4_RUNNER_CLASS_NAME)) {
            final Class<Annotation> testAnnotation = classloader
                    .load(JUNIT4_TEST_ANNOTATION_CLASS_NAME);
            final Class<?> testCaseClass = classloader.load(JUNIT3_TEST_CASE_CLASS_NAME);
            for (final Class clazz : classes) {
                if (isJunit3Test(clazz, testCaseClass) || isJunit4Test(clazz, testAnnotation)) {
                    testClasses.add(clazz);
                }
            }
        } else if (classloader.isDefined(JUNIT3_RUNNER_CLASS_NAME)) {
            final Class<?> testCaseClass = classloader.load(JUNIT3_TEST_CASE_CLASS_NAME);
            for (final Class clazz : classes) {
                if (isJunit3Test(clazz, testCaseClass)) {
                    testClasses.add(clazz);
                }
            }
        }
        return testClasses;
    }

    private static boolean isJunit3Test(Class<?> candidtateClazz, Class<?> testCaseClass) {
        if (Modifier.isAbstract(candidtateClazz.getModifiers())) {
            return false;
        }
        return testCaseClass.isAssignableFrom(candidtateClazz);
    }

    private static boolean isJunit4Test(Class<?> candidateClass, Class<Annotation> testAnnotation) {
        if (Modifier.isAbstract(candidateClass.getModifiers())) {
            return false;
        }
        return hasConcreteTestMethods(candidateClass, testAnnotation);
    }

    private static boolean hasConcreteTestMethods(Class<?> candidateClass,
            Class<Annotation> testAnnotation) {
        for (final Method method : candidateClass.getMethods()) {
            final int modifiers = method.getModifiers();
            if (!Modifier.isAbstract(modifiers) && Modifier.isPublic(modifiers)
                    && method.getAnnotation(testAnnotation) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    private static Object createJunit3TestSuite(JkClassLoader classLoader,
            Iterable<Class> testClasses) {
        final Class<?>[] classArray = JkUtilsIterable.arrayOf(testClasses, Class.class);
        final Class<?> testSuiteClass = classLoader.load(JUNIT3_TEST_SUITE_CLASS_NAME);
        try {
            final Constructor constructor = testSuiteClass.getConstructor(classArray.getClass());
            return constructor.newInstance((Object) classArray);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JkTestSuiteResult fromJunit3Result(Properties properties, String suiteName,
            Object result, long durationInMillis) {
        final Integer runCount = JkUtilsReflect.invoke(result, "runCount");
        final Integer ignoreCount = 0;
        final Enumeration<Object> junitFailures = JkUtilsReflect.invoke(result, "failures");
        final Enumeration<Object> junitErrors = JkUtilsReflect.invoke(result, "errors");
        final List<JkTestSuiteResult.TestCaseFailure> failures = new ArrayList<>();
        while (junitFailures.hasMoreElements()) {
            final Object junitFailure = junitFailures.nextElement();
            failures.add(JkTestSuiteResult.fromJunit3Failure(junitFailure));
        }
        while (junitErrors.hasMoreElements()) {
            final Object junitError = junitErrors.nextElement();
            failures.add(JkTestSuiteResult.fromJunit3Failure(junitError));
        }
        return new JkTestSuiteResult(properties, suiteName, runCount, ignoreCount, failures,
                durationInMillis);

    }

    @SuppressWarnings("rawtypes")
    private static String getSuiteName(Iterable<Class> classes) {
        final Iterator<Class> it = classes.iterator();
        if (!it.hasNext()) {
            return "";
        }
        final Class<?> firstClass = it.next();
        if (!it.hasNext()) {
            return firstClass.getName();
        }
        String[] result = firstClass.getPackage().getName().split("\\.");
        while (it.hasNext()) {
            final String[] packageName = it.next().getPackage().getName().split("\\.");
            final int min = Math.min(result.length, packageName.length);
            for (int i = 0; i < min; i++) {
                if (!result[i].equals(packageName[i])) {
                    if (i == 0) {
                        return "ALL";
                    }
                    result = Arrays.copyOf(result, i);
                    break;
                }
            }
        }
        return JkUtilsString.join(Arrays.asList(result), ".");
    }

}
