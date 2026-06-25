package de.insulink.api.statistic;

import de.insulink.api.configuration.Configuration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.INIConfiguration;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class StatisticConfiguration implements Configuration {
  private String statisticKey;

  @Override
  public void load(INIConfiguration file) {
    statisticKey = file.getString("statistic.key");
  }
}