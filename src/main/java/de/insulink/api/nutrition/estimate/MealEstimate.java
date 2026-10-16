package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * What Claude reckons is on the plate: a short name for the meal and every food
 * on it as a {@link MealEstimateItem}. The totals are the sums of the items, so
 * they can never disagree with the breakdown the user sees. A guess to be
 * checked by the user, never a dose input on its own. No items means no food.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimate {
  private final String name;
  private final List<MealEstimateItem> items;

  /** Reads the structured output the CLI validated against the schema. */
  public static MealEstimate fromJson(JSONObject json) {
    return create(json.getString("name"), parseItems(json.getJSONArray("items")));
  }

  /** The items of a JSON array, as the schema and {@link #itemsJson()} write them. */
  public static List<MealEstimateItem> parseItems(JSONArray array) {
    return IntStream.range(0, array.length())
      .mapToObj(index -> MealEstimateItem.fromJson(array.getJSONObject(index)))
      .toList();
  }

  public double grams() {
    return total(MealEstimateItem::grams);
  }

  public double carbs() {
    return total(MealEstimateItem::carbs);
  }

  public double fat() {
    return total(MealEstimateItem::fat);
  }

  public double protein() {
    return total(MealEstimateItem::protein);
  }

  public double kcal() {
    return total(MealEstimateItem::kcal);
  }

  /** The items as a JSON array, the form they are stored in. */
  public String itemsJson() {
    return new JSONArray(items.stream().map(MealEstimateItem::information).toList())
      .toString();
  }

  /** The totals and the items as the snake_case response fields. */
  public Map<String, Object> information() {
    var information = new HashMap<String, Object>(Map.of("name", name,
      "grams", grams(), "carbs", carbs(), "fat", fat(), "protein", protein(),
      "kcal", kcal()));
    information.put("items",
      items.stream().map(MealEstimateItem::information).toList());
    return information;
  }

  private double total(ToDoubleFunction<MealEstimateItem> nutrient) {
    return items.stream().mapToDouble(nutrient).sum();
  }
}
