package com.teamscale.jacoco.agent.agent.listeners;

import com.teamscale.client.TestDetails;
import com.teamscale.jacoco.agent.agent.controllers.JaCoCoAgentController;
import com.teamscale.report.testwise.model.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.runner.Description;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.io.File;
import java.util.Optional;

/**
 * TestNG and JUnit listener that instructs JaCoCo to create one session per test.
 */
public class TestNGListener extends JUnit4Listener implements ITestListener {

    @Override
    public void onTestStart(ITestResult result) {
        JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromDescription(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(result),
                ETestExecutionResult.PASSED.toString(), StringUtils.EMPTY_STRING);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(result),
                ETestExecutionResult.FAILURE.toString(),
                Optional.ofNullable(result.getThrowable()).map(ExceptionUtils::readStackTrace).orElse(StringUtils.EMPTY_STRING));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(result),
                ETestExecutionResult.SKIPPED.toString(), StringUtils.EMPTY_STRING);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(result), ETestExecutionResult.PASSED.toString(), StringUtils.EMPTY_STRING);
    }

    private TestDetails createTestDetailsFromDescription(ITestResult description) {
        Class testClass = description.getTestClass().getRealClass();

        String uniformPath = testClass.getName() + ":" + description.getMethod().getMethodName();
        String internalId = testClass.getCanonicalName().replaceAll("\\.", "/") + "/" + description.getMethod().getMethodName();
        String sourcePath = StringUtils.stripPrefix(
                new File(testClass.getResource(testClass.getSimpleName() + ".class").getPath()).getAbsolutePath(),
                new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath() + "/");
        sourcePath = StringUtils.stripSuffix(sourcePath, ".class") + ".java";

        return new TestDetails(uniformPath, sourcePath, description.getMethod().getMethodName() + "(" + testClass.getName() + ")");
    }

    @Override
    public void onStart(ITestContext context) {
        // nop
    }

    @Override
    public void onFinish(ITestContext context) {
        // nop
    }

}
