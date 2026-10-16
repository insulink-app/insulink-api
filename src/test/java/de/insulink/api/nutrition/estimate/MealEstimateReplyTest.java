package de.insulink.api.nutrition.estimate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The CLI's stdout is several stream-json lines; only a successful result line
 * with schema-checked output may become an estimate. Anything else (a usage
 * limit, an expired token, a killed run) must come out empty so the endpoint
 * answers with an error instead of zeros.
 */
final class MealEstimateReplyTest {
  private static final String INIT = "{\"type\":\"system\",\"subtype\":\"init\"}";
  private static final String RESULT = "{\"type\":\"result\",\"is_error\":false,"
    + "\"structured_output\":{\"name\":\"Spaghetti\",\"grams\":350,\"carbs\":72,"
    + "\"fat\":14,\"protein\":18,\"kcal\":490}}";

  @Test
  void theResultLineBecomesTheEstimate() {
    var estimate = MealEstimateReply.create(INIT + "\n" + RESULT + "\n")
      .estimate().orElseThrow();
    Assertions.assertEquals("Spaghetti", estimate.name());
    Assertions.assertEquals(350, estimate.grams());
    Assertions.assertEquals(72, estimate.carbs());
    Assertions.assertEquals(490, estimate.kcal());
  }

  @Test
  void anErrorResultGivesNoEstimate() {
    var failed = RESULT.replace("\"is_error\":false", "\"is_error\":true");
    Assertions.assertTrue(MealEstimateReply.create(failed).estimate().isEmpty());
  }

  @Test
  void aResultWithoutStructuredOutputGivesNoEstimate() {
    var plain = "{\"type\":\"result\",\"is_error\":false,\"result\":\"limit\"}";
    Assertions.assertTrue(MealEstimateReply.create(plain).estimate().isEmpty());
  }

  @Test
  void aKilledRunWithAHalfLineGivesNoEstimate() {
    var cut = INIT + "\n" + RESULT.substring(0, 40);
    Assertions.assertTrue(MealEstimateReply.create(cut).estimate().isEmpty());
  }
}
