package de.insulink.api.web;

import de.insulink.api.configuration.Configuration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.INIConfiguration;

import java.util.List;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class WebConfiguration implements Configuration {
  private int port;
  private String authenticationKey;
  private String refreshKey;
  private List<String> allowedOrigins;

  @Override
  public void load(INIConfiguration file) {
    port = file.getInt("api.port");
    authenticationKey = file.getString("api.authentication_key");
    refreshKey = file.getString("api.refresh_key");
    allowedOrigins = file.getList(String.class, "api.allowed_origins");
  }
}