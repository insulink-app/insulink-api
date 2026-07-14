package de.insulink.api.user.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the platform sniffing behind a session's device entry, using the header
 * strings the browsers and the app actually send. The checks overlap on
 * purpose — an iPhone also says "Mac OS X" and an Android also says "Linux" —
 * so the order they run in is the rule being tested here, not an accident.
 */
final class UserAgentTest {
  private String platformOf(String header) {
    return UserAgent.create(header).findPlatform();
  }

  @Test
  void windowsIsRecognised() {
    Assertions.assertEquals("WINDOWS", platformOf(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    ));
  }

  @Test
  void desktopLinuxIsRecognisedByItsWindowSystem() {
    Assertions.assertEquals("LINUX", platformOf(
      "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0"
    ));
  }

  @Test
  void iphoneWinsOverTheMacItAlsoClaimsToBe() {
    Assertions.assertEquals("IOS", platformOf(
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15"
    ));
  }

  @Test
  void ipadWinsOverTheMacItAlsoClaimsToBe() {
    Assertions.assertEquals("IOS", platformOf(
      "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15"
    ));
  }

  @Test
  void macIsRecognised() {
    Assertions.assertEquals("MAC", platformOf(
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
    ));
  }

  @Test
  void androidIsNotMistakenForTheLinuxItRunsOn() {
    Assertions.assertEquals("ANDROID", platformOf(
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
    ));
  }

  @Test
  void detectionIgnoresCasing() {
    Assertions.assertEquals("ANDROID", platformOf("okhttp/4.12.0 ANDROID"));
  }

  @Test
  void unknownAgentYieldsNoPlatformRatherThanAGuess() {
    Assertions.assertEquals("", platformOf("curl/8.5.0"));
    Assertions.assertEquals("", platformOf(""));
  }
}
