package de.insulink.api.health;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface PulseSampleRepository
  extends DatabaseRepository<PulseSample, UUID> {
  @Async
  CompletableFuture<List<PulseSample>> findByUserIdOrderByRecordedAt(UUID userId);

  /**
   * Inserts the sample, or updates the bpm of the user's row at the same
   * instant. Atomic in Postgres, so two overlapping pushes of the same tail can
   * no longer collide on {@code uq_health_pulse_sample}.
   */
  @Async
  @Modifying
  @Transactional
  @Query(nativeQuery = true, value = """
    insert into health_pulse_samples (id, user_id, recorded_at, bpm)
    values (:#{#sample.id()}, :#{#sample.userId()}, :#{#sample.recordedAt()},
      :#{#sample.bpm()})
    on conflict (user_id, recorded_at) do update set bpm = excluded.bpm
    """)
  CompletableFuture<Integer> upsert(@Param("sample") PulseSample sample);
}
