/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent;

import eu.cqse.teamscale.client.CommitDescriptor;
import eu.cqse.teamscale.client.EReportFormat;
import com.teamscale.jacoco.agent.commandline.Validator;
import okhttp3.HttpUrl;
import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.filesystem.FileSystemUtils;
import org.conqat.lib.commons.string.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Parses agent command line options.
 */
public class AgentOptionsParser {

	/** Character which starts a comment in the config file. */
	private static final String COMMENT_PREFIX = "#";

	/**
	 * Parses the given command-line options.
	 */
	public static AgentOptions parse(String optionsString) throws AgentOptionParseException {
		if (StringUtils.isEmpty(optionsString)) {
			throw new AgentOptionParseException(
					"No agent options given. You must at least provide an output directory (out)"
							+ " and a classes directory (class-dir)");
		}

		AgentOptions options = new AgentOptions();
		options.originalOptionsString = optionsString;

		String[] optionParts = optionsString.split(",");
		for (String optionPart : optionParts) {
			handleOption(options, optionPart);
		}

		Validator validator = options.getValidator();
		if (!validator.isValid()) {
			throw new AgentOptionParseException("Invalid options given: " + validator.getErrorMessage());
		}
		return options;
	}

	/**
	 * Parses and stores the given option in the format <code>key=value</code>.
	 */
	private static void handleOption(AgentOptions options, String optionPart) throws AgentOptionParseException {
		String[] keyAndValue = optionPart.split("=", 2);
		if (keyAndValue.length < 2) {
			throw new AgentOptionParseException("Got an option without any value: " + optionPart);
		}

		String key = keyAndValue[0].toLowerCase();
		String value = keyAndValue[1];

		// Remove quotes, which may be used to pass arguments with spaces via the command line
		if (value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1);
		}

