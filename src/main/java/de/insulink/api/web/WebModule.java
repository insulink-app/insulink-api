package de.insulink.api.web;

import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.configuration2.INIConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Configuration
public class WebModule {
  @Bean
  WebConfiguration apiConfiguration(INIConfiguration file) {
    var configuration = WebConfiguration.create();
    configuration.load(file);
    return configuration;
  }

  @Bean("verificationKey")
  Key verificationKey(@Qualifier("apiConfiguration") WebConfiguration configuration) {
    return new SecretKeySpec(configuration.verificationKey()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Bean("authenticationKey")
  Key authenticationKey(@Qualifier("apiConfiguration") WebConfiguration configuration) {
    return new SecretKeySpec(configuration.authenticationKey()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Bean("refreshKey")
  Key refreshKey(@Qualifier("apiConfiguration") WebConfiguration configuration) {
    return new SecretKeySpec(configuration.refreshKey()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }
}
