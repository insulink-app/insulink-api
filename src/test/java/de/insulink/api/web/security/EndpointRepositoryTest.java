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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * What the authentication filter asks to know whether to guard a request. It
 * has to agree with the dispatcher on every spelling of a path: the guard used
 * to compare the raw URI, so {@code /v1/glucose/%68istory/} reached the
 * controller unguarded. An endpoint without {@code @AppEndpoint} — signup,
 * login, the statistic counters — must stay open.
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

  private boolean guarded(String method, String uri) {
    var request = new MockHttpServletRequest(method, uri);
    request.setContextPath("/v1");
    return endpointRepository.routesToAnnotated(request, AppEndpoint.class);
  }

  @Test
  void anAnnotatedEndpointIsGuarded() {
    Assertions.assertTrue(guarded("GET", "/v1/glucose/history/"));
  }

  @Test
  void anEncodedSpellingOfAnAnnotatedPathIsGuardedToo() {
    Assertions.assertTrue(guarded("GET", "/v1/glucose/%68istory/"));
  }

  @Test
  void anUnannotatedEndpointIsNotGuarded() {
    Assertions.assertFalse(guarded("POST", "/v1/statistic/app/installation/"));
  }

  @Test
  void anUnknownPathIsLeftToTheNotFoundAnswer() {
    Assertions.assertFalse(guarded("GET", "/v1/does/not/exist/"));
  }

  @Test
  void aWrongMethodOnAGuardedPathFailsClosed() {
    Assertions.assertTrue(guarded("DELETE", "/v1/glucose/history/"));
  }
}
