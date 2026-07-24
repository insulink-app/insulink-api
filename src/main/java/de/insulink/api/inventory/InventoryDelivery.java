package de.insulink.api.inventory;

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

/**
 * A planned delivery for an {@link InventoryItem}: when it arrives and how many
 * units. Its own row (referencing the item by {@code itemId}) rather than a JSON
 * blob, and denormalised with {@code userId} so a user's deliveries load and
 * clear in one query during the replace-all sync.
 */
@Entity
@Table(name = "inventory_deliveries")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class InventoryDelivery {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;
  @Column(name = "arrives_at", nullable = false, updatable = false)
  private long arrivesAt;
  @Column(name = "quantity", nullable = false, updatable = false)
  private int quantity;
}
