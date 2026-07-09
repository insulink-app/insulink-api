package de.insulink.api.web.app.health;

import de.insulink.api.health.PulseSample;
import de.insulink.api.health.PulseSampleRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reconciles a user's stored pulse samples against what the app pushes, keyed by
 * {@code recordedAt}: {@link #merge} inserts unseen timestamps and updates one
 * whose bpm changed, without ever deleting — so an incremental live push never
 * wipes history, and re-pushing an overlapping sample is idempotent.
 */
@RequiredArgsConstructor(staticName = "create")
final class PulseSampleSync {
  private final PulseSampleRepository sampleRepository;

  CompletableFuture<ApiResponse> merge(
    List<PulseSample> existing, List<PulseSample> fresh
  ) {
    var existingByTime = existing.stream().collect(
      Collectors.toMap(PulseSample::recordedAt, Function.identity(), (kept, _) -> kept));
    var changes = fresh.stream()
      .map(sample -> reconcile(existingByTime, sample))
      .filter(Objects::nonNull)
      .toList();
    return AsyncIterator.execute(changes, sampleRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Returns the row to save for one pushed sample: the sample itself when its
   * timestamp is new, a bpm-corrected copy carrying the existing id when the
   * stored bpm differs, or {@code null} when nothing changed (so it is skipped).
   */
  private PulseSample reconcile(
    Map<Long, PulseSample> existingByTime, PulseSample sample
  ) {
    var match = existingByTime.get(sample.recordedAt());
    if (match == null) {
      return sample;
    }
    if (match.bpm() == sample.bpm()) {
      return null;
    }
    return PulseSample.create(
      match.id(), sample.userId(), sample.recordedAt(), sample.bpm());
  }

  Map<String, Object> information(PulseSample sample) {
    return Map.of("t", sample.recordedAt(), "b", sample.bpm());
  }
}
