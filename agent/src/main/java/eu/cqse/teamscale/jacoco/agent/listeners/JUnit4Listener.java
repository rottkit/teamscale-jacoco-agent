package eu.cqse.teamscale.jacoco.agent.listeners;

import eu.cqse.teamscale.client.TestDetails;
import eu.cqse.teamscale.jacoco.agent.controllers.JaCoCoAgentController;
import eu.cqse.teamscale.jacoco.agent.testimpact.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;

import static eu.cqse.teamscale.jacoco.agent.controllers.JaCoCoAgentController.MESSAGE_BEFORE_AFTER_TEST;

/**
 * JUnit listener that instructs JaCoCo to create one session per test.
 */
public class JUnit4Listener extends RunListener {
    private static final Description FAILED = Description.createTestDescription("Test failed", "Test failed");

    private Throwable lastThrownException = null;
    private TestDetails nextToUseAfterClassDetails;

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
        assert(description.getChildren().size() == 1);
        Description testClassDescription = description.getChildren().get(0);

        JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromDescription(testClassDescription,
                TestDetails.BEFORE_CLASS_SUFFIX));
        nextToUseAfterClassDetails = createTestDetailsFromDescription(testClassDescription,TestDetails.AFTER_CLASS_SUFFIX);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        assert(nextToUseAfterClassDetails != null);
        // Agent will not use the time of this call as starting time for duration calculation, but the time of the last testFinish-call.
        JaCoCoAgentController.getInstance().onTestStart(nextToUseAfterClassDetails);
        JaCoCoAgentController.getInstance().onTestFinish(nextToUseAfterClassDetails, ETestExecutionResult.PASSED.toString(), MESSAGE_BEFORE_AFTER_TEST + StringUtils
                .stripSuffix(nextToUseAfterClassDetails.internalId, TestDetails.AFTER_CLASS_SUFFIX));
        System.out.println("Total round trip time until now in ms: " + JaCoCoAgentController.getInstance().getTotalResponeTimeMs());
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
        return createTestDetailsFromDescription(description, null);
    }

    private TestDetails createTestDetailsFromDescription(Description description, String methodNameReplacement) {
        String methodName = methodNameReplacement;
        if (methodNameReplacement == null) {
            methodName = description.getMethodName();
        }
        Class testClass = description.getTestClass();
        String externalId = description.getClassName() + ":" + methodName;
        String internalId = testClass.getCanonicalName().replaceAll("\\.", "/") + "/" + methodName;
        String sourcePath = StringUtils.stripPrefix(
                new File(testClass.getResource(testClass.getSimpleName() + ".class").getPath()).getAbsolutePath(),
                new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath() + "/");
        sourcePath = StringUtils.stripSuffix(sourcePath, ".class") + ".java";

        return new TestDetails(externalId, internalId, sourcePath, description.getDisplayName(), "");
    }




}