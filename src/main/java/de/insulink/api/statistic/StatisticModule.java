package de.insulink.api.statistic;

import org.apache.commons.configuration2.INIConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatisticModule {
  @Bean
  public StatisticConfiguration statisticConfiguration(INIConfiguration file) {
    var configuration = StatisticConfiguration.create();
    configuration.load(file);
    return configuration;
  }
}
