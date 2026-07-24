package de.insulink.api.web.security;

import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.statistic.StatisticConfiguration;
import de.insulink.api.statistic.installation.AppInstallationRepository;
import de.insulink.api.statistic.opening.AppOpeningRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.app.glucose.GlucoseHistoryController;
import de.insulink.api.web.app.statistic.StatisticController;
import de.insulink.api.web.security.app.AppEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

/**
 * What the authentication filter reads at startup to know which paths to guard.
 * Two things have to hold or the whole guard silently stops working: the paths
 * carry the {@code /v1} prefix the servlet actually sees, and an endpoint
 * without {@code @AppEndpoint} — signup, login, the statistic counters — is not
 * in the list.
 */
@AppControllerTest({GlucoseHistoryController.class, StatisticController.class})
@Import(EndpointRepository.class)
final class EndpointRepositoryTest {
  @Autowired
  private EndpointRepository endpointRepository;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private GlucoseRepository glucoseRepository;
  @MockitoBean
  private AppInstallationRepository installationRepository;
  @MockitoBean
  private AppOpeningRepository openingRepository;
  @MockitoBean
  private StatisticConfiguration statisticConfiguration;

  private List<String> guardedPaths() {
    return endpointRepository.findAnnotatedEndpoints(AppEndpoint.class)
      .stream().map(Endpoint::path).toList();
  }

  @Test
  void anAnnotatedEndpointIsListedUnderItsServletPath() {
    Assertions.assertTrue(guardedPaths().contains("/v1/glucose/history/"),
      guardedPaths().toString());
  }

  @Test
  void anUnannotatedEndpointIsNotGuarded() {
    Assertions.assertFalse(
      guardedPaths().contains("/v1/statistic/app/installation/"),
      guardedPaths().toString());
  }

  @Test
  void everyMappedEndpointShowsUpInTheFullList() {
    var allPaths = endpointRepository.all().stream().map(Endpoint::path).toList();
    Assertions.assertTrue(allPaths.contains("/v1/glucose/history/"),
      allPaths.toString());
    Assertions.assertTrue(allPaths.contains("/v1/statistic/app/installation/"),
      allPaths.toString());
  }

  @Test
  void anEndpointKnowsWhichAnnotationsItCarries() {
    var guarded = endpointRepository.findAnnotatedEndpoints(AppEndpoint.class)
      .getFirst();
    Assertions.assertTrue(guarded.annotations().contains(AppEndpoint.class));
  }
}
