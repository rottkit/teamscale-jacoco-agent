package com.teamscale.jacoco.agent.agent.controllers;

import com.teamscale.client.TestDetails;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface TestImpactClient {
    @POST("test/start/{testId}")
    Call<String> handleTestStart(@Path("testId") String testId, @Body TestDetails testDetails);

    @POST("test/end/{testId}/{result}")
    Call<String> handleTestEnd(@Path("testId") String testId, @Body String commandLineOutput, @Path("result") String result);

    @POST("dump")
    Call<String> dumpReport(@Body TestDetails testDetails);
}
