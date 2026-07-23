package de.insulink.api.web.security.app;

import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AppRestController {
  private final Key authenticationKey;
  private final UserRepository userRepository;

  /**
   * Is used to authenticate a user request
   * @param request The request
   * @return The user
   */
  protected CompletableFuture<User> findUser(
    HttpServletRequest request
  ) {
    try {
      var apiKey = findApiKey(request);
      var userId = findUserId(apiKey);
      return userRepository.findById(userId).thenApply(user -> user.orElse(null));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Is used to find the id of the user that send the request
   * @param request The request
   * @return The id of the user
   */
  protected UUID findUserId(HttpServletRequest request) {
    return findUserId(findApiKey(request));
  }

  /**
   * Is used to find the id of a user inside an api key
   * @param apiKey The api key
   * @return The id of the user
   */
  protected UUID findUserId(String apiKey) {
    return UUID.fromString(Jwts.parser().setSigningKey(authenticationKey).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  /**
   * Is used to find the id of the session that send the request
   * @param request The request
   * @return The id of the session
   */
  protected UUID findSessionId(HttpServletRequest request) {
    return findSessionId(findApiKey(request));
  }

  /**
   * Is used to find the id of a session inside an api key
   * @param apiKey The api key
   * @return The id of the session
   */
  protected UUID findSessionId(String apiKey) {
    return UUID.fromString(Jwts.parser().setSigningKey(authenticationKey).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  /**
   * Is used to find the api key that is sent via a request
   * @param request The request
   * @return The api key
   */
  protected String findApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }
}
