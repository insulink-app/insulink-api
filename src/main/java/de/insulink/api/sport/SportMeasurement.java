package de.insulink.api.sport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * A single body/activity measurement (steps, distance in km, calories, or weight
 * in kg) as a value + timestamp — the activity counterpart of a glucose reading.
 */
@Entity
@Table(name = "sport_measurements")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class SportMeasurement {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private SportMeasurementType type;
  @Column(name = "value", nullable = false, updatable = false)
  private double value;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}
