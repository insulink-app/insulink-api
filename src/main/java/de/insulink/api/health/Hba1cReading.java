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
 * One laboratory HbA1c result: the percentage (DCCT) plus the instant it was
 * measured.
 *
 * <p>Its own table rather than another {@link de.insulink.api.sport.SportMeasurement}
 * type — an HbA1c is a lab value on its own timeline (a handful of readings a
 * year, entered by hand), not a body/activity metric that a Google Health import
 * writes daily, and the two are read by different screens. Sharing the table
 * would put an unrelated enum constant on every sport measurement query.
 */
@Entity
@Table(name = "hba1c_readings", uniqueConstraints = @UniqueConstraint(
  name = "uq_hba1c_reading", columnNames = {"user_id", "recorded_at"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Hba1cReading {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "percent", nullable = false)
  private double percent;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}
