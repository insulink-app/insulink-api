package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.key.UserKey;
import de.insulink.api.user.key.UserKeyRepository;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserPasswordController extends AppRestController {
  private final Hashing hashing;
  private final UserSessionRepository sessionRepository;
  private final UserKeyRepository keyRepository;

  private UserPasswordController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, Hashing hashing,
    UserSessionRepository sessionRepository, UserKeyRepository keyRepository
  ) {
    super(authenticationKey, userRepository);
    this.hashing = hashing;
    this.sessionRepository = sessionRepository;
    this.keyRepository = keyRepository;
  }

  /**
   * 1000 for a wrong current password, 1001 for a malformed {@code key}, 1005
   * for a new password below the minimum length. The optional {@code key} is
   * the data key wrapped for the new password; it is stored with the change,
   * so the key and the password never disagree.
   */
  @AppEndpoint
  @RequestMapping(path = "/user/password/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeUserPassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var newPassword = body.getString("new_password");
    if (newPassword.strip().length() < User.MINIMUM_PASSWORD_LENGTH) {
      return ApiResponse.error(1005).future();
    }
    var key = body.has("key") ?
      UserKey.of(findUserId(request), body.getString("key")).orElse(null) : null;
    if (body.has("key") && key == null) {
      return ApiResponse.error(1001).future();
    }
    var change = new PasswordChange(body.getString("password"), newPassword,
      key, findSessionId(request));
    return findUser(request).thenCompose(user -> changeUserPassword(user, change));
  }

  private CompletableFuture<ApiResponse> changeUserPassword(
    User user, PasswordChange change
  ) {
    if (user == null || !hashing.matches(change.currentPassword(), user.password())) {
      return ApiResponse.error(1000).future();
    }
    user.changePassword(hashing.hash(change.newPassword()));
    return userRepository().save(user)
      .thenCompose(_ -> storeKey(change.key()))
      .thenCompose(_ -> closeOtherSessions(user.id(), change.keptSessionId()))
      .thenApply(_ -> ApiResponse.success());
  }

  private CompletableFuture<?> storeKey(UserKey key) {
    return key == null ? CompletableFuture.completedFuture(null) :
      keyRepository.save(key);
  }

  /**
   * Signs every other device out: whoever knew the old password may hold a
   * session from it. The session making the change stays open.
   */
  private CompletableFuture<?> closeOtherSessions(UUID userId, UUID keptSessionId) {
    return sessionRepository.findByUserIdAndStatus(userId, UserSessionStatus.ACTIVE)
      .thenCompose(sessions -> AsyncIterator.execute(
        othersClosed(sessions, keptSessionId), sessionRepository::save));
  }

  private List<UserSession> othersClosed(
    List<UserSession> sessions, UUID keptSessionId
  ) {
    var others = sessions.stream()
      .filter(session -> !session.id().equals(keptSessionId)).toList();
    others.forEach(UserSession::close);
    return others;
  }

  private record PasswordChange(
    String currentPassword, String newPassword, UserKey key, UUID keptSessionId
  ) {
  }
}
