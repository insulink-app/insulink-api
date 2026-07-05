package de.insulink.api.prediction;

import de.insulink.api.configuration.Configuration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.INIConfiguration;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class PredictionConfiguration implements Configuration {
  private String serviceUrl;

  @Override
  public void load(INIConfiguration file) {
    serviceUrl = file.getString("prediction.service_url");
  }
}
