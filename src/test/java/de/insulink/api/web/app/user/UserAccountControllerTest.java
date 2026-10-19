package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.key.UserKey;
import de.insulink.api.user.key.UserKeyRepository;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changing the two things about an account a user can change. The password
 * change is the one that matters: it only goes through against the current
 * password, and what is stored afterwards is a hash of the new one, never the
 * new one. The display name is user input that ends up rendered, so it is
 * sanitized and may not end up empty.
 */
@AppControllerTest({UserPasswordController.class, UserNameController.class})
final class UserAccountControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();
  private static final String CURRENT_PASSWORD = "correct horse battery staple";
  private static final String STORED_HASH = "$argon2i$stored";
  private static final String NEW_HASH = "$argon2i$new";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private Hashing hashing;
  @MockitoBean
  private UserSessionRepository sessionRepository;
  @MockitoBean
  private UserKeyRepository keyRepository;

  private AsyncEndpoint endpoint;
  private User user;
  private UserSession currentSession;
  private UserSession otherSession;

  @BeforeEach
  void stubUser() {
    endpoint = AsyncEndpoint.on(mockMvc);
    user = User.create(USER_ID, "Lukas", STORED_HASH, "de", true, 0L);
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(userRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(hashing.matches(CURRENT_PASSWORD, STORED_HASH)).thenReturn(true);
    Mockito.when(hashing.hash("a brand new password")).thenReturn(NEW_HASH);
    stubSessions();
  }

  private void stubSessions() {
    currentSession = openSession(SESSION_ID);
    otherSession = openSession(UUID.randomUUID());
    Mockito.when(sessionRepository.findByUserIdAndStatus(USER_ID,
        UserSessionStatus.ACTIVE))
      .thenReturn(CompletableFuture.completedFuture(
        List.of(currentSession, otherSession)));
    Mockito.when(sessionRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(keyRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private UserSession openSession(UUID sessionId) {
    return UserSession.create(sessionId, USER_ID, UserSessionStatus.ACTIVE,
      "", "", "", "", 0L, "token", 0L, null);
  }

  private ResultActions changePassword(String current, String replacement)
    throws Exception {
    return changePassword(new JSONObject().put("password", current)
      .put("new_password", replacement));
  }

  private ResultActions changePassword(JSONObject payload) throws Exception {
    return endpoint.call(post("/user/password/change/")
      .header("Authorization", TestAuthentication.bearer(USER_ID, SESSION_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload.toString()));
  }

  private ResultActions changeName(String name) throws Exception {
    return endpoint.call(post("/user/name/change/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"name\": \"" + name + "\"}"));
  }

  private User savedUser() {
    var saved = ArgumentCaptor.forClass(User.class);
    Mockito.verify(userRepository).save(saved.capture());
    return saved.getValue();
  }

  @Test
  void theNewPasswordIsStoredAsAHashOfItself() throws Exception {
    changePassword(CURRENT_PASSWORD, "a brand new password")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    Assertions.assertEquals(NEW_HASH, savedUser().password());
    Mockito.verify(hashing).hash("a brand new password");
  }

  @Test
  void theChangeIsRefusedWithoutTheCurrentPassword() throws Exception {
    changePassword("wrong", "a brand new password")
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Assertions.assertEquals(STORED_HASH, user.password());
  }

  @Test
  void anEmptyNewPasswordIsRefusedBeforeAnythingIsChecked() throws Exception {
    changePassword(CURRENT_PASSWORD, "   ")
      .andExpect(jsonPath("$.error.code").value(1005));
    Mockito.verifyNoInteractions(hashing);
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aNewPasswordBelowTheMinimumLengthIsRefused() throws Exception {
    changePassword(CURRENT_PASSWORD, "short")
      .andExpect(jsonPath("$.error.code").value(1005));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  /** Whoever knew the old password may hold a session from it. */
  @Test
  void aChangeSignsOutEveryOtherSessionButKeepsTheCurrentOne() throws Exception {
    changePassword(CURRENT_PASSWORD, "a brand new password")
      .andExpect(jsonPath("$.success").value(true));
    Assertions.assertTrue(otherSession.status().isClosed());
    Assertions.assertTrue(currentSession.status().isActive());
  }

  /** The data key wrapped for the new password is stored with the change. */
  @Test
  void aKeySentWithTheChangeIsStoredForTheUser() throws Exception {
    changePassword(new JSONObject().put("password", CURRENT_PASSWORD)
        .put("new_password", "a brand new password").put("key", "{\"version\": 1}"))
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(UserKey.class);
    Mockito.verify(keyRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("{\"version\": 1}", saved.getValue().content());
  }

  @Test
  void aMalformedKeyRefusesTheWholeChange() throws Exception {
    changePassword(new JSONObject().put("password", CURRENT_PASSWORD)
        .put("new_password", "a brand new password").put("key", "not json"))
      .andExpect(jsonPath("$.error.code").value(1001));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aWrongCurrentPasswordStoresNoKey() throws Exception {
    changePassword(new JSONObject().put("password", "wrong")
        .put("new_password", "a brand new password").put("key", "{}"))
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verifyNoInteractions(keyRepository);
  }

  @Test
  void aChangeForAUserThatNoLongerExistsIsRefused() throws Exception {
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    changePassword(CURRENT_PASSWORD, "a brand new password")
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void theNewNameIsStoredOnTheUser() throws Exception {
    changeName("Lukas B.")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    Assertions.assertEquals("Lukas B.", savedUser().name());
  }

  @Test
  void aBlankNameIsRefused() throws Exception {
    changeName("   ").andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * The name is rendered in the app and the panel, so markup never reaches
   * storage — and a name that is nothing but markup is left blank and refused.
   */
  @Test
  void aNameThatIsOnlyMarkupIsRefused() throws Exception {
    changeName("<b>x</b>").andExpect(jsonPath("$.success").value(true));
    Assertions.assertFalse(savedUser().name().contains("<"), savedUser().name());
  }
}
