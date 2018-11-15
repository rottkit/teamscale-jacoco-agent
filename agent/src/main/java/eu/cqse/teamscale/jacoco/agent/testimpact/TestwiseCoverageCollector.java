package eu.cqse.teamscale.jacoco.agent.testimpact;

import eu.cqse.teamscale.client.TestDetails;
import eu.cqse.teamscale.jacoco.agent.AgentOptions;
import eu.cqse.teamscale.jacoco.agent.ITestListener;
import eu.cqse.teamscale.jacoco.agent.JacocoRuntimeController;
import eu.cqse.teamscale.jacoco.agent.store.IXmlStore;
import eu.cqse.teamscale.jacoco.util.Benchmark;
import eu.cqse.teamscale.report.jacoco.dump.Dump;
import eu.cqse.teamscale.report.testwise.jacoco.TestwiseXmlReportGenerator;
import eu.cqse.teamscale.report.testwise.jacoco.cache.CoverageGenerationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jacoco.core.data.SessionInfo;
import spark.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static eu.cqse.teamscale.client.EReportFormat.TESTWISE_COVERAGE;
import static eu.cqse.teamscale.jacoco.util.LoggingUtils.wrap;

/**
 * Test listener which is capable of generating testwise coverage reports.
 */
public class TestwiseCoverageCollector implements ITestListener {

	/** The logger. */
	protected final Logger logger = LogManager.getLogger(this);

	/** Generates XML reports from binary execution data. */
	private TestwiseXmlReportGenerator generator;

	/** Controls the JaCoCo runtime. */
	private JacocoRuntimeController controller;

	/** List of dumps, one for each test. */
	private final List<Dump> dumps = new ArrayList<>();

	/** Constructor. */
	public TestwiseCoverageCollector(JacocoRuntimeController controller, AgentOptions options) throws CoverageGenerationException {
		this.controller = controller;
		this.generator = new TestwiseXmlReportGenerator(options.getClassDirectoriesOrZips(),
				options.getLocationIncludeFilter(), options.shouldIgnoreDuplicateClassFiles(), wrap(logger));
	}

	@Override
	public void onTestStart(Request request, Dump dump) {
		String testId = request.params(TestImpactAgent.TEST_ID_PARAMETER);
		if (testId.endsWith(TestDetails.AFTER_CLASS_SUFFIX)){
			return;
		}
		// Reset coverage so that we only record coverage that belongs to this particular test case.
		// Dumps from previous tests are stored in #dumps
		controller.reset();
		controller.setSessionId(testId);
	}

	@Override
	public void onTestFinish(Request request, Dump dump) {
		String testId = request.params(TestImpactAgent.TEST_ID_PARAMETER);
		if (testId.endsWith(TestDetails.AFTER_CLASS_SUFFIX)){
			SessionInfo unnamedSessionInfo = dump.info;
			SessionInfo sessionInfo= new SessionInfo(testId, unnamedSessionInfo.getStartTimeStamp(), unnamedSessionInfo.getDumpTimeStamp());
			dump = new Dump(sessionInfo, dump.store);
		}
		dumps.add(dump);
	}

	@Override
	public void onDump(IXmlStore store) {
		if (dumps.isEmpty()) {
			return;
		}
		String xml;
		try (Benchmark benchmark = new Benchmark("Generating the XML report")) {
			xml = generator.convertToString(dumps);
		} catch (IOException e) {
			logger.error("Converting binary dumps to XML failed", e);
			return;
		}

		store.store(xml, TESTWISE_COVERAGE);
		dumps.clear();
	}
}
