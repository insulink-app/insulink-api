package de.insulink.api.web.app.statistic;

import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.security.app.AppRestController;
import de.insulink.api.statistic.StatisticConfiguration;
import de.insulink.api.statistic.installation.AppInstallation;
import de.insulink.api.statistic.installation.AppInstallationRepository;
import de.insulink.api.statistic.opening.AppOpening;
import de.insulink.api.statistic.opening.AppOpeningRepository;
import de.insulink.api.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StatisticController extends AppRestController {
  private final AppInstallationRepository appInstallationRepository;
  private final AppOpeningRepository appOpeningRepository;
  private final StatisticConfiguration statisticConfiguration;

  private StatisticController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository,
    AppInstallationRepository appInstallationRepository,
    AppOpeningRepository appOpeningRepository,
    StatisticConfiguration statisticConfiguration
  ) {
    super(authenticationKey, userRepository);
    this.appInstallationRepository = appInstallationRepository;
    this.appOpeningRepository = appOpeningRepository;
    this.statisticConfiguration = statisticConfiguration;
  }

  @RequestMapping(path = "/statistic/app/installation/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> noteAppInstallation(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    if (!checkStatisticKey(body)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return appInstallationRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> AppInstallation.create(id, System.currentTimeMillis()))
      .thenCompose(appInstallationRepository::save)
      .thenApply(_ -> Map.of("success", true));
  }

  @RequestMapping(path = "/statistic/app/opening/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> noteAppOpening(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    if (!checkStatisticKey(body)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return appOpeningRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> AppOpening.create(id, System.currentTimeMillis(),
        body.getSanitizedString("version", 32)))
      .thenCompose(appOpeningRepository::save)
      .thenApply(_ -> Map.of("success", true));
  }

  private boolean checkStatisticKey(ApiRequestBody body) {
    var originalKey = statisticConfiguration.statisticKey()
      .getBytes(StandardCharsets.UTF_8);
    var providedKey = body.getString("key").getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(originalKey, providedKey);
  }
}
