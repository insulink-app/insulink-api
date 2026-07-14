package de.insulink.api.web.response;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Pins the envelope every endpoint answers in: the app branches on the
 * "success" flag and on the error code below it, so their shape and the status
 * code they ride on are part of the contract, not an implementation detail.
 */
final class ApiResponseTest {
  @Test
  void successIsFlaggedAndCarriesOk() {
    var response = ApiResponse.success();
    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    Assertions.assertEquals(true, response.getBody().get("success"));
  }

  @Test
  void successMergesThePayloadNextToTheFlag() {
    var response = ApiResponse.success(Map.of("user_id", "abc"));
    Assertions.assertEquals(true, response.getBody().get("success"));
    Assertions.assertEquals("abc", response.getBody().get("user_id"));
  }

  @Test
  void errorReportsItsCodeUnderABadRequest() {
    var response = ApiResponse.error(1000);
    Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    Assertions.assertEquals(false, response.getBody().get("success"));
    Assertions.assertEquals(1000, error(response).get("code"));
  }

  @Test
  void errorWithoutMessageOmitsTheMessageKeyEntirely() {
    Assertions.assertFalse(error(ApiResponse.error(1000)).containsKey("message"));
  }

  @Test
  void errorMessageIsPassedThroughWhenGiven() {
    Assertions.assertEquals("too late", error(ApiResponse.error(1001, "too late")).get("message"));
  }

  @Test
  void expandAddsToTheExistingBodyAndStaysChainable() {
    var response = ApiResponse.success().expand(Map.of("session_id", "xyz"));
    Assertions.assertEquals(true, response.getBody().get("success"));
    Assertions.assertEquals("xyz", response.getBody().get("session_id"));
  }

  @Test
  void futureCompletesWithTheSameResponse() {
    var response = ApiResponse.success();
    Assertions.assertSame(response, response.future().join());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> error(ApiResponse response) {
    return (Map<String, Object>) response.getBody().get("error");
  }
}
