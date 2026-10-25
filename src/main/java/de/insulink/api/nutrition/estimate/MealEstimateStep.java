package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * Where a running estimate is, for the app to show under "Analysing":
 * {@code looking} at the photo, {@code searching} the web for a product (the
 * query as detail) or {@code estimating} one food (its name as detail). The
 * app localizes the step; the detail is the model's own words.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimateStep {
  private final String step;
  private final String detail;

  /** The first step, reported before the CLI writes anything. */
  public static MealEstimateStep looking() {
    return create("looking", "");
  }

  public Map<String, Object> information() {
    return Map.of("step", step, "detail", detail);
  }
}
