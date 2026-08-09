package de.insulink.api.web.app.health;

import de.insulink.api.health.Hba1cReading;
import de.insulink.api.health.Hba1cReadingRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reconciles a user's stored HbA1c readings against what the app pushes, keyed by
 * the instant of measurement: {@link #merge} inserts unseen rows and corrects
 * changed ones without ever deleting (so a device that has not loaded the full
 * history cannot wipe it), while {@link #remove} deletes exactly the rows the app
 * named. Mirrors the sport measurement sync, minus the metric type — an HbA1c
 * table holds one kind of value, so the timestamp alone identifies a reading.
 */
@RequiredArgsConstructor(staticName = "create")
final class Hba1cSync {
  private final Hba1cReadingRepository readingRepository;

  CompletableFuture<ApiResponse> merge(
    List<Hba1cReading> existing, List<Hba1cReading> fresh
  ) {
    var existingByTime = existing.stream().collect(Collectors.toMap(
      Hba1cReading::recordedAt, Function.identity(), (kept, _) -> kept));
    var changes = fresh.stream()
      .map(reading -> reconcile(existingByTime, reading))
      .filter(Objects::nonNull)
      .toList();
    return AsyncIterator.execute(changes, readingRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Deletes exactly the user's readings whose timestamp is in {@code targets},
   * leaving every other reading untouched.
   */
  CompletableFuture<ApiResponse> remove(
    List<Hba1cReading> existing, Set<Long> targets
  ) {
    var toDelete = existing.stream()
      .filter(reading -> targets.contains(reading.recordedAt()))
      .toList();
    return AsyncIterator.execute(toDelete, readingRepository::delete)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Returns the row to save for one pushed reading: the reading itself when new,
   * a corrected copy carrying the existing id when the stored percentage differs,
   * or {@code null} when nothing changed (so it is skipped).
   */
  private Hba1cReading reconcile(
    Map<Long, Hba1cReading> existingByTime, Hba1cReading reading
  ) {
    var match = existingByTime.get(reading.recordedAt());
    if (match == null) {
      return reading;
    }
    if (match.percent() == reading.percent()) {
      return null;
    }
    return Hba1cReading.create(match.id(), reading.userId(),
      reading.percent(), reading.recordedAt());
  }
}
