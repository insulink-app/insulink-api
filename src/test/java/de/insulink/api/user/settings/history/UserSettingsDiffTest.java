package de.insulink.api.user.settings.history;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The app pushes the complete settings blob on every save, so the diff must turn
 * an unchanged push into nothing and a changed one into exactly the keys that
 * moved — added, changed and removed alike.
 */
final class UserSettingsDiffTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long CHANGED_AT = 1_700_000_000_000L;

  private final UserSettingsDiff diff = UserSettingsDiff.create(USER_ID, CHANGED_AT);

  private List<String> describe(String oldContent, String newContent) {
    return diff.changes(oldContent, newContent).stream()
      .map(change -> change.settingKey() + ":" + change.oldValue() + "->" + change.newValue())
      .toList();
  }

  @Test
  void anUnchangedBlobRecordsNothing() {
    var content = "{\"bolus_carb_factor\":\"15\",\"glucose_unit\":\"mgdl\"}";
    Assertions.assertTrue(diff.changes(content, content).isEmpty());
  }

  @Test
  void onlyTheKeyThatMovedIsRecorded() {
    Assertions.assertEquals(List.of("bolus_carb_factor:15->12"), describe(
      "{\"bolus_carb_factor\":\"15\",\"glucose_unit\":\"mgdl\"}",
      "{\"bolus_carb_factor\":\"12\",\"glucose_unit\":\"mgdl\"}"));
  }

  @Test
  void addedAndRemovedKeysCarryANullOnTheMissingSide() {
    Assertions.assertEquals(Arrays.asList("bolus_max:null->10", "theme:dark->null"),
      describe("{\"theme\":\"dark\"}", "{\"bolus_max\":\"10\"}"));
  }

  @Test
  void aBooleanFromThePanelEqualsTheSameValueAsAnAppString() {
    Assertions.assertTrue(diff.changes(
      "{\"silent_mode\":\"true\"}", "{\"silent_mode\":true}").isEmpty());
  }

  @Test
  void everyChangeIsStampedWithTheUserAndTime() {
    var change = diff.changes("{}", "{\"bolus_max\":\"10\"}").getFirst();
    Assertions.assertEquals(USER_ID, change.userId());
    Assertions.assertEquals(CHANGED_AT, change.changedAt());
  }
}
