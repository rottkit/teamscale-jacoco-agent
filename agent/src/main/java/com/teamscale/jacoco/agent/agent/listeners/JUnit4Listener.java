package eu.cqse.teamscale.jacoco.agent.listeners;

import eu.cqse.teamscale.client.TestDetails;
import eu.cqse.teamscale.jacoco.agent.controllers.JaCoCoAgentController;
import eu.cqse.teamscale.jacoco.agent.testimpact.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;

/**
 * JUnit listener that instructs JaCoCo to create one session per test.
 */
public class JUnit4Listener extends RunListener {
    private static final Description FAILED = Description.createTestDescription("Test failed", "Test failed");

    @Override
    public void testStarted(Description description) {
        JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromDescription(description));
    }

    @Override
    public void testFinished(Description description) {
        String result = ETestExecutionResult.PASSED.name();
        if (description.getChildren().contains(FAILED)) {
            result = ETestExecutionResult.FAILURE.name();
        }

        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(description), result);
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        System.out.println("Testrun Start");
    }
    @Override
    public void testFailure(Failure failure) throws Exception {
        failure.getDescription().addChild(FAILED);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromDescription(description), ETestExecutionResult.IGNORED.name());
    }

    private TestDetails createTestDetailsFromDescription(Description description) {
        Class testClass = description.getTestClass();
        String externalId = description.getClassName() + ":" + description.getMethodName();
        String internalId = testClass.getCanonicalName().replaceAll("\\.", "/") + "/" + description.getMethodName();
        String sourcePath = StringUtils.stripPrefix(
                new File(testClass.getResource(testClass.getSimpleName() + ".class").getPath()).getAbsolutePath(),
                new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath() + "/");
        sourcePath = StringUtils.stripSuffix(sourcePath, ".class") + ".java";

        return new TestDetails(externalId, internalId, sourcePath, description.getDisplayName(), "");
    }




}