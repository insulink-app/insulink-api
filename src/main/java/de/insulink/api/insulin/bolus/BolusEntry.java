package de.insulink.api.insulin.bolus;

import de.insulink.api.insulin.InsulinType;
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
 * A single bolus record as delivered by Dexcom: the blood glucose and carb
 * input that fed the calculation, the carb ratio used, and the resulting
 * delivered insulin (units). Blood glucose is always stored in mg/dL.
 */
@Entity
@Table(name = "bolus_entries")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class BolusEntry {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  // Blood glucose input, always in mg/dL unit
  @Column(name = "glucose", nullable = false, updatable = false)
  private float glucose;
  @Column(name = "carbohydrates", nullable = false, updatable = false)
  private float carbohydrates;
  @Column(name = "carbohydrate_ratio", nullable = false, updatable = false)
  private float carbohydrateRatio;
  @Column(name = "insulin", nullable = false, updatable = false)
  private float insulin;
  @Enumerated(EnumType.STRING)
  @Column(name = "insulin_type", nullable = false, updatable = false)
  private InsulinType insulinType;
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private long recordedAt;
}
