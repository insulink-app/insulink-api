package de.insulink.api.insulin.delivery;

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
 * One volume the pump put out: a bolus, or the prime and cannula volumes an
 * activation consumes. A record of what the pump did, NOT an insulin source:
 * boluses reach the forecast as meal records and basal as {@code BasalEntry}, so
 * reading this table as insulin on board would count every dose twice.
 */
@Entity
@Table(name = "pump_deliveries", uniqueConstraints = @UniqueConstraint(
  name = "uq_pump_delivery", columnNames = {"user_id", "delivered_at"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class PumpDelivery {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  @Column(name = "delivered_at", nullable = false, updatable = false)
  private long deliveredAt;
  // A slug the app owns: "bolus", "prime" or "cannula".
  @Column(name = "kind", nullable = false, updatable = false)
  private String kind;
  // Who asked for it: "app" or "notification"; empty for prime/cannula.
  @Column(name = "source", nullable = false, updatable = false)
  private String source;
  // Lowered when a bolus is stopped part-way.
  @Column(name = "units", nullable = false)
  private double units;
  // False while the pod never named the bolus back, so it may not have run.
  @Column(name = "confirmed", nullable = false)
  private boolean confirmed;
}
