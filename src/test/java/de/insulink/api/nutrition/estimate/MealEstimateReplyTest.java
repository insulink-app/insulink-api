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
    + "\"structured_output\":{\"name\":\"Brot mit Banane\",\"items\":["
    + "{\"name\":\"Mischbrot\",\"grams\":45,\"carbs\":19,\"fat\":0.5,"
    + "\"protein\":3,\"kcal\":95},"
    + "{\"name\":\"Banane\",\"grams\":120,\"carbs\":25,\"fat\":0.2,"
    + "\"protein\":1.3,\"kcal\":107}]}}";

  @Test
  void theResultLineBecomesTheEstimate() {
    var estimate = MealEstimateReply.create(INIT + "\n" + RESULT + "\n")
      .estimate().orElseThrow();
    Assertions.assertEquals("Brot mit Banane", estimate.name());
    Assertions.assertEquals(2, estimate.items().size());
    Assertions.assertEquals("Banane", estimate.items().get(1).name());
  }

  @Test
  void theTotalsAreTheSumsOfTheItems() {
    var estimate = MealEstimateReply.create(RESULT).estimate().orElseThrow();
    Assertions.assertEquals(165, estimate.grams());
    Assertions.assertEquals(44, estimate.carbs());
    Assertions.assertEquals(202, estimate.kcal());
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

  @Test
  void theLastLineSaysWhyThereIsNoEstimate() {
    var refused = INIT + "\n{\"type\":\"result\",\"result\":\"Not logged in\"}\n";
    Assertions.assertTrue(MealEstimateReply.create(refused).lastLine()
      .contains("Not logged in"));
  }
}
