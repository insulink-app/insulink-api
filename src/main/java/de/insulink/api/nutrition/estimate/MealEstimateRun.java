package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.util.concurrent.CancellationException;

/**
 * One estimate the user can call off while it runs. It holds the CLI process
 * once started, so a cancel can kill it: the model otherwise keeps spending the
 * subscription's tokens on an answer the app no longer waits for. A cancel
 * that comes before the process starts (the run still queued) keeps it from
 * starting at all.
 */
@RequiredArgsConstructor(staticName = "create")
@Accessors(fluent = true)
public final class MealEstimateRun {
  /** The app's id for this run, empty from an app that cannot cancel. */
  @Getter
  private final String id;
  private Process process;
  @Getter
  private volatile boolean cancelled;

  /** Starts the CLI, or refuses when the run was called off before. */
  public synchronized Process start(ProcessBuilder builder) throws IOException {
    if (cancelled) {
      throw new CancellationException("meal estimate cancelled");
    }
    process = builder.start();
    return process;
  }

  /** Calls the run off and kills its CLI, which ends the read of its output. */
  public synchronized void cancel() {
    cancelled = true;
    if (process != null) {
      process.destroyForcibly();
    }
  }
}
