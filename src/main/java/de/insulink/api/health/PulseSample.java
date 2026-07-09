package de.insulink.api.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * One granular intraday heart-rate point: a bpm at {@code recordedAt} (epoch
 * millis), keyed by ({@code user_id}, {@code recorded_at}). The app buckets the
 * worn-band pulse to one averaged point per minute (so the timestamps are
 * minute-aligned and unique per minute), letting this archive redraw the full
 * heart-rate curve on its own — without Health Connect — while the day archive
 * ({@link HealthDay}) only carries the day aggregates.
 */
@Entity
@Table(name = "health_pulse_samples", uniqueConstraints = @UniqueConstraint(
  name = "uq_health_pulse_sample", columnNames = {"user_id", "recorded_at"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class PulseSample {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
  @Column(name = "bpm", nullable = false)
  private int bpm;
}
