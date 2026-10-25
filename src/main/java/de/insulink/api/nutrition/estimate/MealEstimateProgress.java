package de.insulink.api.nutrition.estimate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest step of every running estimate, by user, for the app to poll while
 * it waits. Kept in memory: a step is only worth something while its run lasts.
 * ponytail: one api instance, a shared store if the api ever runs twice.
 */
public final class MealEstimateProgress {
  private final Map<UUID, MealEstimateStep> steps = new ConcurrentHashMap<>();

  public void report(UUID userId, MealEstimateStep step) {
    steps.put(userId, step);
  }

  public Optional<MealEstimateStep> current(UUID userId) {
    return Optional.ofNullable(steps.get(userId));
  }

  public void finish(UUID userId) {
    steps.remove(userId);
  }
}
