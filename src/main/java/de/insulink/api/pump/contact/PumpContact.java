package de.insulink.api.pump.contact;

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
 * A minute in which the app got a status out of the pump. The pump keeps no log
 * and reports no outage, so the gaps between these rows ARE its downtime.
 * Per user, not per pod: the history outlives each pod.
 */
@Entity
@Table(name = "pump_contacts", uniqueConstraints = @UniqueConstraint(
  name = "uq_pump_contact", columnNames = {"user_id", "contacted_at"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class PumpContact {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  // Minute-aligned epoch ms.
  @Column(name = "contacted_at", nullable = false, updatable = false)
  private long contactedAt;
}
