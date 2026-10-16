package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.json.JSONObject;

import java.util.Map;

/**
 * One food on the plate as Claude reckons it: its edible weight in grams and the
 * carbohydrates, fat and protein in grams and energy in kcal of that weight.
 * Listing the foods one by one keeps the model honest about each portion and
 * shows the user which one an odd total came from.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimateItem {
  private final String name;
  private final double grams;
  private final double carbs;
  private final double fat;
  private final double protein;
  private final double kcal;

  /**
   * One entry of the schema-checked {@code items} array. Food cannot hold more
   * carbohydrates, fat and protein than it weighs, nor more than 9 kcal per
   * gram; values past that (per-100 g values copied onto a lighter piece) are
   * scaled down to what the weight can hold, so they never inflate a dose.
   */
  public static MealEstimateItem fromJson(JSONObject json) {
    var grams = json.getDouble("grams");
    var carbs = json.getDouble("carbs");
    var fat = json.getDouble("fat");
    var protein = json.getDouble("protein");
    var macros = carbs + fat + protein;
    var scale = macros > grams ? grams / macros : 1;
    var kcal = Math.min(json.getDouble("kcal"), grams * 9);
    return create(json.getString("name"), grams, carbs * scale, fat * scale,
      protein * scale, kcal);
  }

  /** The item as snake_case fields, for the response and the stored record. */
  public Map<String, Object> information() {
    return Map.of("name", name, "grams", grams, "carbs", carbs, "fat", fat,
      "protein", protein, "kcal", kcal);
  }
}
