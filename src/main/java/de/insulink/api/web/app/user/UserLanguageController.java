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
public final class UserLanguageController extends AppRestController {
  private UserLanguageController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository
  ) {
    super(authenticationKey, userRepository);
  }

  @AppEndpoint
  @RequestMapping(path = "/user/language/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeUserLanguage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var language = body.getString("language");
    return findUser(request)
      .thenCompose(user -> changeUserLanguage(user, language));
  }

  private CompletableFuture<ApiResponse> changeUserLanguage(
    User user, String language
  ) {
    user.changeLanguage(language);
    return userRepository().save(user).thenApply(_ -> ApiResponse.success());
  }
}