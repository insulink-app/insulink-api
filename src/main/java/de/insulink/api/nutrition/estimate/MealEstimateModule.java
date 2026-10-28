package de.insulink.api.nutrition.estimate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MealEstimateModule {
  @Bean
  public MealPhotoEstimator mealPhotoEstimator() {
    return MealPhotoEstimator.create();
  }

  @Bean
  public MealEstimateProgress mealEstimateProgress() {
    return new MealEstimateProgress();
  }

  @Bean
  public MealEstimateRuns mealEstimateRuns() {
    return new MealEstimateRuns();
  }
}
