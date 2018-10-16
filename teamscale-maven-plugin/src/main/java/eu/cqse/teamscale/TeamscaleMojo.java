package eu.cqse.teamscale;

import eu.cqse.teamscale.client.EReportFormat;
import eu.cqse.teamscale.client.TeamscaleServer;
import eu.cqse.teamscale.client.CommitDescriptor;
import eu.cqse.teamscale.client.TeamscaleClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Goal which generates and uploads all
 *  - JUnit test execution results
 *  - TestDetails and
 *  - testwise coverage
 * to Teamscale.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, aggregator = true)
public class TeamscaleMojo extends AbstractMojo {


    public static final ReportTypeWithFilePrefixAndSuffix TEST_RESULTS_REPORT =
            new ReportTypeWithFilePrefixAndSuffix("Test Results","junit-", ".xml", EReportFormat.JUNIT);

    public static final ReportTypeWithFilePrefixAndSuffix TEST_DETAILS_REPORT =
            new ReportTypeWithFilePrefixAndSuffix("Test Details","test-list-", ".json", EReportFormat.TEST_LIST);

    public static final ReportTypeWithFilePrefixAndSuffix TESTWISE_COVERAGE_REPORT =
            new ReportTypeWithFilePrefixAndSuffix("Testwise Coverage","testwise-coverage-", ".xml", EReportFormat.TESTWISE_COVERAGE);

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Teamscale server configuration.
     */
    @Parameter(readonly = true, required = true)
    private TeamscaleServer server;

    /**
     * Name of the branch of the code revision to upload to.
     */
    @Parameter
    private String branchName;

    /**
     * Timestamp of the code revision to upload to.
     */
    @Parameter
    private Long timestamp;

    private TeamscaleClient client;
    private CommitDescriptor commitDescriptor;

    public void execute() throws MojoExecutionException {
        client = new TeamscaleClient(server.url, server.userName, server.userAccessToken, server.project);
        commitDescriptor = getCommitDescriptor();

        uploadReport(TEST_RESULTS_REPORT, "junit");
        uploadReport(TEST_DETAILS_REPORT, "testwise");
        uploadReport(TESTWISE_COVERAGE_REPORT, "testwise");
    }

    private static class ReportTypeWithFilePrefixAndSuffix {
        String reportType;
        String prefix;
        String postfix;
        EReportFormat format;

        public ReportTypeWithFilePrefixAndSuffix(String reportType, String prefix, String postfix, EReportFormat format) {
            this.reportType = reportType;
            this.prefix = prefix;
            this.postfix = postfix;
            this.format = format;
        }
    }

    private CommitDescriptor getCommitDescriptor() throws MojoExecutionException {
       if (timestamp != null) {
            if (branchName == null) {
                return new CommitDescriptor("master", timestamp);
            } else {
                return new CommitDescriptor(branchName, timestamp);
            }
        }

        try {
            return GitRepositoryHelper.getHeadCommitDescriptor(project.getBasedir());
        } catch (GitAPIException | IOException e) {
            throw new MojoExecutionException("Failed to retrieve git commit descriptor!", e);
        }
    }

    private void uploadReport(ReportTypeWithFilePrefixAndSuffix reportType, String partition) {
        Set<File> filesToUpload = new HashSet<>();

        try {
            filesToUpload = Files.walk(project.getBasedir().toPath(), 1)
                    .filter(path -> path.getFileName().toString().startsWith(reportType.prefix) && path.toString().endsWith(reportType.postfix))
                    .map(Path::toFile)
                    .collect(Collectors.toSet());
            } catch (IOException e) {
            e.printStackTrace();
        }

        getLog().info("Found " + reportType.reportType + " Files to upload: " + filesToUpload.toString());

        if (!filesToUpload.isEmpty()) {
            getLog().info("Uploading those files...");
            try {
                client.uploadReports(reportType.format, filesToUpload, commitDescriptor, partition, reportType.reportType + " Maven upload");
            } catch (IOException e) {
                getLog().warn("Upload failed!");
                getLog().debug(e);
            }
        }
    }
}
