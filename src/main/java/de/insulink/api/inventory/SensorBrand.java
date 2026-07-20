package de.insulink.api.inventory;

/**
 * Which CGM a sensor item restocks (set only when the item is a sensor).
 * Persisted by name; the wire form is the lowercase {@link #key()}.
 */
public enum SensorBrand {
  LIBRE,
  DEXCOM,
  OTHER;

  public static SensorBrand fromKey(String key) {
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
