package de.insulink.api.nutrition.estimate;

import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Turns the CLI's stream-json lines, as they arrive, into the steps the app
 * shows while it waits: every web search with its query, and every food of the
 * answer once its name is fully written. The answer streams in as pieces of
 * JSON ({@code --include-partial-messages}), so a food's name is taken from the
 * text so far as soon as its closing quote is there. Thinking, system and
 * result lines report nothing.
 */
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimateStream {
  private static final String WEB_SEARCH = "WebSearch";
  /** The tool the CLI answers a {@code --json-schema} run through. */
  private static final String ANSWER = "StructuredOutput";
  private static final Pattern NAME =
    Pattern.compile("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  private final Consumer<MealEstimateStep> onStep;
  private final StringBuilder answer = new StringBuilder();
  private boolean answering;
  private int reportedFoods;

  /**
   * Reads one stdout line. A line of an unexpected shape reports nothing: the
   * steps are only a courtesy, they must never break the estimate.
   */
  public void accept(String line) {
    try {
      var json = new JSONObject(line);
      if ("assistant".equals(json.optString("type"))) {
        reportSearches(json.getJSONObject("message").optJSONArray("content"));
      }
      if ("stream_event".equals(json.optString("type"))) {
        follow(json.getJSONObject("event"));
      }
    } catch (JSONException _) {
      return;
    }
  }

  /** The searches of one complete assistant message, so the query is whole. */
  private void reportSearches(JSONArray content) {
    for (var index = 0; content != null && index < content.length(); index++) {
      var block = content.getJSONObject(index);
      if ("tool_use".equals(block.optString("type"))
        && WEB_SEARCH.equals(block.optString("name"))) {
        onStep.accept(MealEstimateStep.create("searching",
          block.getJSONObject("input").optString("query")));
      }
    }
  }

  /** Collects the answer's pieces and reports the foods written so far. */
  private void follow(JSONObject event) {
    switch (event.optString("type")) {
      case "content_block_start" -> answering = ANSWER.equals(
        event.getJSONObject("content_block").optString("name"));
      case "content_block_delta" -> {
        var delta = event.getJSONObject("delta");
        if (answering && "input_json_delta".equals(delta.optString("type"))) {
          answer.append(delta.optString("partial_json"));
          reportFoods();
        }
      }
      case "content_block_stop" -> answering = false;
      default -> { }
    }
  }

  /**
   * The names after {@code "items"}: the meal's own name comes before it and is
   * no food. Each name is reported once, in the order the model writes them.
   */
  private void reportFoods() {
    var items = answer.indexOf("\"items\"");
    if (items < 0) {
      return;
    }
    var names = NAME.matcher(answer.substring(items)).results().toList();
    for (var index = reportedFoods; index < names.size(); index++) {
      onStep.accept(MealEstimateStep.create("estimating",
        unescape(names.get(index).group(1))));
    }
    reportedFoods = names.size();
  }

  private String unescape(String jsonString) {
    return new JSONArray("[\"" + jsonString + "\"]").getString(0);
  }
}
