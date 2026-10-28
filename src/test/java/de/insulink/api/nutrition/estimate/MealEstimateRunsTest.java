package de.insulink.api.nutrition.estimate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * A cancel must stop exactly the run it names: the one running, the one whose
 * cancel came first, never the next photo's.
 */
final class MealEstimateRunsTest {
  private final MealEstimateRuns runs = new MealEstimateRuns();
  private final UUID userId = UUID.randomUUID();

  @Test
  void aCancelStopsTheRunItNames() {
    var run = runs.open(userId, "first");
    runs.cancel(userId, "first");
    Assertions.assertTrue(run.cancelled());
  }

  @Test
  void aCancelForAnOldRunLeavesTheNewOneAlone() {
    var run = runs.open(userId, "second");
    runs.cancel(userId, "first");
    Assertions.assertFalse(run.cancelled());
  }

  @Test
  void aCancelThatCameFirstStopsTheRunAsItOpens() {
    runs.cancel(userId, "late");
    Assertions.assertTrue(runs.open(userId, "late").cancelled());
    Assertions.assertFalse(runs.open(userId, "next").cancelled());
  }

  @Test
  void aCancelledRunNeverStartsItsProcess() {
    var run = runs.open(userId, "queued");
    run.cancel();
    Assertions.assertThrows(CancellationException.class,
      () -> run.start(new ProcessBuilder("true")));
  }

  @Test
  void aCancelKillsTheRunningProcess() throws IOException, InterruptedException {
    var run = runs.open(userId, "running");
    var process = run.start(new ProcessBuilder("sleep", "30"));
    run.cancel();
    Assertions.assertNotEquals(0, process.waitFor());
  }
}
