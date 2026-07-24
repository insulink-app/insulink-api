package de.insulink.api.web.security;

import de.insulink.api.web.WebConfiguration;
import org.apache.commons.configuration2.INIConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The {@code [web]} section a filter test runs against, built in memory instead
 * of read from {@code configurations/config.ini} — that file is deliberately not
 * in the repository. Goes through the real {@code load}, so the parsing the app
 * does at startup is exercised too.
 */
@TestConfiguration
public class TestWebConfiguration {
  public static final String ALLOWED_ORIGIN = "https://panel.insulink.de";
  public static final String SECOND_ALLOWED_ORIGIN = "https://insulink.de";

  @Bean
  WebConfiguration apiConfiguration() {
    var configuration = WebConfiguration.create();
    configuration.load(configurationFile());
    return configuration;
  }

  private INIConfiguration configurationFile() {
    var file = new INIConfiguration();
    file.addProperty("web.port", 8080);
    file.addProperty("web.authentication_key", "authentication-secret");
    file.addProperty("web.refresh_key", "refresh-secret");
    file.addProperty("web.allowed_origins", ALLOWED_ORIGIN);
    file.addProperty("web.allowed_origins", SECOND_ALLOWED_ORIGIN);
    return file;
  }
}
