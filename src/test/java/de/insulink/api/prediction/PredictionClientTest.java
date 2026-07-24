package de.insulink.api.prediction;

import com.sun.net.httpserver.HttpServer;
import de.insulink.api.glucose.GlucoseEntry;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * The wire contract with the Python predictor, checked against a real socket —
 * the shapes on both sides are agreed between two repositories, so a mock of the
 * http client would only re-state what this code already assumes. What is pinned
 * here is the request the sidecar is promised: the user id as a string, the
 * readings renamed to the sidecar's short keys, and the horizon in minutes.
 */
final class PredictionClientTest {
  private HttpServer predictor;
  private PredictionClient client;
  private String receivedBody;
  private String receivedPath;
  private String reply;
  private int replyStatus;

  @BeforeEach
  void startPredictor() throws IOException {
    reply = "{\"horizon_min\": 30, \"generated_at\": 1700000000000, "
      + "\"curve\": [120, 124]}";
    replyStatus = 200;
    predictor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    predictor.createContext("/", exchange -> {
      receivedPath = exchange.getRequestURI().getPath();
      receivedBody = new String(exchange.getRequestBody().readAllBytes(),
        StandardCharsets.UTF_8);
      var bytes = reply.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(replyStatus, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    predictor.start();
    var configuration = Mockito.mock(PredictionConfiguration.class);
    Mockito.when(configuration.serviceUrl())
      .thenReturn("http://127.0.0.1:" + predictor.getAddress().getPort());
    client = PredictionClient.create(configuration);
  }

  @AfterEach
  void stopPredictor() {
    predictor.stop(0);
  }

  private GlucoseEntry reading(float value, long recordedAt) {
    return GlucoseEntry.create(null, null, value, recordedAt);
  }

  private JSONObject predict(List<GlucoseEntry> readings, int horizon) {
    return client.predict(UUID.randomUUID(), readings, horizon).join();
  }

  @Test
  void theRequestGoesToThePredictEndpointOfTheConfiguredService() {
    predict(List.of(), 30);
    Assertions.assertEquals("/predict", receivedPath);
  }

  @Test
  void theUserIdTravelsAsAString() {
    var userId = UUID.randomUUID();
    client.predict(userId, List.of(), 30).join();
    Assertions.assertEquals(userId.toString(),
      new JSONObject(receivedBody).getString("user_id"));
  }

  @Test
  void theHorizonIsSentInMinutes() {
    predict(List.of(), 60);
    Assertions.assertEquals(60, new JSONObject(receivedBody).getInt("horizon_min"));
  }

  /**
   * The sidecar names a reading {@code ts}/{@code mgdl}, not {@code time}/
   * {@code value} — renaming happens here and nowhere else.
   */
  @Test
  void readingsAreRenamedToTheSidecarsShortKeys() {
    predict(List.of(reading(118.5f, 1_700_000_000_000L),
      reading(121.0f, 1_700_000_300_000L)), 30);
    var readings = new JSONObject(receivedBody).getJSONArray("readings");
    Assertions.assertEquals(2, readings.length());
    Assertions.assertEquals(1_700_000_000_000L,
      readings.getJSONObject(0).getLong("ts"));
    Assertions.assertEquals(118.5, readings.getJSONObject(0).getDouble("mgdl"), 0.001);
    Assertions.assertEquals(1_700_000_300_000L,
      readings.getJSONObject(1).getLong("ts"));
  }

  @Test
  void aRequestWithoutReadingsStillCarriesAnEmptyArray() {
    predict(List.of(), 30);
    Assertions.assertEquals(0,
      new JSONObject(receivedBody).getJSONArray("readings").length());
  }

  @Test
  void theSidecarsAnswerIsHandedBackParsed() {
    var result = predict(List.of(), 30);
    Assertions.assertEquals(30, result.getInt("horizon_min"));
    Assertions.assertEquals(1_700_000_000_000L, result.getLong("generated_at"));
    Assertions.assertEquals(2, result.getJSONArray("curve").length());
  }

  /**
   * A sidecar that answers with something other than JSON must fail the future
   * — the controller turns that into its "prediction unavailable" code.
   */
  @Test
  void anAnswerThatIsNotJsonFailsTheCallRatherThanReturningNothing() {
    reply = "<html>502 Bad Gateway</html>";
    var call = client.predict(UUID.randomUUID(), List.of(), 30);
    Assertions.assertThrows(CompletionException.class, call::join);
  }
}
