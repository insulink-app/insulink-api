package de.insulink.api.nutrition.estimate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Base64;

/**
 * The meal photo as the app sent it: a base64 JPEG the app already shrank and
 * compressed. Only a real JPEG under {@link #MAX_BYTES} counts as valid, so
 * nothing else reaches the CLI or the database.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class MealImage {
  public static final int MAX_BYTES = 2 * 1024 * 1024;

  private final byte[] bytes;

  /** Decodes the base64 text; anything that is not base64 becomes empty. */
  public static MealImage fromBase64(String base64) {
    try {
      return create(Base64.getDecoder().decode(base64));
    } catch (IllegalArgumentException _) {
      return create(new byte[0]);
    }
  }

  /** Non-empty, within the size cap, and starting with the JPEG marker. */
  public boolean valid() {
    return bytes.length > 3 && bytes.length <= MAX_BYTES
      && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
      && (bytes[2] & 0xFF) == 0xFF;
  }

  public String base64() {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
