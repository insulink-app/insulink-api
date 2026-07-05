package de.insulink.api.prediction;

import org.apache.commons.configuration2.INIConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PredictionModule {
  @Bean
  public PredictionConfiguration predictionConfiguration(INIConfiguration file) {
    var configuration = PredictionConfiguration.create();
    configuration.load(file);
    return configuration;
  }

  @Bean
  public PredictionClient predictionClient(PredictionConfiguration configuration) {
    return PredictionClient.create(configuration);
  }
}
