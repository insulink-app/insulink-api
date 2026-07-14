package de.insulink.api.locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the lookup contract every {@code LocaleString.of(...)} call site assumes:
 * the nested locale files collapse into dot-separated keys, a node that is both
 * a label and a prefix stays reachable under "_", and an unknown key hands back
 * the key itself rather than null or an empty label. Backed by
 * test resources/locale/flatten-test.json, since the shipped de/en files are
 * still empty.
 */
final class LocaleTest {
  private Locale flattenTestLocale() throws Exception {
    return Locale.createAndLoad("flatten-test");
  }

  @Test
  void topLevelKeyIsFoundUnchanged() throws Exception {
    Assertions.assertEquals("Hallo", flattenTestLocale().findText("greeting"));
  }

  @Test
  void nestedSectionsCollapseIntoDotSeparatedKeys() throws Exception {
    var locale = flattenTestLocale();
    Assertions.assertEquals("Zielwert", locale.findText("profile.glucose.target"));
    Assertions.assertEquals("Name", locale.findText("profile.name"));
    Assertions.assertEquals("mg/dL", locale.findText("profile.glucose.unit.mgdl"));
  }

  @Test
  void aNodeThatIsBothLabelAndPrefixKeepsItsOwnValueUnderUnderscore() throws Exception {
    var locale = flattenTestLocale();
    Assertions.assertEquals("Glukose", locale.findText("profile.glucose._"));
    Assertions.assertEquals("Einheit", locale.findText("profile.glucose.unit._"));
  }

  @Test
  void aPrefixIsNotItselfATranslatableKey() throws Exception {
    Assertions.assertEquals("profile.glucose", flattenTestLocale().findText("profile.glucose"));
  }

  @Test
  void nonStringValuesAreKeptAsText() throws Exception {
    Assertions.assertEquals("50", flattenTestLocale().findText("limits.max_entries"));
  }

  @Test
  void unknownKeyFallsBackToTheKeyItself() throws Exception {
    Assertions.assertEquals("profile.unknown", flattenTestLocale().findText("profile.unknown"));
  }

  @Test
  void languageIsKeptAsGiven() throws Exception {
    Assertions.assertEquals("flatten-test", flattenTestLocale().language());
  }

  @Test
  void missingLocaleFileFailsLoudly() {
    Assertions.assertThrows(Exception.class, () -> Locale.createAndLoad("does-not-exist"));
  }

  @Test
  void addedLocaleContributesItsOwnKeysAndWinsOnTheOverlap() throws Exception {
    var locale = flattenTestLocale();
    locale.addLocale(Locale.createAndLoad("flatten-overlay"));
    Assertions.assertEquals("Pumpe", locale.findText("profile.pump"));
    Assertions.assertEquals("Moin", locale.findText("greeting"));
    Assertions.assertEquals("Zielwert", locale.findText("profile.glucose.target"));
  }

  @Test
  void removingALocaleLeavesTheKeysItNeverTouchedAlone() throws Exception {
    var locale = flattenTestLocale();
    locale.addLocale(Locale.createAndLoad("flatten-overlay"));
    locale.removeLocale(Locale.createAndLoad("flatten-overlay"));
    Assertions.assertEquals("profile.pump", locale.findText("profile.pump"));
    Assertions.assertEquals("Zielwert", locale.findText("profile.glucose.target"));
  }

  /**
   * Removal works on the key set, so a key the overlay shared with the base is
   * dropped outright instead of falling back to the base value. Anything that
   * unloads an overlay has to reload the base afterwards.
   */
  @Test
  void removingALocaleAlsoDropsKeysItSharedWithTheBase() throws Exception {
    var locale = flattenTestLocale();
    locale.addLocale(Locale.createAndLoad("flatten-overlay"));
    locale.removeLocale(Locale.createAndLoad("flatten-overlay"));
    Assertions.assertEquals("greeting", locale.findText("greeting"));
  }
}
