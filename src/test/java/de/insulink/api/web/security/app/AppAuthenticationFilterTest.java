package de.insulink.api.web.security.app;

import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import de.insulink.api.web.TestAuthentication;
import de.insulink.api.web.security.EndpointRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The gate in front of every {@code @AppEndpoint}. Its status codes are the
 * contract the app's http layer switches on and are deliberately not the usual
 * ones: 417 means "refresh the token and retry", 403 means "this session is
 * over, clear it". Unannotated paths — signup, login, refresh — are not guarded
 * here at all, or nobody could ever obtain a token. A token only counts while
 * the session it names is open, so a logout ends it at once.
 */
@SpringJUnitConfig(classes = {AppAuthenticationFilter.class,
  AppAuthenticationFilterTest.Endpoints.class, TestAuthentication.class})
final class AppAuthenticationFilterTest {
  private static final String GUARDED_PATH = "/v1/glucose/report/";
  private static final String OPEN_PATH = "/v1/login/";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  /**
   * Stands in for the handler mapping: one guarded path. Which paths really
   * route to an annotated method is {@code EndpointRepositoryTest}'s job.
   */
  @TestConfiguration
  static class Endpoints {
    @Bean
    EndpointRepository endpointRepository() {
      var repository = Mockito.mock(EndpointRepository.class);
      Mockito.when(repository.routesToAnnotated(Mockito.any(), Mockito.any()))
        .thenAnswer(invocation -> GUARDED_PATH.equals(
          invocation.<HttpServletRequest>getArgument(0).getRequestURI()));
      return repository;
    }

    /** The stand-in still inherits the field the real repository injects. */
    @Bean
    RequestMappingHandlerMapping requestMappingHandlerMapping() {
      return Mockito.mock(RequestMappingHandlerMapping.class);
    }

    @Bean
    UserSessionRepository userSessionRepository() {
      return Mockito.mock(UserSessionRepository.class);
    }
  }

  @Autowired
  private AppAuthenticationFilter filter;
  @Autowired
  private UserSessionRepository sessionRepository;

  private MockHttpServletResponse response;
  private MockFilterChain chain;
  private UserSession session;

  @BeforeEach
  void createExchange() {
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    session = UserSession.create(SESSION_ID, USER_ID, UserSessionStatus.ACTIVE,
      "", "", "", "", 0L, "token", 0L, null);
    Mockito.when(sessionRepository.findById(SESSION_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(session)));
  }

  private void filter(String path, String authorization) throws Exception {
    var request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    filter.doFilter(request, response, chain);
  }

  private boolean reachedTheController() {
    return chain.getRequest() != null;
  }

  @Test
  void aValidTokenReachesTheEndpoint() throws Exception {
    filter(GUARDED_PATH, TestAuthentication.bearer(USER_ID, SESSION_ID));
    Assertions.assertTrue(reachedTheController());
  }

  @Test
  void aMissingAuthorizationHeaderIsRefusedWithoutReachingTheEndpoint()
    throws Exception {
    filter(GUARDED_PATH, null);
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    Assertions.assertFalse(reachedTheController());
  }

  /**
   * 417 is the app's signal to refresh and retry — a plain 403 here would log
   * the user out on every expired token instead.
   */
  @Test
  void anExpiredTokenAsksTheAppToRefreshRatherThanLoggingItOut()
    throws Exception {
    filter(GUARDED_PATH, TestAuthentication.expiredBearer(USER_ID));
    Assertions.assertEquals(HttpServletResponse.SC_EXPECTATION_FAILED,
      response.getStatus());
    Assertions.assertFalse(reachedTheController());
  }

  @Test
  void aTokenSignedWithAnotherKeyEndsTheSession() throws Exception {
    filter(GUARDED_PATH, TestAuthentication.bearerWithWrongSignature(USER_ID));
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    Assertions.assertFalse(reachedTheController());
  }

  @Test
  void garbageInTheHeaderEndsTheSession() throws Exception {
    filter(GUARDED_PATH, "Bearer not-a-token");
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    Assertions.assertFalse(reachedTheController());
  }

  /** Logged out, or closed by a password change: the token dies with it. */
  @Test
  void aTokenOfAClosedSessionEndsTheSession() throws Exception {
    session.close();
    filter(GUARDED_PATH, TestAuthentication.bearer(USER_ID, SESSION_ID));
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    Assertions.assertFalse(reachedTheController());
  }

  @Test
  void aTokenNamingAnUnknownSessionEndsTheSession() throws Exception {
    Mockito.when(sessionRepository.findById(SESSION_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    filter(GUARDED_PATH, TestAuthentication.bearer(USER_ID, SESSION_ID));
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void aTokenNamingAnotherUsersSessionEndsTheSession() throws Exception {
    filter(GUARDED_PATH, TestAuthentication.bearer(UUID.randomUUID(), SESSION_ID));
    Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void anUnannotatedPathIsNotGuardedSoLoginStaysReachable() throws Exception {
    filter(OPEN_PATH, null);
    Assertions.assertTrue(reachedTheController());
  }

  /**
   * The header is sent as "Bearer <token>", but the raw token has to work too —
   * the filter strips the prefix rather than requiring it.
   */
  @Test
  void aTokenWithoutTheBearerPrefixIsStillAccepted() throws Exception {
    filter(GUARDED_PATH,
      TestAuthentication.bearer(USER_ID, SESSION_ID).replace("Bearer ", ""));
    Assertions.assertTrue(reachedTheController());
  }
}
