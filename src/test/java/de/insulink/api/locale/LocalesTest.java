package de.insulink.api.locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The registry of loaded languages and how a key becomes text in one of them.
 * The rules that matter at a call site: a language nobody loaded falls back to
 * the key rather than failing, placeholders are filled positionally, and a
 * language is looked up by exactly the string the user has stored.
 */
final class LocalesTest {
  private Locales locales;

  @BeforeEach
  void loadTestLocales() throws Exception {
    locales = Locales.create();
    locales.addLocale(Locale.createAndLoad("flatten-test"));
  }

  @Test
  void theShippedLanguagesAreLoadedOnCreation() throws Exception {
    Assertions.assertTrue(Locales.create().hasLanguage("de"));
    Assertions.assertTrue(Locales.create().hasLanguage("en"));
  }

  @Test
  void aLanguageThatWasNeverLoadedIsNotKnown() {
    Assertions.assertFalse(locales.hasLanguage("fr"));
    Assertions.assertTrue(locales.findLocale("fr").isEmpty());
  }

  @Test
  void anAddedLanguageIsFoundUnderItsOwnName() {
    Assertions.assertTrue(locales.hasLanguage("flatten-test"));
    Assertions.assertEquals("Hallo",
      locales.findLocale("flatten-test").orElseThrow().findText("greeting"));
  }

  @Test
  void theLanguageListHoldsEveryLoadedLanguage() {
    Assertions.assertTrue(locales.languages().containsAll(
      java.util.List.of("de", "en", "flatten-test")));
  }

  /**
   * Adding a second file for a language merges into the one already there
   * instead of replacing it.
   */
  @Test
  void addingASecondFileForALoadedLanguageMergesIntoIt() throws Exception {
    locales.addLocale(Locale.createAndLoad("flatten-test"));
    Assertions.assertEquals(3, locales.languages().size());
    Assertions.assertEquals("Hallo",
      locales.findLocale("flatten-test").orElseThrow().findText("greeting"));
  }

  @Test
  void removingALanguageThatWasNeverLoadedChangesNothing() throws Exception {
    locales.removeLocale(Locale.createAndLoad("flatten-overlay"));
    Assertions.assertTrue(locales.hasLanguage("flatten-test"));
  }
}
