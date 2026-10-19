package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.key.UserKey;
import de.insulink.api.user.key.UserKeyRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the user's wrapped data key (see {@link UserKey}). Reading it needs a
 * session; storing one also needs the password, so a stolen session cannot
 * swap in a key of its own choosing.
 */
@RestController
public final class UserKeyController extends AppRestController {
  private final UserKeyRepository keyRepository;
  private final Hashing hashing;

  private UserKeyController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, UserKeyRepository keyRepository,
    Hashing hashing
  ) {
    super(authenticationKey, userRepository);
    this.keyRepository = keyRepository;
    this.hashing = hashing;
  }

  /** The wrapped key, or 1000 when the account has none yet. */
  @AppEndpoint
  @RequestMapping(path = "/user/key/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findKey(HttpServletRequest request) {
    return keyRepository.findById(findUserId(request))
      .thenApply(key -> key.map(found -> ApiResponse.success(
          Map.of("key", found.content())))
        .orElseGet(() -> ApiResponse.error(1000)));
  }

  /** 1000 for a wrong password, 1001 for a key that is not a sane JSON object. */
  @AppEndpoint
  @RequestMapping(path = "/user/key/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeKey(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var key = UserKey.of(findUserId(request), body.getString("key"));
    if (key.isEmpty()) {
      return ApiResponse.error(1001).future();
    }
    var password = body.getString("password");
    return findUser(request)
      .thenCompose(user -> storeKey(user, password, key.get()));
  }

  private CompletableFuture<ApiResponse> storeKey(
    User user, String password, UserKey key
  ) {
    if (user == null || !hashing.matches(password, user.password())) {
      return ApiResponse.error(1000).future();
    }
    return keyRepository.save(key).thenApply(_ -> ApiResponse.success());
  }
}
