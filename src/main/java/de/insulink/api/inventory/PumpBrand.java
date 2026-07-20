package de.insulink.api.inventory;

/**
 * Which pump a pump item restocks (set only when the item is a pump). Kept ready
 * for the upcoming pump support; today only Omnipod is on the roadmap. Persisted
 * by name; the wire form is the lowercase {@link #key()}.
 */
public enum PumpBrand {
  OMNIPOD,
  OTHER;

  public static PumpBrand fromKey(String key) {
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
