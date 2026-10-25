package de.insulink.api.nutrition.estimate;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines are shaped like a real CLI run with {@code --include-partial-messages}:
 * the answer arrives in pieces cut anywhere, even inside a name, so a food must
 * only be reported once its name is complete, and the meal's own name never.
 */
final class MealEstimateStreamTest {
  private static final String SEARCH = "{\"type\":\"assistant\",\"message\":"
    + "{\"content\":[{\"type\":\"tool_use\",\"name\":\"WebSearch\","
    + "\"input\":{\"query\":\"Kinder Country 23.5 g nutrition\"}}]}}";
  private static final String ANSWER_START = "{\"type\":\"stream_event\","
    + "\"event\":{\"type\":\"content_block_start\",\"content_block\":"
    + "{\"type\":\"tool_use\",\"name\":\"StructuredOutput\"}}}";
  private static final String ANSWER_STOP = "{\"type\":\"stream_event\","
    + "\"event\":{\"type\":\"content_block_stop\"}}";

  private final List<MealEstimateStep> steps = new ArrayList<>();
  private final MealEstimateStream stream = MealEstimateStream.create(steps::add);

  private String piece(String partialJson) {
    return "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\","
      + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":"
      + JSONObject.quote(partialJson) + "}}}";
  }

  private List<String> details() {
    return steps.stream().map(MealEstimateStep::detail).toList();
  }

  @Test
  void aWebSearchIsReportedWithItsQuery() {
    stream.accept(SEARCH);
    Assertions.assertEquals("searching", steps.getFirst().step());
    Assertions.assertEquals(List.of("Kinder Country 23.5 g nutrition"), details());
  }

  @Test
  void eachFoodIsReportedOnceItsNameIsComplete() {
    stream.accept(ANSWER_START);
    stream.accept(piece("{\"visible_text\": \"\", \"name\": \"Toast mit K"));
    stream.accept(piece("äse\", \"items\": [{\"name\": \"To"));
    Assertions.assertTrue(steps.isEmpty());
    stream.accept(piece("ast\", \"count\": 4}, {\"name\": \"Käse \\\"alt\\\"\"}"));
    stream.accept(piece("]}"));
    stream.accept(ANSWER_STOP);
    Assertions.assertEquals(List.of("Toast", "Käse \"alt\""), details());
    Assertions.assertEquals("estimating", steps.getFirst().step());
  }

  @Test
  void piecesOfAnotherToolAndLinesOfAnotherShapeReportNothing() {
    stream.accept(piece("{\"items\": [{\"name\": \"Toast\"}]}"));
    stream.accept("not json");
    stream.accept("{\"type\":\"stream_event\"}");
    Assertions.assertTrue(steps.isEmpty());
  }
}
