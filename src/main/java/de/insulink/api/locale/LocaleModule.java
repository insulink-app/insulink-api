package de.insulink.api.locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocaleModule {
  @Bean
  Locales locales() throws Exception {
    return Locales.create();
  }
}