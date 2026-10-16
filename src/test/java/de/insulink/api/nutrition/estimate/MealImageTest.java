package de.insulink.api.nutrition.estimate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Base64;

/** Only a real JPEG under the cap reaches the CLI and the database. */
final class MealImageTest {
  private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

  private String encode(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Test
  void aJpegIsValid() {
    Assertions.assertTrue(MealImage.fromBase64(encode(JPEG_START)).valid());
  }

  @Test
  void aPngIsRejected() {
    var png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    Assertions.assertFalse(MealImage.fromBase64(encode(png)).valid());
  }

  @Test
  void textThatIsNotBase64IsRejected() {
    Assertions.assertFalse(MealImage.fromBase64("not base64 !").valid());
  }

  @Test
  void aJpegOverTheCapIsRejected() {
    var huge = new byte[MealImage.MAX_BYTES + 1];
    System.arraycopy(JPEG_START, 0, huge, 0, JPEG_START.length);
    Assertions.assertFalse(MealImage.fromBase64(encode(huge)).valid());
  }
}
