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
 * One calendar day of Google Health / Health Connect data (resting heart rate,
 * sleep minutes + stages, blood oxygen, and the night's hypnogram). The metrics
 * are kept as the app's own JSON blob in {@code data} — a day carries a nested,
 * variable set of metrics, so an opaque blob keyed by ({@code user_id},
 * {@code date_key}) stores it faithfully without field-by-field mapping.
 */
@Entity
@Table(name = "health_days", uniqueConstraints = @UniqueConstraint(
  name = "uq_health_day", columnNames = {"user_id", "date_key"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class HealthDay {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "date_key", nullable = false, updatable = false)
  private String dateKey;
  @Column(name = "data", nullable = false, columnDefinition = "TEXT")
  private String data;
}
