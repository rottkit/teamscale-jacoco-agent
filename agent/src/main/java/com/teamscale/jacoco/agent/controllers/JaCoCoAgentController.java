package com.teamscale.jacoco.agent.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.cqse.teamscale.client.TestDetails;
import com.teamscale.jacoco.agent.testimpact.ETestExecutionResult;
import org.conqat.lib.commons.string.StringUtils;
import retrofit2.Response;
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
	public static final String MESSAGE_BEFORE_AFTER_TEST = "Method executed before tests in class ";

	private TestDetails testDetailsOfCurrentTest;

	private int totalResponeTimeMs = 0;

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
				if (testDetailsOfCurrentTest.internalId.endsWith(TestDetails.BEFORE_CLASS_SUFFIX)) {
					onTestFinish(testDetailsOfCurrentTest, ETestExecutionResult.PASSED.toString(),
							MESSAGE_BEFORE_AFTER_TEST + StringUtils
									.stripSuffix(testDetailsOfCurrentTest.internalId, TestDetails.BEFORE_CLASS_SUFFIX));
				} else {
					String message = "For test " + testDetailsOfCurrentTest.externalId + " there was no end event passed to the test listener; assume that it ended now with an error.";
					System.out.println(message);
					onTestFinish(testDetailsOfCurrentTest, ETestExecutionResult.ERROR.name(), message);
				}
			}

			testDetailsOfCurrentTest = testDetails;
			Response<String> response = tiaClient.handleTestStart(testDetails.externalId, testDetails).execute();
			totalResponeTimeMs += response.raw().receivedResponseAtMillis() - response.raw().sentRequestAtMillis();
			System.out.println("Test start ("+testDetails.externalId+"):" + response.body());
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

			Response<String> response = tiaClient.handleTestEnd(testDetails.externalId, consoleOutput, result).execute();
			totalResponeTimeMs += response.raw().receivedResponseAtMillis() - response.raw().sentRequestAtMillis();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getTotalResponeTimeMs() {
		return totalResponeTimeMs;
	}
}
