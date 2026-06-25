package de.insulink.api.glucose;

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
@Table(name = "glucose_entries")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class GlucoseEntry {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  // Value is always in mg/dL unit
  @Column(name = "value", nullable = false, updatable = false)
  private float value;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}