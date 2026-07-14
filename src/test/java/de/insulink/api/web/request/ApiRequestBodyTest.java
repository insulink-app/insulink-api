package de.insulink.api.web.request;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pins the two promises every controller leans on when it reads a payload: a
 * missing key never reaches the domain as a silent default, it marks the
 * request as a 400, and free text that ends up on a screen has its markup
 * stripped before anyone stores it.
 */
final class ApiRequestBodyTest {
  private final AtomicInteger status = new AtomicInteger();

  /**
   * A stand-in response that only remembers the status the body set on it.
   * The class under test touches nothing else, so nothing else is answered.
   */
  private HttpServletResponse recordingResponse() {
    return (HttpServletResponse) Proxy.newProxyInstance(
      HttpServletResponse.class.getClassLoader(),
      new Class<?>[]{HttpServletResponse.class},
      (_, method, arguments) -> {
        if (method.getName().equals("setStatus")) {
          status.set((int) arguments[0]);
        }
        return null;
      }
    );
  }

  private ApiRequestBody bodyOf(String payload) {
    return ApiRequestBody.of(payload, recordingResponse());
  }

  @Test
  void readsTheValueBehindAKey() {
    var body = bodyOf("{\"phone_number\": \"+49123\", \"age\": 31}");
    Assertions.assertEquals("+49123", body.getString("phone_number"));
    Assertions.assertEquals(31, body.getInt("age"));
    Assertions.assertEquals(0, status.get());
  }

  @Test
  void missingKeyMarksTheRequestAsBadRequest() {
    var body = bodyOf("{}");
    Assertions.assertEquals("", body.getString("phone_number"));
    Assertions.assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
  }

  @Test
  void missingNumbersAndFlagsFallBackWithoutLookingLikeRealValues() {
    var body = bodyOf("{}");
    Assertions.assertEquals(-1, body.getInt("carbohydrates"));
    Assertions.assertEquals(-1L, body.getLong("recorded_at"));
    Assertions.assertEquals(-1D, body.getDouble("units"));
    Assertions.assertFalse(body.getBoolean("active"));
    Assertions.assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
  }

  @Test
  void overlongStringIsCutToTheAllowedLength() {
    var body = bodyOf("{\"name\": \"Insulinkasten\"}");
    Assertions.assertEquals("Insu", body.getString("name", 4));
  }

  @Test
  void shorterStringSurvivesTheLengthLimitUntouched() {
    var body = bodyOf("{\"name\": \"Max\"}");
    Assertions.assertEquals("Max", body.getString("name", 32));
  }

  @Test
  void sanitizedStringDropsMarkupButKeepsTheText() {
    var body = bodyOf("{\"note\": \"<script>alert(1)</script>Pasta\"}");
    Assertions.assertEquals("Pasta", body.getSanitizedString("note"));
  }

  @Test
  void sanitizedStringLeavesPlainTextReadable() {
    var body = bodyOf("{\"note\": \"Bread & butter, 5 < 6\"}");
    Assertions.assertEquals("Bread & butter, 5 < 6", body.getSanitizedString("note"));
  }

  @Test
  void malformedUuidIsRejectedInsteadOfGuessed() {
    var body = bodyOf("{\"user_id\": \"not-a-uuid\"}");
    Assertions.assertNull(body.getUUID("user_id"));
    Assertions.assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
  }

  @Test
  void wellFormedUuidIsParsed() {
    var body = bodyOf("{\"user_id\": \"3f2504e0-4f89-11d3-9a0c-0305e82c3301\"}");
    Assertions.assertEquals(
      "3f2504e0-4f89-11d3-9a0c-0305e82c3301", body.getUUID("user_id").toString()
    );
    Assertions.assertEquals(0, status.get());
  }

  @Test
  void nestedObjectIsReadThroughTheSameRules() {
    var body = bodyOf("{\"device\": {\"model\": \"Pixel\"}}");
    Assertions.assertEquals("Pixel", body.getObject("device").getString("model"));
  }

  @Test
  void missingNestedObjectYieldsAnEmptyBodyThatStillReportsTheFailure() {
    var body = bodyOf("{}");
    Assertions.assertFalse(body.getObject("device").has("model"));
    Assertions.assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
  }

  @Test
  void objectListKeepsOrderAndReadsEveryEntry() {
    var body = bodyOf("{\"meals\": [{\"name\": \"Pasta\"}, {\"name\": \"Salad\"}]}");
    var meals = body.getObjectList("meals");
    Assertions.assertEquals(2, meals.size());
    Assertions.assertEquals("Pasta", meals.getFirst().getString("name"));
    Assertions.assertEquals("Salad", meals.getLast().getString("name"));
  }

  @Test
  void missingListsComeBackEmptyRatherThanNull() {
    var body = bodyOf("{}");
    Assertions.assertTrue(body.getObjectList("meals").isEmpty());
    Assertions.assertTrue(body.getList("tags").isEmpty());
    Assertions.assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
  }

  @Test
  void valueListIsHandedOutInItsJsonType() {
    var body = bodyOf("{\"tags\": [\"sport\", \"morning\"]}");
    Assertions.assertEquals(java.util.List.of("sport", "morning"), body.getList("tags"));
  }
}
