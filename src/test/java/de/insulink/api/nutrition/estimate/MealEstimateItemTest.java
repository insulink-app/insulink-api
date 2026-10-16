package de.insulink.api.nutrition.estimate;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A model that copies per-100 g values onto a 23.5 g bar must not hand the
 * bolus calculator more carbohydrates than the bar weighs.
 */
final class MealEstimateItemTest {
  private static MealEstimateItem item(double grams, double carbs, double kcal) {
    return MealEstimateItem.fromJson(new JSONObject().put("name", "Riegel")
      .put("grams", grams).put("carbs", carbs).put("fat", 30)
      .put("protein", 10).put("kcal", kcal));
  }

  @Test
  void macrosHeavierThanTheFoodAreScaledToItsWeight() {
    var bar = item(23.5, 60, 560);
    Assertions.assertEquals(23.5, bar.carbs() + bar.fat() + bar.protein(), 0.001);
    Assertions.assertEquals(23.5 * 9, bar.kcal(), 0.001);
  }

  @Test
  void plausibleValuesStayAsTheyAre() {
    var bar = item(100, 53, 560);
    Assertions.assertEquals(53, bar.carbs());
    Assertions.assertEquals(560, bar.kcal());
  }
}
