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

  /** One entry of the schema-checked {@code items} array. */
  public static MealEstimateItem fromJson(JSONObject json) {
    return create(json.getString("name"), json.getDouble("grams"),
      json.getDouble("carbs"), json.getDouble("fat"),
      json.getDouble("protein"), json.getDouble("kcal"));
  }

  /** The item as snake_case fields, for the response and the stored record. */
  public Map<String, Object> information() {
    return Map.of("name", name, "grams", grams, "carbs", carbs, "fat", fat,
      "protein", protein, "kcal", kcal);
  }
}
