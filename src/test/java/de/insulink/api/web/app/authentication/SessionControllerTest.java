package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session lifetime as the app depends on it: a refresh only succeeds for a
 * token that is signed with the refresh key, belongs to a user and session that
 * still exist, is the session's current token, and whose session is still open.
 * Each of those failing is its own error code, because the app reacts to them
 * differently. A successful refresh rotates the stored token; the one it
 * replaced is still redeemed for the current one until the next rotation.
 */
@AppControllerTest(SessionController.class)
final class SessionControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserSessionRepository sessionRepository;
  @MockitoBean
  private DatabaseReader geoDatabaseReader;

  private AsyncEndpoint endpoint;
  private UserSession session;
  private String currentRefreshToken;

  @BeforeEach
  void stubOpenSession() {
    endpoint = AsyncEndpoint.on(mockMvc);
    currentRefreshToken = TestAuthentication.refreshToken(USER_ID, SESSION_ID);
    session = UserSession.create(SESSION_ID, USER_ID, UserSessionStatus.ACTIVE,
      "android", "127.0.0.1", "Germany", "Aachen", 0L, currentRefreshToken, 0L, null);
    var user = User.create(USER_ID, "Lukas", "hash", "de", true, 0L);
    Mockito.when(userRepository.existsById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(true));
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(sessionRepository.existsById(SESSION_ID))
      .thenReturn(CompletableFuture.completedFuture(true));
    Mockito.when(sessionRepository.findById(SESSION_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(session)));
    Mockito.when(sessionRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private org.springframework.test.web.servlet.ResultActions refresh(String token)
    throws Exception {
    return endpoint.call(post("/refresh/")
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"refresh_token\": \"" + token + "\"}"));
  }

  @Test
  void aValidRefreshHandsBackBothFreshTokens() throws Exception {
    refresh(currentRefreshToken)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.authentication_token").isNotEmpty())
      .andExpect(jsonPath("$.refresh_token").isNotEmpty());
  }

  /**
   * The stored token has to move on with the refresh, otherwise a leaked token
   * stays usable forever.
   */
  @Test
  void aRefreshRotatesTheTokenStoredOnTheSession() throws Exception {
    refresh(currentRefreshToken);
    Assertions.assertNotEquals(currentRefreshToken, session.lastRefreshToken());
    Mockito.verify(sessionRepository).save(session);
  }

  /**
   * A refresh whose response was lost has already rotated; the app can only
   * retry with the token it still holds, and that must not log it out.
   */
  @Test
  void theTokenThatWasJustReplacedGetsTheCurrentOneAgain() throws Exception {
    refresh(currentRefreshToken);
    var rotated = session.lastRefreshToken();
    refresh(currentRefreshToken)
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.refresh_token").value(rotated));
    Assertions.assertEquals(rotated, session.lastRefreshToken());
  }

  @Test
  void aTokenTwoRotationsOldIsNoLongerAccepted() throws Exception {
    refresh(currentRefreshToken);
    session.updateRefreshToken("third");
    refresh(currentRefreshToken).andExpect(jsonPath("$.error.code").value(1002));
  }

  @Test
  void aTokenSignedWithTheWrongKeyIsRejected() throws Exception {
    refresh(TestAuthentication.refreshTokenWithWrongKey(USER_ID, SESSION_ID))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));
  }

  @Test
  void garbageInsteadOfATokenIsRejectedRatherThanFailingTheRequest()
    throws Exception {
    refresh("not-a-token").andExpect(jsonPath("$.error.code").value(1000));
  }

  @Test
  void aTokenForAUserThatNoLongerExistsIsRejected() throws Exception {
    Mockito.when(userRepository.existsById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(false));
    refresh(currentRefreshToken).andExpect(jsonPath("$.error.code").value(1001));
  }

  @Test
  void aTokenForASessionThatNoLongerExistsIsRejected() throws Exception {
    Mockito.when(sessionRepository.existsById(SESSION_ID))
      .thenReturn(CompletableFuture.completedFuture(false));
    refresh(currentRefreshToken).andExpect(jsonPath("$.error.code").value(1001));
  }

  @Test
  void aClosedSessionCannotBeRefreshedBackToLife() throws Exception {
    session.close();
    refresh(currentRefreshToken).andExpect(jsonPath("$.error.code").value(1002));
  }

  @Test
  void loggingOutClosesTheSessionTheTokenNames() throws Exception {
    endpoint.call(get("/logout/").header("Authorization",
        TestAuthentication.bearer(USER_ID, SESSION_ID)))
      .andExpect(status().isOk());
    Assertions.assertTrue(session.status().isClosed());
    Mockito.verify(sessionRepository).save(session);
  }

  @Test
  void authorizedIsTrueForAKnownUser() throws Exception {
    endpoint.call(get("/authorized/").header("Authorization",
        TestAuthentication.bearer(USER_ID, SESSION_ID)))
      .andExpect(jsonPath("$.authorized").value(true));
  }

  @Test
  void authorizedIsFalseWhenTheTokenDoesNotResolveToAUser() throws Exception {
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    endpoint.call(get("/authorized/").header("Authorization",
        TestAuthentication.bearer(USER_ID, SESSION_ID)))
      .andExpect(jsonPath("$.authorized").value(false));
  }

  /**
   * The filter is what rejects a bad token in production; the endpoint itself
   * still has to answer instead of blowing up when it sees one.
   */
  @Test
  void aWronglySignedBearerLeavesAuthorizedFalse() throws Exception {
    endpoint.call(get("/authorized/").header("Authorization",
        TestAuthentication.bearerWithWrongSignature(USER_ID)))
      .andExpect(jsonPath("$.authorized").value(false));
  }
}
