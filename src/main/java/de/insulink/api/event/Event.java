package de.insulink.api.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Event {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  // A stable slug the app maps to a label/icon, e.g. "glucose_low", "signal_loss"
  @Column(name = "type", nullable = false, updatable = false)
  private String type;
  // Event-specific payload, e.g. the triggering glucose value in mg/dL as text.
  // Empty (never null) for events without a payload, so the column stays NOT NULL.
  @Column(name = "data", nullable = false, updatable = false)
  private String data;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}
