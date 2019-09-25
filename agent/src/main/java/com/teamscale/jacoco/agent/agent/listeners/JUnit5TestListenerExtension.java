package com.teamscale.jacoco.agent.agent.listeners;

import com.teamscale.client.TestDetails;
import com.teamscale.jacoco.agent.agent.controllers.JaCoCoAgentController;
import com.teamscale.report.testwise.model.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.runner.Description;

import java.io.File;

/** Implementation of the {@link TestExecutionListener} interface provided by the JUnit platform. */
public class JUnit5TestListenerExtension implements TestExecutionListener {

    private TestPlan testPlan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
    }

	@Override
	public void executionStarted(TestIdentifier testIdentifier) {
		if (testIdentifier.isTest()) {
			JaCoCoAgentController.getInstance().onTestStart(createTestDetailsFromTestIdentifier(testIdentifier));
		}
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		if (testIdentifier.isTest()) {
			JaCoCoAgentController.getInstance().onTestFinish(createTestDetailsFromTestIdentifier(testIdentifier),
                   convertJunit5TestResultToCqseTestResult(testExecutionResult).toString(),
                    testExecutionResult.getThrowable().map(ExceptionUtils::readStackTrace).orElse(StringUtils.EMPTY_STRING));
		}
	}

	private TestDetails createTestDetailsFromTestIdentifier(TestIdentifier testIdentifier) {
        Class testClass = findTestMethodClass(testPlan, testIdentifier);

        String uniformPath = testIdentifier.getUniqueId();

        String methodName = testIdentifier.getSource().map(testSource -> ((MethodSource) testSource).getMethodName()).get();
        String suffixForDynamicOrParametricTest = uniformPath.substring(uniformPath.lastIndexOf(methodName));
        if (suffixForDynamicOrParametricTest.contains("/")) {
            suffixForDynamicOrParametricTest = suffixForDynamicOrParametricTest
                    .substring(suffixForDynamicOrParametricTest.lastIndexOf('/') + 1);
        } else {
            suffixForDynamicOrParametricTest = StringUtils.EMPTY_STRING;
        }

        String internalId = testClass.getCanonicalName().replaceAll("\\.", "/") + "/" + methodName + suffixForDynamicOrParametricTest;
        String displayName = methodName + suffixForDynamicOrParametricTest + "(" + testClass.getCanonicalName() + ")";

        String sourcePath = StringUtils.stripPrefix(
                new File(testClass.getResource(testClass.getSimpleName() + ".class").getPath()).getAbsolutePath(),
                new File(testClass.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath() + "/");
        sourcePath = StringUtils.stripSuffix(sourcePath, ".class") + ".java";

		return new TestDetails(uniformPath, sourcePath, displayName);
	}

    private static Class findTestMethodClass(TestPlan testPlan, TestIdentifier identifier) {
        identifier.getSource().orElseThrow(IllegalStateException::new);
        identifier.getSource().ifPresent(source -> {
            if (!(source instanceof MethodSource)) {
                throw new IllegalStateException("identifier must contain MethodSource");
            }
        });

        TestIdentifier current = identifier;
        while (current != null) {
            if (current.getSource().isPresent() && current.getSource().get() instanceof ClassSource) {
                return ((ClassSource) current.getSource().get()).getJavaClass();
            }
            current = testPlan.getParent(current).orElse(null);
        }
        throw new IllegalStateException("Class not found");
    }

    private static ETestExecutionResult convertJunit5TestResultToCqseTestResult(TestExecutionResult junitTestResult) {
        switch (junitTestResult.getStatus()) {
            case SUCCESSFUL:
                return ETestExecutionResult.PASSED;
            case ABORTED:
                return ETestExecutionResult.ERROR;
            case FAILED:
                return ETestExecutionResult.FAILURE;
            default:
                throw new RuntimeException("Unknown JUnit5 test result could not be converted to CQSE Test result.");
        }
    }
}
