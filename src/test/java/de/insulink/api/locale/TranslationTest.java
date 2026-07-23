package de.insulink.api.locale;

import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Turning a key into text for a specific user. Everything that resolves a
 * message eventually lands here, so the failure modes matter more than the happy
 * path: an unknown language and an unknown key both fall back to the key rather
 * than to null or an empty string, which is why a missing translation shows up
 * as a visible key instead of a blank label.
 */
@SpringJUnitConfig(classes = {Translation.class, TranslationTest.Locales.class})
final class TranslationTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @TestConfiguration
  static class Locales {
    @Bean
    de.insulink.api.locale.Locales locales() throws Exception {
      var locales = de.insulink.api.locale.Locales.create();
      locales.addLocale(Locale.createAndLoad("flatten-test"));
      return locales;
    }

    @Bean
    UserRepository userRepository() {
      return Mockito.mock(UserRepository.class);
    }
  }

  @Autowired
  private Translation translation;
  @Autowired
  private UserRepository userRepository;

  private User user;

  @BeforeEach
  void stubUser() {
    user = User.create(USER_ID, "Lukas", "hash", "flatten-test", true, 0L);
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
  }

  @Test
  void aKeyIsResolvedInTheGivenLanguage() {
    Assertions.assertEquals("Hallo",
      translation.translate("flatten-test", "greeting"));
  }

  @Test
  void aNestedKeyIsResolvedByItsDotPath() {
    Assertions.assertEquals("Zielwert",
      translation.translate("flatten-test", "profile.glucose.target"));
  }

  /**
   * A missing translation has to stay visible as its key — a blank label would
   * ship unnoticed.
   */
  @Test
  void anUnknownKeyFallsBackToTheKeyItself() {
    Assertions.assertEquals("profile.unknown",
      translation.translate("flatten-test", "profile.unknown"));
  }

  @Test
  void anUnloadedLanguageFallsBackToTheKeyRatherThanFailing() {
    Assertions.assertEquals("greeting", translation.translate("fr", "greeting"));
  }

  @Test
  void placeholdersAreFilledInTheOrderTheArgumentsArrive() {
    Assertions.assertEquals("Hallo",
      translation.translate("flatten-test", "greeting", "unused"));
    Assertions.assertEquals("von A nach B",
      translation.translate("fr", "von {0} nach {1}", "A", "B"));
  }

  @Test
  void aUsersOwnLanguageIsUsedWhenTranslatingForThatUser() {
    Assertions.assertEquals("Hallo", translation.translateUser(user, "greeting"));
  }

  @Test
  void aLocaleStringCarriesItsKeyAndArgumentsThrough() {
    Assertions.assertEquals("Zielwert", translation.translateUser(user,
      LocaleString.of("profile.glucose.target")));
  }

  @Test
  void translatingByUserIdLooksTheUserUpFirst() {
    Assertions.assertEquals("Hallo",
      translation.translateUser(USER_ID, LocaleString.of("greeting")).join());
  }
}
