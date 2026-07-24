package de.insulink.api.web.security.app;

import de.insulink.api.web.TestAuthentication;
import de.insulink.api.web.security.Endpoint;
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

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.UUID;

/**
 * The gate in front of every {@code @AppEndpoint}. Its status codes are the
 * contract the app's http layer switches on and are deliberately not the usual
 * ones: 417 means "refresh the token and retry", 403 means "this session is
 * over, clear it". Unannotated paths — signup, login, refresh — are not guarded
 * here at all, or nobody could ever obtain a token.
 */
@SpringJUnitConfig(classes = {AppAuthenticationFilter.class,
  AppAuthenticationFilterTest.Endpoints.class, TestAuthentication.class})
final class AppAuthenticationFilterTest {
  private static final String GUARDED_PATH = "/v1/glucose/report/";
  private static final String OPEN_PATH = "/v1/login/";
  private static final UUID USER_ID = UUID.randomUUID();

  /**
   * Stands in for the reflection over the handler mappings: one guarded path,
   * which is all the filter reads at startup. The handler mapping itself is
   * still needed as a bean, because the stand-in inherits the field the real
   * repository has it injected into.
   */
  @TestConfiguration
  static class Endpoints {
    @Bean
    EndpointRepository endpointRepository() {
      var repository = Mockito.mock(EndpointRepository.class);
      Mockito.when(repository.findAnnotatedEndpoints(AppEndpoint.class))
        .thenReturn(List.of(Endpoint.create(GUARDED_PATH, List.of())));
      return repository;
    }

    @Bean
    RequestMappingHandlerMapping requestMappingHandlerMapping() {
      return Mockito.mock(RequestMappingHandlerMapping.class);
    }
  }

  @Autowired
  private AppAuthenticationFilter filter;

  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void createExchange() {
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
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
    filter(GUARDED_PATH, TestAuthentication.bearer(USER_ID));
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
      TestAuthentication.bearer(USER_ID).replace("Bearer ", ""));
    Assertions.assertTrue(reachedTheController());
  }
}
