package de.insulink.api.inventory;

/**
 * What kind of supply an inventory item is. Persisted by name; the wire form
 * (app JSON) is the lowercase {@link #key()}.
 */
public enum InventoryItemType {
  SENSOR,
  PUMP,
  OTHER;

  public static InventoryItemType fromKey(String key) {
    try {
      return valueOf(key.toUpperCase());
    } catch (IllegalArgumentException | NullPointerException exception) {
      return OTHER;
    }
  }

  public String key() {
    return name().toLowerCase();
  }
}
