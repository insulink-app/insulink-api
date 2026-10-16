package de.insulink.api.nutrition.estimate;

import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Optional;

/**
 * The CLI's stream-json stdout: one JSON object per line, ending in a
 * {@code "type": "result"} line that holds the schema-checked
 * {@code structured_output}. Empty when the run failed, timed out, or produced
 * no structured output (an expired token, a usage limit, an empty pipe).
 */
@RequiredArgsConstructor(staticName = "create")
public final class MealEstimateReply {
  private final String stdout;

  public Optional<MealEstimate> estimate() {
    return Arrays.stream(stdout.split("\n"))
      .map(this::parseLine)
      .flatMap(Optional::stream)
      .filter(line -> "result".equals(line.optString("type")))
      .filter(line -> !line.optBoolean("is_error", true))
      .map(line -> line.optJSONObject("structured_output"))
      .filter(output -> output != null)
      .map(this::parseEstimate)
      .flatMap(Optional::stream)
      .findFirst();
  }

  /**
   * The CLI's last words, for the log when there is no estimate: the result
   * line says why ("Not logged in", a usage limit). Cut to 500 characters.
   */
  public String lastLine() {
    var lines = stdout.strip().split("\n");
    var last = lines[lines.length - 1];
    return last.substring(0, Math.min(500, last.length()));
  }

  private Optional<JSONObject> parseLine(String line) {
    try {
      return Optional.of(new JSONObject(line));
    } catch (JSONException _) {
      return Optional.empty();
    }
  }

  private Optional<MealEstimate> parseEstimate(JSONObject output) {
    try {
      return Optional.of(MealEstimate.fromJson(output));
    } catch (JSONException _) {
      return Optional.empty();
    }
  }
}
