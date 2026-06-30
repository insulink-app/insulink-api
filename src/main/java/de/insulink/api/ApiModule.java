package de.insulink.api;

import de.insulink.api.log.Log;
import de.insulink.api.web.WebModule;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;

@Configuration
@Import(WebModule.class)
public class ApiModule {
  @Bean
  Log apiLog() throws Exception {
    return Log.create("Api");
  }

  @Bean
  INIConfiguration configurationFile() throws Exception {
    return new Configurations().ini(new File("configurations/config.ini"));
  }
}
