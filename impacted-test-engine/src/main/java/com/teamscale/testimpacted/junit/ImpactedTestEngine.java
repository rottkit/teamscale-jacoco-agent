package com.teamscale.testimpacted.junit;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.TestDetails;
import com.teamscale.report.ReportUtils;
import com.teamscale.report.testwise.model.TestExecution;
import com.teamscale.testimpacted.junit.executor.AvailableTests;
import com.teamscale.testimpacted.junit.executor.ITestExecutor;
import com.teamscale.testimpacted.junit.executor.TestExecutorRequest;
import com.teamscale.testimpacted.junit.options.OptionsUtils;
import com.teamscale.testimpacted.junit.options.TestEngineOptions;
import com.teamscale.testimpacted.test_descriptor.TestDescriptorUtils;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ImpactedTestEngine implements TestEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImpactedTestEngine.class);

	static final String ENGINE_ID = "teamscale-test-impacted";

	private final TestEngineRegistry testEngineRegistry = new TestEngineRegistry();

	private final TestEngineOptions testEngineOptions = OptionsUtils.getEngineOptions(System.getProperties());

	@Override
	public String getId() {
		return ENGINE_ID;
	}

	@Override
	public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
		EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, "Teamscale Impacted Tests");
		List<TestEngine> enabledTestEngines = new ArrayList<>(testEngineRegistry.getEnabledTestEngines().values());
		List<TestDetails> allAvailableTests = new ArrayList<>();

		enabledTestEngines.sort(Comparator.comparing(TestEngine::getId));

		LOGGER.debug(() -> "Starting test discovery for engine " + ENGINE_ID);

		for (TestEngine delegateTestEngine : enabledTestEngines) {
			LOGGER.debug(() -> "Starting test discovery for delegate engine: " + delegateTestEngine.getId());
			TestDescriptor delegateEngineDescriptor = delegateTestEngine.discover(discoveryRequest,
					UniqueId.forEngine(delegateTestEngine.getId()));
			AvailableTests availableTests = TestDescriptorUtils
					.getAvailableTests(delegateTestEngine, delegateEngineDescriptor);

			engineDescriptor.addChild(delegateEngineDescriptor);
			allAvailableTests.addAll(availableTests.getTestList());
		}

		LOGGER.debug(() -> "Discovered test descriptor for engine " + ENGINE_ID + ":\n" + TestDescriptorUtils
				.getTestDescriptorAsString(engineDescriptor));

		dumpTestDetails(allAvailableTests);

		return engineDescriptor;
	}

	@Override
	public void execute(ExecutionRequest request) {
		EngineExecutionListener engineExecutionListener = request.getEngineExecutionListener();
		EngineDescriptor engineDescriptor = (EngineDescriptor) request.getRootTestDescriptor();
		Map<String, TestEngine> delegateTestEngines = testEngineRegistry.getEnabledTestEngines();

		LOGGER.debug(() -> "Starting execution of request for engine " + ENGINE_ID + ":\n" + TestDescriptorUtils
				.getTestDescriptorAsString(engineDescriptor));

		engineExecutionListener.executionStarted(engineDescriptor);
		runTestExecutor(request, delegateTestEngines);
		engineExecutionListener.executionFinished(engineDescriptor, TestExecutionResult.successful());
	}

	private void runTestExecutor(ExecutionRequest request, Map<String, TestEngine> delegateTestEngines) {
		List<TestExecution> testExecutions = new ArrayList<>();
		ITestExecutor testExecutor = testEngineOptions.createTestExecutor();

		for (TestDescriptor engineTestDescriptor : request.getRootTestDescriptor().getChildren()) {
			Optional<String> engineId = engineTestDescriptor.getUniqueId().getEngineId();

			if (!engineId.isPresent()) {
				continue;
			}

			TestEngine testEngine = delegateTestEngines.get(engineId.get());
			TestExecutorRequest testExecutorRequest = new TestExecutorRequest(testEngine, engineTestDescriptor,
					request.getEngineExecutionListener(), request.getConfigurationParameters());
			List<TestExecution> testExecutionsOfEngine = testExecutor.execute(testExecutorRequest);

			testExecutions.addAll(testExecutionsOfEngine);
		}

		dumpTestExecutions(testExecutions);
	}

	private void dumpTestExecutions(List<TestExecution> testExecutions) {
		writeReport(new File(testEngineOptions.getReportDirectory(), "test-execution.json"), testExecutions);
	}

	/** Writes the given test details to a report file. */
	private void dumpTestDetails(List<TestDetails> testDetails) {
		writeReport(new File(testEngineOptions.getReportDirectory(), "test-list.json"), testDetails);
	}

	private static <T> void writeReport(File file, T report) {
		try {
			ReportUtils.writeReportToFile(file, report);
		} catch (IOException e) {
			LOGGER.error(e, () -> "Error while writing report to file: " + file);
		}
	}
}
