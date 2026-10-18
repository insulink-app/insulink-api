package de.insulink.api.nutrition.estimate;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The CLI must run without tools, so text in a photo cannot make it read the
 * container's secrets; the photo goes over stdin, never on the command line.
 */
final class MealEstimatePromptTest {
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

  private static MealEstimatePrompt prompt(String language) {
    return MealEstimatePrompt.create(MealImage.create(JPEG), language);
  }

  @Test
  void theCliRunsWithoutToolsOrSettings() {
    var command = prompt("de").command();
    var tools = command.indexOf("--tools");
    Assertions.assertEquals("", command.get(tools + 1));
    Assertions.assertTrue(command.contains("--strict-mcp-config"));
    Assertions.assertTrue(command.contains("--no-session-persistence"));
  }

  @Test
  void thePhotoAndTheLanguageGoOverStdin() {
    var content = new JSONObject(prompt("de").stdinLine())
      .getJSONObject("message").getJSONArray("content");
    Assertions.assertEquals(MealImage.create(JPEG).base64(),
      content.getJSONObject(0).getJSONObject("source").getString("data"));
    Assertions.assertTrue(content.getJSONObject(1).getString("text").contains("German"));
  }

  @Test
  void thePackagingIsReadBeforeTheItems() {
    var command = prompt("de").command();
    var schema = command.get(command.indexOf("--json-schema") + 1);
    Assertions.assertTrue(schema.indexOf("visible_text") < schema.indexOf("items"));
    Assertions.assertTrue(new JSONObject(schema).getJSONArray("required").toList()
      .contains("visible_text"));
  }

  @Test
  void thePiecesAreCountedBeforeTheWeight() {
    var command = prompt("de").command();
    var schema = command.get(command.indexOf("--json-schema") + 1);
    Assertions.assertTrue(schema.indexOf("\"count\"") < schema.indexOf("\"grams\""));
    Assertions.assertTrue(schema.indexOf("piece_grams") < schema.indexOf("\"grams\""));
  }

  @Test
  void anUnknownLanguageFallsBackToEnglish() {
    Assertions.assertTrue(prompt("fr").stdinLine().contains("English"));
  }
}
