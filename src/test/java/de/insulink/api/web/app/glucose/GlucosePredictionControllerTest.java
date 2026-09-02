package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.prediction.PredictionClient;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The forecast is not computed here — the request is handed to the predictor
 * sidecar and its answer passed through. What this endpoint owns is the edge
 * around it: only the two horizons the models are trained for are accepted, the
 * readings the app has not managed to sync yet ride along, and a sidecar that is
 * down or answering nonsense becomes an error code instead of a failed request.
 */
@AppControllerTest(GlucosePredictionController.class)
final class GlucosePredictionControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private PredictionClient predictionClient;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubPredictor() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(predictionClient.predict(Mockito.any(), Mockito.any(),
        Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(new JSONObject()
        .put("horizon_min", 30)
        .put("generated_at", 1_700_000_000_000L)
        .put("curve", List.of(120, 124, 128))));
  }

  private ResultActions backtest(String payload) throws Exception {
    Mockito.when(predictionClient.backtest(Mockito.any(), Mockito.anyInt(),
        Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(new JSONObject()
        .put("horizon_min", 30)
        .put("grid_minutes", 5)
        .put("points", List.of(new JSONObject()
          .put("ts", 1_700_000_000_000L)
          .put("mgdl", 132.0)
          .put("anchor_mgdl", 128.0)))));
    return endpoint.call(post("/glucose/predict/backtest/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  private Long forwardedBound(int position) {
    var bounds = ArgumentCaptor.forClass(Long.class);
    Mockito.verify(predictionClient).backtest(Mockito.any(), Mockito.anyInt(),
      bounds.capture(), bounds.capture());
    return bounds.getAllValues().get(position);
  }

  private ResultActions predict(String payload) throws Exception {
    return endpoint.call(post("/glucose/predict/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  private int forwardedHorizon() {
    var horizon = ArgumentCaptor.forClass(Integer.class);
    Mockito.verify(predictionClient).predict(Mockito.any(), Mockito.any(),
      horizon.capture());
    return horizon.getValue();
  }

  @Test
  void thePredictorsAnswerIsPassedThroughToTheApp() throws Exception {
    predict("{\"horizon\": 30}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.horizon_min").value(30))
      .andExpect(jsonPath("$.generated_at").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.curve[0]").value(120))
      .andExpect(jsonPath("$.curve.length()").value(3));
  }

  @Test
  void theRequestIsMadeForTheUserFromTheToken() throws Exception {
    predict("{\"horizon\": 30}");
    var userId = ArgumentCaptor.forClass(UUID.class);
    Mockito.verify(predictionClient).predict(userId.capture(), Mockito.any(),
      Mockito.anyInt());
    Assertions.assertEquals(USER_ID, userId.getValue());
  }

  @Test
  void anOmittedHorizonFallsBackToThirtyMinutes() throws Exception {
    predict("{}").andExpect(status().isOk());
    Assertions.assertEquals(30, forwardedHorizon());
  }

  @Test
  void theHourHorizonIsAcceptedToo() throws Exception {
    predict("{\"horizon\": 60}").andExpect(status().isOk());
    Assertions.assertEquals(60, forwardedHorizon());
  }

  /**
   * A horizon no model was trained for would come back as a silently wrong
   * curve, so it is refused before it reaches the sidecar.
   */
  @Test
  void anUntrainedHorizonIsRefusedWithoutAskingThePredictor() throws Exception {
    predict("{\"horizon\": 45}")
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1500))
      .andExpect(jsonPath("$.error.message").value("horizon must be 30 or 60"));
    Mockito.verifyNoInteractions(predictionClient);
  }

  /**
   * The app's background sync is debounced, so the newest readings are usually
   * not in the database yet when the forecast is asked for.
   */
  @Test
  void theReadingsTheAppHasNotSyncedYetAreForwardedAlong() throws Exception {
    predict("""
      {"horizon": 30, "readings": [
        {"value": 118.5, "time": 1700000000000},
        {"value": 121.0, "time": 1700000300000}
      ]}""");
    @SuppressWarnings("unchecked")
    var readings = (ArgumentCaptor<List<GlucoseEntry>>) (ArgumentCaptor<?>)
      ArgumentCaptor.forClass(List.class);
    Mockito.verify(predictionClient).predict(Mockito.any(), readings.capture(),
      Mockito.anyInt());
    Assertions.assertEquals(2, readings.getValue().size());
    Assertions.assertEquals(118.5f, readings.getValue().getFirst().value());
    Assertions.assertEquals(1_700_000_300_000L,
      readings.getValue().getLast().recordedAt());
  }

  @Test
  void aRequestWithoutReadingsLetsThePredictorAnchorToTheDatabase()
    throws Exception {
    predict("{\"horizon\": 30}").andExpect(status().isOk());
    @SuppressWarnings("unchecked")
    var readings = (ArgumentCaptor<List<GlucoseEntry>>) (ArgumentCaptor<?>)
      ArgumentCaptor.forClass(List.class);
    Mockito.verify(predictionClient).predict(Mockito.any(), readings.capture(),
      Mockito.anyInt());
    Assertions.assertTrue(readings.getValue().isEmpty());
  }

  @Test
  void aPredictorThatIsDownBecomesAnErrorCodeNotAFailedRequest()
    throws Exception {
    Mockito.when(predictionClient.predict(Mockito.any(), Mockito.any(),
        Mockito.anyInt()))
      .thenReturn(CompletableFuture.failedFuture(
        new IllegalStateException("connection refused")));
    predict("{\"horizon\": 30}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error.code").value(1501))
      .andExpect(jsonPath("$.error.message").value("prediction unavailable"));
  }

  @Test
  void anAnswerMissingTheCurveIsReportedAsUnavailableRatherThanHalfRendered()
    throws Exception {
    Mockito.when(predictionClient.predict(Mockito.any(), Mockito.any(),
        Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(
        new JSONObject().put("horizon_min", 30)));
    predict("{\"horizon\": 30}")
      .andExpect(jsonPath("$.error.code").value(1501));
  }

  @Test
  void theBacktestPassesThePastForecastsThroughForTheAppToScore()
    throws Exception {
    backtest("{\"horizon\": 30, \"since\": 1700000000000, \"until\": 1700086400000}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.grid_minutes").value(5))
      .andExpect(jsonPath("$.points[0].ts").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.points[0].anchor_mgdl").value(128.0));
  }

  /**
   * The app's analysis range selector drives the window, so both bounds ride
   * along to the sidecar untouched.
   */
  @Test
  void theSelectedAnalysisWindowIsForwardedAsItStands() throws Exception {
    var since = System.currentTimeMillis() - Duration.ofDays(7).toMillis();
    backtest("{\"horizon\": 30, \"since\": " + since + "}")
      .andExpect(status().isOk());
    Assertions.assertEquals(since, forwardedBound(0));
  }

  @Test
  void anOmittedWindowLetsTheSidecarPickItsOwnDefault() throws Exception {
    backtest("{\"horizon\": 30}").andExpect(status().isOk());
    Assertions.assertNull(forwardedBound(0));
    Assertions.assertNull(forwardedBound(1));
  }

  /** A year of anchors is a replay of the whole history for one screen. */
  @Test
  void aWindowReachingBeyondNinetyDaysIsCutBackToThem() throws Exception {
    var floor = System.currentTimeMillis() - Duration.ofDays(90).toMillis();
    backtest("{\"horizon\": 30, \"since\": 1000000000000}")
      .andExpect(status().isOk());
    Assertions.assertTrue(forwardedBound(0) >= floor);
  }

  @Test
  void aBacktestForAnUntrainedHorizonIsRefusedTheSameWayAForecastIs()
    throws Exception {
    backtest("{\"horizon\": 45}")
      .andExpect(jsonPath("$.error.code").value(1500));
    Mockito.verify(predictionClient, Mockito.never())
      .backtest(Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any());
  }
}
