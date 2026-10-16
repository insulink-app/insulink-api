package de.insulink.api.nutrition.estimate;

import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Builds the headless Claude Code call: the command line and the one stream-json
 * user message that carries the photo over stdin.
 *
 * The CLI runs with NO tools, no MCP servers, no settings files and no saved
 * session. A photo can carry text, and a prompt injected that way must not be
 * able to read the container's {@code config.ini} or reach the network; with no
 * tools there is nothing for it to call.
 */
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimatePrompt {
  private static final String MODEL = "sonnet";
  private static final String SYSTEM_PROMPT = String.join(" ",
    "You estimate the nutrition of a meal from one photo for a diabetes diary.",
    "The carbohydrates set an insulin dose, so too much is as harmful as too",
    "little. List every food on the plate as its own item.",
    "Count only the edible part: a banana or an orange without its peel, meat",
    "or fish without bones, nothing of packaging, plates or cups.",
    "Judge each weight from plates, cutlery, hands and packaging. When unsure,",
    "take the typical portion, not the largest plausible one. Typical portions:",
    "a slice of bread 45 g, a bread roll 60 g, a slice of toast 25 g, a medium",
    "banana 120 g, a medium apple 150 g, a medium potato 100 g, cooked pasta or",
    "rice 200 g as a main dish.",
    "Take each food's nutrients per 100 g from common nutrition tables (for",
    "example mixed rye bread 42 g carbohydrates, banana 21 g) and scale them to",
    "its weight. Give every item's values for its weight, not per 100 g.",
    "name is a short name for the whole meal. When the photo shows no food,",
    "return an empty item list.");
  private static final JSONObject NUMBER_FIELD = new JSONObject()
    .put("type", "number").put("minimum", 0);
  private static final JSONObject ITEM = new JSONObject()
    .put("type", "object")
    .put("properties", new JSONObject()
      .put("name", new JSONObject().put("type", "string"))
      .put("grams", NUMBER_FIELD)
      .put("carbs", NUMBER_FIELD)
      .put("fat", NUMBER_FIELD)
      .put("protein", NUMBER_FIELD)
      .put("kcal", NUMBER_FIELD))
    .put("required", List.of("name", "grams", "carbs", "fat", "protein", "kcal"));
  private static final String SCHEMA = new JSONObject()
    .put("type", "object")
    .put("properties", new JSONObject()
      .put("name", new JSONObject().put("type", "string"))
      .put("items", new JSONObject().put("type", "array").put("items", ITEM)))
    .put("required", List.of("name", "items"))
    .toString();

  private final MealImage image;
  private final String language;

  /** The CLI invocation; arguments only, no shell, so nothing is interpolated. */
  public List<String> command() {
    return List.of("claude", "-p", "--input-format", "stream-json",
      "--output-format", "stream-json", "--verbose", "--tools", "",
      "--strict-mcp-config", "--setting-sources", "", "--no-session-persistence",
      "--model", MODEL, "--json-schema", SCHEMA, "--system-prompt", SYSTEM_PROMPT);
  }

  /** The single user message: the JPEG as a base64 image block, then the ask. */
  public String stdinLine() {
    var content = new JSONArray()
      .put(new JSONObject().put("type", "image").put("source", new JSONObject()
        .put("type", "base64").put("media_type", "image/jpeg")
        .put("data", image.base64())))
      .put(new JSONObject().put("type", "text").put("text", question()));
    return new JSONObject().put("type", "user").put("message", new JSONObject()
      .put("role", "user").put("content", content)) + "\n";
  }

  /** The names come back in the app's language; anything unknown is English. */
  private String question() {
    var languageName = "de".equals(language) ? "German" : "English";
    return "Estimate this meal. Write the meal name and the food names in "
      + languageName + ".";
  }
}
