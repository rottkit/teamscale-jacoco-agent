package eu.cqse.teamscale.jacoco.agent.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.cqse.teamscale.client.TestDetails;
import eu.cqse.teamscale.jacoco.agent.testimpact.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Translates test start and finish event into actions for the locally running jacoco agent.
 */
public class JaCoCoAgentController {

	/** Singleton instance for this class. */
	private static JaCoCoAgentController singleton;

	/** Reference to the test-impact-analysis client that sends the requests invoked by the test listener. */
	private final TestImpactClient tiaClient;

	private TestDetails testDetailsOfCurrentTest;

	/** Constructor. */
	private JaCoCoAgentController() {
		Gson gson = new GsonBuilder()
				.setLenient()
				.create();

		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl("http://localhost:8000/")
				.addConverterFactory(GsonConverterFactory.create(gson))
				.build();

		tiaClient = retrofit.create(TestImpactClient.class);
	}

	/** Returns a singleton instance of the controller. */
	public static JaCoCoAgentController getInstance() {
		if (singleton == null) {
			singleton = new JaCoCoAgentController();
		}
		return singleton;
	}

	/**
	 * Called when a test starts.
	 */
	public void onTestStart(TestDetails testDetails) {
		try {
			if (Objects.nonNull(testDetailsOfCurrentTest)) {
				String message = "For test " + testDetails.externalId + " there was no end event passed to the test listener; assume that it ended now with an error.";
				System.out.println(message);
				onTestFinish(testDetails, ETestExecutionResult.ERROR.name(), message);
			}

			testDetailsOfCurrentTest = testDetails;
			System.out.println("Test start ("+testDetails.externalId+"):" + tiaClient.handleTestStart(testDetails.externalId, testDetails).execute().body());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called when a test finished.
	 */
	public void onTestFinish(TestDetails testDetails, String result, String consoleOutput) {
		try {
			if (testDetailsOfCurrentTest.externalId.equals(testDetails.externalId)) {
				testDetailsOfCurrentTest = null;
			}

			tiaClient.handleTestEnd(testDetails.externalId, consoleOutput, result).execute();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
