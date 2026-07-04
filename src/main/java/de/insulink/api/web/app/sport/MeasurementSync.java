package de.insulink.api.web.app.sport;

import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.sport.SportMeasurement;
import de.insulink.api.sport.SportMeasurementRepository;
import de.insulink.api.sport.SportMeasurementType;
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
 * Reconciles a user's stored measurements against what the app pushes, keyed by
 * the natural key (type + timestamp): {@link #merge} inserts unseen rows and
 * updates changed ones without ever deleting (so a Google Health import cannot
 * wipe history the app has not loaded), while {@link #remove} deletes exactly the
 * rows the app explicitly asked to delete — nothing else.
 */
@RequiredArgsConstructor(staticName = "create")
final class MeasurementSync {
  private final SportMeasurementRepository measurementRepository;

  /**
   * Identity of a measurement within one user: its metric and the instant it was
   * recorded. Backed by the {@code uq_sport_measurement} unique constraint.
   */
  record MeasurementKey(SportMeasurementType type, long recordedAt) {
    static MeasurementKey of(SportMeasurement measurement) {
      return new MeasurementKey(measurement.type(), measurement.recordedAt());
    }

    static MeasurementKey of(SportMeasurementType type, long recordedAt) {
      return new MeasurementKey(type, recordedAt);
    }
  }

  CompletableFuture<ApiResponse> merge(
    List<SportMeasurement> existing, List<SportMeasurement> fresh
  ) {
    var existingByKey = existing.stream().collect(
      Collectors.toMap(MeasurementKey::of, Function.identity(), (kept, _) -> kept));
    var changes = fresh.stream()
      .map(entry -> reconcile(existingByKey, entry))
      .filter(Objects::nonNull)
      .toList();
    return AsyncIterator.execute(changes, measurementRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Deletes exactly the user's rows whose natural key is in {@code targets},
   * leaving every other measurement untouched.
   */
  CompletableFuture<ApiResponse> remove(
    List<SportMeasurement> existing, Set<MeasurementKey> targets
  ) {
    var toDelete = existing.stream()
      .filter(entry -> targets.contains(MeasurementKey.of(entry)))
      .toList();
    return AsyncIterator.execute(toDelete, measurementRepository::delete)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Returns the row to save for one pushed entry: the entry itself when new, a
   * value-corrected copy carrying the existing id when the stored value differs,
   * or {@code null} when nothing changed (so it is skipped).
   */
  private SportMeasurement reconcile(
    Map<MeasurementKey, SportMeasurement> existingByKey, SportMeasurement entry
  ) {
    var match = existingByKey.get(MeasurementKey.of(entry));
    if (match == null) {
      return entry;
    }
    if (match.value() == entry.value()) {
      return null;
    }
    return SportMeasurement.create(match.id(), entry.userId(), entry.type(),
      entry.value(), entry.recordedAt());
  }
}
