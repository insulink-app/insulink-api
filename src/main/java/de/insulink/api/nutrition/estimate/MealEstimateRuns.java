package de.insulink.api.nutrition.estimate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The estimate each user has running, so the app can call it off when the user
 * takes a new photo or leaves the camera.
 *
 * A cancel names the run it means by the app's run id, so it can never kill the
 * next photo's run. A cancel that arrives before its run is opened (the upload
 * finished just as the user left) is kept and stops that run as it opens.
 * ponytail: in memory like {@link MealEstimateProgress}, one api instance.
 */
public final class MealEstimateRuns {
  private final Map<UUID, MealEstimateRun> running = new HashMap<>();
  private final Map<UUID, String> calledOff = new HashMap<>();

  /** The run for {@code runId}, already cancelled when its cancel came first. */
  public synchronized MealEstimateRun open(UUID userId, String runId) {
    var run = MealEstimateRun.create(runId);
    if (!runId.isEmpty() && calledOff.remove(userId, runId)) {
      run.cancel();
    }
    running.put(userId, run);
    return run;
  }

  /** Cancels the user's run {@code runId}, now or as soon as it opens. */
  public synchronized void cancel(UUID userId, String runId) {
    var run = running.get(userId);
    if (run != null && run.id().equals(runId)) {
      run.cancel();
      return;
    }
    calledOff.put(userId, runId);
  }

  /** Forgets {@code run} once it is over, unless a newer one took its place. */
  public synchronized void close(UUID userId, MealEstimateRun run) {
    running.remove(userId, run);
  }
}
