package de.insulink.api.web.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The CORS edge. A browser only reaches the api at all when the panel's origin
 * is echoed back, and only origins from {@code config.ini} may be — anything
 * else gets no header and therefore no data. The preflight is answered here and
 * never reaches a controller.
 */
@SpringJUnitConfig(classes = {EquipmentFilter.class, TestWebConfiguration.class})
final class EquipmentFilterTest {
  @Autowired
  private EquipmentFilter filter;

  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void createExchange() {
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  private MockHttpServletRequest requestFrom(String origin, String method) {
    var request = new MockHttpServletRequest(method, "/v1/glucose/report/");
    if (origin != null) {
      request.addHeader("Origin", origin);
    }
    return request;
  }

  private void filter(String origin, String method) throws Exception {
    filter.doFilter(requestFrom(origin, method), response, chain);
  }

  @Test
  void anAllowedOriginIsEchoedBackSoTheBrowserKeepsTheResponse() throws Exception {
    filter(TestWebConfiguration.ALLOWED_ORIGIN, "GET");
    Assertions.assertEquals(TestWebConfiguration.ALLOWED_ORIGIN,
      response.getHeader("Access-Control-Allow-Origin"));
  }

  @Test
  void everyConfiguredOriginIsAllowedNotJustTheFirst() throws Exception {
    filter(TestWebConfiguration.SECOND_ALLOWED_ORIGIN, "GET");
    Assertions.assertEquals(TestWebConfiguration.SECOND_ALLOWED_ORIGIN,
      response.getHeader("Access-Control-Allow-Origin"));
  }

  @Test
  void anUnknownOriginIsNotEchoedBack() throws Exception {
    filter("https://evil.example", "GET");
    Assertions.assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }

  /**
   * The app itself sends no Origin at all; it must still be served.
   */
  @Test
  void aRequestWithoutAnOriginStillReachesTheController() throws Exception {
    filter(null, "GET");
    Assertions.assertNull(response.getHeader("Access-Control-Allow-Origin"));
    Assertions.assertNotNull(chain.getRequest());
  }

  @Test
  void thePreflightIsAnsweredHereAndGoesNoFurther() throws Exception {
    filter(TestWebConfiguration.ALLOWED_ORIGIN, RequestMethod.OPTIONS.name());
    Assertions.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    Assertions.assertNull(chain.getRequest());
  }

  @Test
  void aNormalRequestIsPassedOnToTheController() throws Exception {
    filter(TestWebConfiguration.ALLOWED_ORIGIN, "POST");
    Assertions.assertNotNull(chain.getRequest());
  }

  @Test
  void theHeadersTheBrowserNeedsForTheAuthorizedCallAreAlwaysSet()
    throws Exception {
    filter(TestWebConfiguration.ALLOWED_ORIGIN, "GET");
    Assertions.assertTrue(response.getHeader("Access-Control-Allow-Methods")
      .contains("POST"));
    Assertions.assertTrue(response.getHeader("Access-Control-Allow-Headers")
      .contains("authorization"));
    Assertions.assertEquals("3600", response.getHeader("Access-Control-Max-Age"));
    Assertions.assertEquals("application/json", response.getHeader("Content-Type"));
  }
}
