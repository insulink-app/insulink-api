package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.json.JSONObject;

import java.util.Map;

/**
 * What Claude reckons is on the plate: a short dish name and the whole pictured
 * portion's weight in grams with its carbohydrates, fat and protein in grams and
 * its energy in kcal. Totals, not per 100 g; the app converts. A guess to be
 * checked by the user, never a dose input on its own.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimate {
  private final String name;
  private final double grams;
  private final double carbs;
  private final double fat;
  private final double protein;
  private final double kcal;

  /** Reads the structured output the CLI validated against the schema. */
  public static MealEstimate fromJson(JSONObject json) {
    return create(json.getString("name"), json.getDouble("grams"),
      json.getDouble("carbs"), json.getDouble("fat"),
      json.getDouble("protein"), json.getDouble("kcal"));
  }

  /** The estimate as the snake_case response fields of the endpoint. */
  public Map<String, Object> information() {
    return Map.of("name", name, "grams", grams, "carbs", carbs, "fat", fat,
      "protein", protein, "kcal", kcal);
  }
}
