package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.json.JSONObject;

import java.util.Map;

/**
 * One food on the plate as Claude reckons it: how many pieces it counted (0 for
 * food without pieces, such as rice or a drink), its edible weight in grams and
 * the carbohydrates, fat and protein in grams and energy in kcal of that weight.
 * The count lets the app correct a miscounted plate without a new photo.
 * Listing the foods one by one keeps the model honest about each portion and
 * shows the user which one an odd total came from.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimateItem {
  private final String name;
  private final int count;
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
   * Items stored before the count existed read as 0, no pieces.
   */
  public static MealEstimateItem fromJson(JSONObject json) {
    var grams = json.getDouble("grams");
    var carbs = json.getDouble("carbs");
    var fat = json.getDouble("fat");
    var protein = json.getDouble("protein");
    var macros = carbs + fat + protein;
    var scale = macros > grams ? grams / macros : 1;
    var kcal = Math.min(json.getDouble("kcal"), grams * 9);
    return create(json.getString("name"), json.optInt("count", 0), grams,
      carbs * scale, fat * scale, protein * scale, kcal);
  }

  /** The item as snake_case fields, for the response and the stored record. */
  public Map<String, Object> information() {
    return Map.of("name", name, "count", count, "grams", grams, "carbs", carbs,
      "fat", fat, "protein", protein, "kcal", kcal);
  }
}
