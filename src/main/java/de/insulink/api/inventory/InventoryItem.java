package de.insulink.api.inventory;

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
 * A tracked supply in the user's inventory (sensor, pump part, other): how much
 * is on hand, its full/target amount, and how long one unit lasts. The app's own
 * item id is stored in {@code itemId} so a pull restores the same identity. The
 * planned deliveries are their own rows ({@link InventoryDelivery}). Synced
 * replace-all like the nutrition collections.
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class InventoryItem {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "item_id", nullable = false, updatable = false)
  private String itemId;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "stock", nullable = false, updatable = false)
  private int stock;
  @Column(name = "base_stock", nullable = false, updatable = false)
  private int baseStock;
  @Column(name = "days_per_unit", nullable = false, updatable = false)
  private double daysPerUnit;
  @Column(name = "anchor_ms", nullable = false, updatable = false)
  private long anchorMs;
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private InventoryItemType type;
  @Enumerated(EnumType.STRING)
  @Column(name = "sensor_brand", updatable = false)
  private SensorBrand sensorBrand;
  @Enumerated(EnumType.STRING)
  @Column(name = "pump_brand", updatable = false)
  private PumpBrand pumpBrand;
}
