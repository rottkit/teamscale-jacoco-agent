package com.teamscale.jacoco.agent.agent.listeners;

import com.teamscale.client.TestDetails;
import com.teamscale.jacoco.agent.agent.controllers.JaCoCoAgentController;
import com.teamscale.report.testwise.model.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;

/**
 * JUnit listener that instructs JaCoCo to create one session per test.
 */
public class JUnit4Listener extends RunListener {
    private static final Description FAILED = Description.createTestDescription("Test failed", "Test failed");

    private Throwable lastThrownException = null;

    @Override
    public void testStarted(Description description) {
        JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromDescription(description));
    }

    @Override
    public void testFinished(Description description) {
        String result = ETestExecutionResult.PASSED.name();
        String consoleOutput = StringUtils.EMPTY_STRING;

        if (description.getChildren().contains(FAILED)) {
            result = ETestExecutionResult.FAILURE.name();
            consoleOutput = ExceptionUtils.readStackTrace(lastThrownException);
            lastThrownException = null;
        }

        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(description), result,
                consoleOutput);
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        System.out.println("Testrun Start");
    }
    @Override
    public void testFailure(Failure failure) throws Exception {
        failure.getDescription().addChild(FAILED);
        lastThrownException = failure.getException();
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromDescription(description));
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(description), ETestExecutionResult.IGNORED.name(), StringUtils.EMPTY_STRING);
    }

    private TestDetails createTestDetailsFromDescription(Description description) {
        Class testClass = description.getTestClass();
        String uniformPath = description.getClassName() + ":" + description.getMethodName();
        String internalId = testClass.getCanonicalName().replaceAll("\\.", "/") + "/" + description.getMethodName();
        String sourcePath = StringUtils.stripPrefix(
                new File(testClass.getResource(testClass.getSimpleName() + ".class").getPath()).getAbsolutePath(),
                new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath() + "/");
        sourcePath = StringUtils.stripSuffix(sourcePath, ".class") + ".java";

        return new TestDetails(uniformPath, sourcePath, description.getDisplayName());
    }




}