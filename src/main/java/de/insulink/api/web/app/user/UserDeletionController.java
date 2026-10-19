package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.AccountEraser;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
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
import java.util.concurrent.CompletableFuture;

/**
 * Deletes the caller's account with all of its data. Needs the password on top
 * of the session, so a stolen session cannot wipe an account.
 */
@RestController
public final class UserDeletionController extends AppRestController {
  private final Hashing hashing;
  private final AccountEraser eraser;

  private UserDeletionController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, Hashing hashing, AccountEraser eraser
  ) {
    super(authenticationKey, userRepository);
    this.hashing = hashing;
    this.eraser = eraser;
  }

  /** 1000 for a wrong password. */
  @AppEndpoint
  @RequestMapping(path = "/user/delete/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> deleteUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var password = ApiRequestBody.of(payload, response).getString("password");
    return findUser(request).thenCompose(user -> deleteUser(user, password));
  }

  private CompletableFuture<ApiResponse> deleteUser(User user, String password) {
    if (user == null || !hashing.matches(password, user.password())) {
      return ApiResponse.error(1000).future();
    }
    return eraser.erase(user.id()).thenApply(_ -> ApiResponse.success());
  }
}
