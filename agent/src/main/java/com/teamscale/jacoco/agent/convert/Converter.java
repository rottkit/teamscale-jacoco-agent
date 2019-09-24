package com.teamscale.jacoco.agent.convert;

import eu.cqse.teamscale.report.jacoco.dump.Dump;
import eu.cqse.teamscale.report.jacoco.JaCoCoXmlReportGenerator;
import eu.cqse.teamscale.report.util.AntPatternIncludeFilter;
import com.teamscale.jacoco.util.Benchmark;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.conqat.lib.commons.filesystem.FileSystemUtils;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.IOException;

import static com.teamscale.jacoco.util.LoggingUtils.wrap;

/** Converts one .exec binary coverage file to XML. */
public class Converter {

	/** The command line arguments. */
	private ConvertCommand arguments;

	/** Constructor. */
	public Converter(ConvertCommand arguments) {
		this.arguments = arguments;
	}

	/** Converts one .exec binary coverage file to XML. */
	public void run() throws IOException {
		ExecFileLoader loader = new ExecFileLoader();
		loader.load(arguments.getInputFile());

		SessionInfo sessionInfo = loader.getSessionInfoStore().getMerged("dummy");
		ExecutionDataStore executionDataStore = loader.getExecutionDataStore();

		AntPatternIncludeFilter locationIncludeFilter = new AntPatternIncludeFilter(
				arguments.getLocationIncludeFilters(), arguments.getLocationExcludeFilters());
		Logger logger = LogManager.getLogger(this);
		JaCoCoXmlReportGenerator generator = new JaCoCoXmlReportGenerator(arguments.getClassDirectoriesOrZips(),
				locationIncludeFilter, arguments.shouldIgnoreDuplicateClassFiles(), wrap(logger));

		try (Benchmark benchmark = new Benchmark("Generating the XML report")) {
			String xml = generator.convert(new Dump(sessionInfo, executionDataStore));
			FileSystemUtils.writeFileUTF8(arguments.getOutputFile(), xml);
		}
	}
}
