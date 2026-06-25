package de.insulink.api.web.app.user;

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
public final class UserNameController extends AppRestController {
  private UserNameController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository
  ) {
    super(authenticationKey, userRepository);
  }

  @AppEndpoint
  @RequestMapping(path = "/user/name/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeUserName(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var name = body.getSanitizedString("name");
    if (name.isBlank()) {
      return ApiResponse.error(1000).future();
    }
    return findUser(request)
      .thenCompose(user -> changeUserName(user, name));
  }

  private CompletableFuture<ApiResponse> changeUserName(User user, String name) {
    user.changeName(name);
    return userRepository().save(user).thenApply(_ -> ApiResponse.success());
  }
}