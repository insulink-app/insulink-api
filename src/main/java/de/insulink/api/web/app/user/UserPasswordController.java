package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
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

@RestController
public final class UserPasswordController extends AppRestController {
  private final Hashing hashing;

  private UserPasswordController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, Hashing hashing
  ) {
    super(authenticationKey, userRepository);
    this.hashing = hashing;
  }

  @AppEndpoint
  @RequestMapping(path = "/user/password/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeUserPassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var currentPassword = body.getString("password");
    var newPassword = body.getString("new_password");
    if (newPassword.isBlank()) {
      return ApiResponse.error(1000).future();
    }
    return findUser(request)
      .thenCompose(user -> changeUserPassword(user, currentPassword, newPassword));
  }

  private CompletableFuture<ApiResponse> changeUserPassword(
    User user, String currentPassword, String newPassword
  ) {
    if (user == null || !hashing.matches(currentPassword, user.password())) {
      return ApiResponse.error(1000).future();
    }
    user.changePassword(hashing.hash(newPassword));
    return userRepository().save(user).thenApply(_ -> ApiResponse.success());
  }
}
