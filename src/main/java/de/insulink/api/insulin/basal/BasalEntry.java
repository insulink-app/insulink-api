package de.insulink.api.insulin.basal;

import de.insulink.api.insulin.InsulinType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(name = "basal_entries")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class BasalEntry {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  // Blood glucose input, always in mg/dL unit
  @Column(name = "glucose", nullable = false, updatable = false)
  private float glucose;
  @Column(name = "insulin", nullable = false, updatable = false)
  private float insulin;
  @Enumerated(EnumType.STRING)
  @Column(name = "insulin_type", nullable = false, updatable = false)
  private InsulinType insulinType;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}
