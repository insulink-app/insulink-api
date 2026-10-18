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
  private static final String MODEL = "opus";
  private static final String SYSTEM_PROMPT = String.join(" ",
    "You estimate the nutrition of a meal from one photo for a diabetes diary.",
    "The carbohydrates set an insulin dose, so too much is as harmful as too",
    "little. Work in three steps, in the order of the answer's fields.",
    "Step 1, visible_text: every brand, product name, size and nutrition value",
    "you can read on packaging, labels or menus, empty when there is none.",
    "Step 2, counting: a wrong count is the most common and the most costly",
    "mistake, so count before you weigh. Scan the whole photo, every corner and",
    "the edges of the frame, and count each piece one by one. Pieces often hide:",
    "slices of toast or bread stacked, fanned out or overlapping show only their",
    "crust edges, so count the edges and the layers, not only the fully visible",
    "tops. A stack's height tells how many slices it holds (a toast slice is",
    "about 1 cm thick). Sandwiches hold two slices each. Count halved or cut",
    "pieces as halves (two toast halves are one slice). Count pieces lying in",
    "the background, on a second plate or in a hand too. Look closely before",
    "putting two different foods into one item: a coconut macaroon is not a",
    "biscuit, a roll is not a slice of bread.",
    "Step 3, items: one item per kind of food. Its name is the plain food in",
    "one or two words, as a person writes it in a diary: no parentheses, no",
    "piece count, no cut, shape or preparation. Not \"Toast bread (sandwich",
    "toast, 4 slices cut into triangles)\" but \"Toast\", not \"Cheese (Gouda),",
    "melted\" but \"Cheese\". A branded product keeps its short product name",
    "(Kinder Country); the details belong in visible_text.",
    "count is only for food eaten piece by piece that a person would count on",
    "the plate: slices of bread or toast, rolls, biscuits, bars, eggs, whole",
    "fruits, dumplings. It holds the number of pieces and piece_grams the",
    "weight of one; grams is then count times piece_grams. Everything else",
    "takes count 0 and piece_grams 0, and grams is the judged portion:",
    "toppings and fillings such as cheese, sausage, ham, butter, jam or",
    "spreads, even when they lie in slices, and rice, pasta, soup, salad,",
    "vegetables, sauces and drinks. Toppings and spreads on bread are items",
    "of their own.",
    "Count only the edible part: a banana or an orange without its peel, meat",
    "or fish without bones, nothing of packaging, plates or cups.",
    "Judge each weight from plates, cutlery, hands and packaging. When unsure,",
    "take the typical portion, not the largest plausible one. Typical portions:",
    "a slice of bread 45 g, a bread roll 60 g, a slice of toast 25 g, a medium",
    "banana 120 g, a medium apple 150 g, a medium potato 100 g, cooked pasta or",
    "rice 200 g as a main dish, a coconut macaroon 20 g, a croissant 60 g, a",
    "pretzel 80 g, butter on a slice 5 g, jam or Nutella on a slice 15 g.",
    "Packaged foods: count only the pieces that are visible and about to be",
    "eaten, never the whole pack or multipack. One bar, biscuit or chocolate",
    "piece is one piece at its single weight (a chocolate bar such as Kinder",
    "Country 23.5 g, a Snickers 50 g, a biscuit 10 g). A wrapper alone means",
    "one piece. Nutrition labels list values per 100 g; scale them to the",
    "piece weight, never copy them.",
    "Drinks: take the volume from the printed size of the bottle, can or",
    "carton (0.33 l, 0.5 l), else from the glass; 1 ml counts as 1 g. Water,",
    "unsweetened coffee or tea and zero, light or sugar-free drinks have no",
    "carbohydrates. Shandy, Radler and Fassbrause hold about 5 g per 100 ml,",
    "half of a regular lemonade or cola (about 10 g).",
    "Nutrients per 100 g, in this order: values printed on the packaging; else",
    "the known values of the branded product you recognise; only else common",
    "nutrition tables (for example mixed rye bread 42 g carbohydrates, banana",
    "21 g). Scale them to the item's weight. Give every item's values for its",
    "weight, not per 100 g.",
    "name is a short name for the whole meal, at most three words. When the",
    "photo shows no food,",
    "return an empty item list.");
  /**
   * The answer's shape. A text block, not a {@link JSONObject}: that one keeps
   * no key order, and visible_text has to come first so the model reads the
   * packaging before it writes a single number. Likewise count and piece_grams
   * come before grams, so every weight starts from pieces counted one by one;
   * the server keeps the count, so the app can correct it without a new photo.
   */
  private static final String SCHEMA = """
    {"type": "object",
     "properties": {
       "visible_text": {"type": "string"},
       "name": {"type": "string"},
       "items": {"type": "array", "items": {"type": "object",
         "properties": {
           "name": {"type": "string"},
           "count": {"type": "integer", "minimum": 0},
           "piece_grams": {"type": "number", "minimum": 0},
           "grams": {"type": "number", "minimum": 0},
           "carbs": {"type": "number", "minimum": 0},
           "fat": {"type": "number", "minimum": 0},
           "protein": {"type": "number", "minimum": 0},
           "kcal": {"type": "number", "minimum": 0}},
         "required": ["name", "count", "piece_grams", "grams", "carbs", "fat",
           "protein", "kcal"]}}},
     "required": ["visible_text", "name", "items"]}
    """;

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