		if (key.startsWith("jacoco-")) {
			options.additionalJacocoOptions.add(key.substring(7), value);
			return;
		} else if (key.startsWith("teamscale-") && handleTeamscaleOptions(options, key, value)) {
			return;
		} else if (key.startsWith("http-server-") && handleHttpServerOptions(options, key, value)) {
			return;
		} else if (handleAgentOptions(options, key, value)) {
			return;
		}
		throw new AgentOptionParseException("Unknown option: " + key);
	}

	/**
	 * Handles all command line options for the agent without special prefix.
	 *
	 * @return true if it has successfully process the given option.
	 */
	private static boolean handleAgentOptions(AgentOptions options, String key, String value) throws AgentOptionParseException {
		switch (key) {
			case "config-file":
				readConfigFromFile(options, new File(value));
				return true;
			case "logging-config":
				options.loggingConfig = parsePath(key, value);
				return true;
			case "interval":
				try {
					options.dumpIntervalInMinutes = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					throw new AgentOptionParseException("Non-numeric value given for option 'interval'");
				}
				return true;
			case "out":
				options.outputDirectory = parsePath(key, value);
				return true;
			case "module":
				options.moduleName = value;
				return true;
			case "upload-url":
				options.uploadUrl = parseUrl(value);
				if (options.uploadUrl == null) {
					throw new AgentOptionParseException("Invalid URL given for option 'upload-url'");
				}
				return true;
			case "upload-metadata":
				try {
					options.additionalMetaDataFiles = CollectionUtils.map(splitMultiOptionValue(value), Paths::get);
				} catch (InvalidPathException e) {
					throw new AgentOptionParseException("Invalid path given for option 'upload-metadata'", e);
				}
				return true;
			case "ignore-duplicates":
				options.shouldIgnoreDuplicateClassFiles = Boolean.parseBoolean(value);
				return true;
			case "includes":
				options.jacocoIncludes = value.replaceAll(";", ":");
				return true;
			case "excludes":
				options.jacocoExcludes = value.replaceAll(";", ":");
				return true;
			case "class-dir":
				options.classDirectoriesOrZips = CollectionUtils.map(splitMultiOptionValue(value), File::new);
				return true;
			default:
				return false;
		}
	}

	/**
	 * Reads configuration parameters from the given file.
	 * The expected format is basically the same as for the command line, but line breaks are also considered as
	 * separators.
	 * e.g.
	 * class-dir=out
	 * # Some comment
	 * includes=test.*
	 * excludes=third.party.*
	 */
	private static void readConfigFromFile(AgentOptions options, File configFile) throws AgentOptionParseException {
		try {
			List<String> configFileKeyValues = FileSystemUtils.readLinesUTF8(configFile);
			for (String optionKeyValue : configFileKeyValues) {
				String trimmedOption = optionKeyValue.trim();
				if (trimmedOption.isEmpty() || trimmedOption.startsWith(COMMENT_PREFIX)) {
					continue;
				}
				handleOption(options, optionKeyValue);
			}
		} catch (FileNotFoundException e) {
			throw new AgentOptionParseException(
					"File " + configFile.getAbsolutePath() + " given for option 'config-file' not found!", e);
		} catch (IOException e) {
			throw new AgentOptionParseException(
					"An error occurred while reading the config file " + configFile.getAbsolutePath() + "!", e);
		}
	}

	/**
	 * Handles all command line options prefixed with "teamscale-".
	 *
	 * @return true if it has successfully process the given option.
	 */
	private static boolean handleTeamscaleOptions(AgentOptions options, String key, String value) throws AgentOptionParseException {
		switch (key) {
			case "teamscale-server-url":
				options.teamscaleServer.url = parseUrl(value).toString();
				if (options.teamscaleServer.url == null) {
					throw new AgentOptionParseException(
							"Invalid URL " + value + " given for option 'teamscale-server-url'!");
				}
				return true;
			case "teamscale-project":
				options.teamscaleServer.project = value;
				return true;
			case "teamscale-user":
				options.teamscaleServer.userName = value;
				return true;
			case "teamscale-access-token":
				options.teamscaleServer.userAccessToken = value;
				return true;
			case "teamscale-partition":
				options.teamscaleServer.partition = value;
				return true;
			case "teamscale-commit":
				options.teamscaleServer.commit = parseCommit(value);
				return true;
			case "teamscale-commit-manifest-jar":
				options.teamscaleServer.commit = getCommitFromManifest(new File(value));
				return true;
			case "teamscale-message":
				options.teamscaleServer.message = value;
				return true;
			default:
				return false;
		}
	}

	/**
	 * Reads `Branch` and `Timestamp` entries from the given jar/war file and
	 * builds a commit descriptor out of it.
	 */
	private static CommitDescriptor getCommitFromManifest(File jarFile) throws AgentOptionParseException {
		try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jarFile))) {
			Manifest manifest = jarStream.getManifest();
			String branch = manifest.getMainAttributes().getValue("Branch");
			String timestamp = manifest.getMainAttributes().getValue("Timestamp");
			if (StringUtils.isEmpty(branch)) {
				throw new AgentOptionParseException("No entry 'Branch' in MANIFEST!");
			} else if (StringUtils.isEmpty(timestamp)) {
				throw new AgentOptionParseException("No entry 'Timestamp' in MANIFEST!");
			}
			return new CommitDescriptor(branch, timestamp);
		} catch (IOException e) {
			throw new AgentOptionParseException("Reading jar " + jarFile.getAbsolutePath() + " failed!", e);
		}
	}

	/**
	 * Handles all command line options prefixed with "http-server-".
	 *
	 * @return true if it has successfully process the given option.
	 */
	private static boolean handleHttpServerOptions(AgentOptions options, String key, String value) throws AgentOptionParseException {
		switch (key) {
			case "http-server-formats":
				options.httpServerReportFormats = parseReportFormats(value);
				return true;
			case "http-server-port":
				try {
					options.httpServerPort = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					throw new AgentOptionParseException(
							"Invalid port number " + value + " given for option 'http-server-port'!");
				}
				return true;
			default:
				return false;
		}
	}

	/**
	 * Parses a semicolon-separated list of report formats like TESTWISE_COVERAGE;JUNIT.
	 */
	private static Set<EReportFormat> parseReportFormats(String reportFormatsString) throws AgentOptionParseException {
		List<String> reportFormatString = splitMultiOptionValue(reportFormatsString.trim());
		if (reportFormatString.size() == 0) {
			throw new AgentOptionParseException("'http-server-formats' is empty!");
		}
		Set<EReportFormat> reportFormats = new HashSet<>();
		for (String format : reportFormatString) {
			try {
				reportFormats.add(EReportFormat.valueOf(format.trim()));
			} catch (IllegalArgumentException e) {
				throw new AgentOptionParseException(
						"Invalid report format '" + format + "' for parameter 'http-server-formats'!", e);
			}
		}
		return reportFormats;
	}

	/**
	 * Parses the given value as a {@link Path}.
	 */
	private static Path parsePath(String optionName, String value) throws AgentOptionParseException {
		try {
			return Paths.get(value);
		} catch (InvalidPathException e) {
			throw new AgentOptionParseException("Invalid path given for option " + optionName + ": " + value, e);
		}
	}

	/**
	 * Parses the given value as a URL or returns <code>null</code> if that fails.
	 */
	private static HttpUrl parseUrl(String value) {
		// default to HTTP if no scheme is given
		if (!value.startsWith("http://") && !value.startsWith("https://")) {
			value = "http://" + value;
		}

		return HttpUrl.parse(value);
	}


	/**
	 * Parses the the string representation of a commit to a  {@link CommitDescriptor} object.
	 * <p>
	 * The expected format is "branch:timestamp".
	 */
	private static CommitDescriptor parseCommit(String commit) throws AgentOptionParseException {
		String[] split = commit.split(":");
		if (split.length != 2) {
			throw new AgentOptionParseException("Invalid commit given " + commit);
		}
		return new CommitDescriptor(split[0], split[1]);
	}

	/**
	 * Splits the given value at semicolons.
	 */
	private static List<String> splitMultiOptionValue(String value) {
		return Arrays.asList(value.split(";"));
	}

}
