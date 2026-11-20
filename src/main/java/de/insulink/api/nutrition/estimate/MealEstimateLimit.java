package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * The subscription's usage limit refused the run. {@code resetsAt} is when it
 * frees up again (epoch ms), 0 when the CLI did not say.
 */
@Getter
@Accessors(fluent = true)
public final class MealEstimateLimit extends IllegalStateException {
  private final long resetsAt;

  public MealEstimateLimit(long resetsAt) {
    super("usage limit reached, resets at " + resetsAt);
    this.resetsAt = resetsAt;
  }

  /** The reset for the app; nothing when it is unknown. */
  public Map<String, Object> information() {
    return resetsAt > 0 ? Map.of("resets_at", resetsAt) : Map.of();
  }
}
