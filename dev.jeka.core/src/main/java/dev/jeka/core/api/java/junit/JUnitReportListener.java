package dev.jeka.core.api.java.junit;

import dev.jeka.core.api.java.junit.JkTestSuiteResult.JkExceptionDescription;
import dev.jeka.core.api.java.junit.JkTestSuiteResult.JkIgnoredCase;
import dev.jeka.core.api.java.junit.JkTestSuiteResult.JkTestCaseFailure;
import dev.jeka.core.api.java.junit.JkTestSuiteResult.JkTestCaseResult;
import dev.jeka.core.api.utils.JkUtilsTime;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

class JUnitReportListener extends RunListener {

    private List<JkTestCaseResult> cases;

    private Properties properties;

    private Class<?> currentClass;

    private long suiteTimeNano;

    private long testTimeNano;

    private int ignoreCount;

    private final Path folder;

    private String currentTestName;

    private boolean failureFlag;

    public JUnitReportListener(Path folder) {
        super();
        this.folder = folder;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        final Class<?> clazz = description.getTestClass();
        if (!clazz.equals(currentClass)) {
            dump();
            init(clazz);
        }
        testTimeNano = System.nanoTime();
        currentTestName = description.getMethodName();
        failureFlag = false;
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        ignoreCount++;
        final JkIgnoredCase ignoredCase = JkIgnoredCase.of(currentClass.getName(), currentTestName);
        this.cases.add(ignoredCase);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        final float duration = (JkUtilsTime.durationInMillis(testTimeNano)) / 1000f;
        if (!failureFlag) {
            final JkTestCaseResult result = JkTestCaseResult.of(currentClass.getName(),
                    currentTestName, duration);
            cases.add(result);
        }
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        failureFlag = true;
        final float duration = (JkUtilsTime.durationInMillis(testTimeNano)) / 1000f;
        final JkTestCaseFailure caseFailure = JkTestCaseFailure.of(currentClass.getName(),
                currentTestName, duration, JkExceptionDescription.of(failure.getException()));
        cases.add(caseFailure);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        dump();
    }

    private void init(Class<?> clazz) {
        this.currentClass = clazz;
        this.properties = new Properties();
        this.properties.putAll(System.getProperties());
        this.suiteTimeNano = System.nanoTime();
        this.cases = new LinkedList<>();
        this.ignoreCount = 0;
    }

    private void dump() {
        if (currentClass == null) {
            return;
        }
        final long duration = JkUtilsTime.durationInMillis(suiteTimeNano);
        final String suiteName = currentClass.getName();
        final int count = cases.size();
        final JkTestSuiteResult result = new JkTestSuiteResult(properties, suiteName, count,
                ignoreCount, cases, duration);
        TestReportBuilder.of(result).writeToFileSystem(folder);
    }

}
