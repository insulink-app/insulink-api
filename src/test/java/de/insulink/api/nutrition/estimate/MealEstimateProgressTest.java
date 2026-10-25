package de.insulink.api.nutrition.estimate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * The app polls the step while it waits: it must see the latest one of its own
 * run only, and nothing once the run is over.
 */
final class MealEstimateProgressTest {
  private final MealEstimateProgress progress = new MealEstimateProgress();
  private final UUID userId = UUID.randomUUID();

  @Test
  void theLatestStepOfTheUsersRunIsCurrent() {
    progress.report(userId, MealEstimateStep.looking());
    progress.report(userId, MealEstimateStep.create("estimating", "Toast"));
    var step = progress.current(userId).orElseThrow();
    Assertions.assertEquals("estimating", step.step());
    Assertions.assertEquals("Toast", step.information().get("detail"));
    Assertions.assertTrue(progress.current(UUID.randomUUID()).isEmpty());
  }

  @Test
  void aFinishedRunHasNoStep() {
    progress.report(userId, MealEstimateStep.looking());
    progress.finish(userId);
    Assertions.assertTrue(progress.current(userId).isEmpty());
  }
}
