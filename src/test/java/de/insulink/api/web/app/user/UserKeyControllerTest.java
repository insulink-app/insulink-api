package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.key.UserKey;
import de.insulink.api.user.key.UserKeyRepository;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * The wrapped data key a reinstalled app unwraps at sign-in. Reading it only
 * needs the session; storing one needs the password as well, so a stolen
 * session cannot put a key of its own in place of the user's.
 */
@AppControllerTest(UserKeyController.class)
final class UserKeyControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String PASSWORD = "correct horse battery staple";
  private static final String STORED_HASH = "$argon2i$stored";
  private static final String WRAPPED = "{\"version\":1,\"wrapped\":\"abc\"}";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserKeyRepository keyRepository;
  @MockitoBean
  private Hashing hashing;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubUser() {
    endpoint = AsyncEndpoint.on(mockMvc);
    var user = User.create(USER_ID, "Lukas", STORED_HASH, "de", true, 0L);
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(hashing.matches(PASSWORD, STORED_HASH)).thenReturn(true);
    Mockito.when(keyRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Mockito.when(keyRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private ResultActions change(String password, String key) throws Exception {
    return endpoint.call(post("/user/key/change/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(new JSONObject().put("password", password).put("key", key)
        .toString()));
  }

  @Test
  void anAccountWithoutAKeyAnswersWithItsOwnCode() throws Exception {
    endpoint.call(get("/user/key/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.error.code").value(1000));
  }

  @Test
  void theStoredKeyIsHandedBackAsItWasStored() throws Exception {
    Mockito.when(keyRepository.findById(USER_ID)).thenReturn(CompletableFuture
      .completedFuture(Optional.of(UserKey.create(USER_ID, WRAPPED, 0L))));
    endpoint.call(get("/user/key/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.key").value(WRAPPED));
  }

  @Test
  void theRightPasswordStoresTheKey() throws Exception {
    change(PASSWORD, WRAPPED).andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(UserKey.class);
    Mockito.verify(keyRepository).save(saved.capture());
    Assertions.assertEquals(WRAPPED, saved.getValue().content());
  }

  @Test
  void aSessionWithoutThePasswordCannotReplaceTheKey() throws Exception {
    change("wrong", WRAPPED).andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(keyRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void anythingButASmallJsonObjectIsRefused() throws Exception {
    change(PASSWORD, "not json").andExpect(jsonPath("$.error.code").value(1001));
    change(PASSWORD, "{\"x\":\"" + "a".repeat(UserKey.MAXIMUM_CONTENT_LENGTH) + "\"}")
      .andExpect(jsonPath("$.error.code").value(1001));
    Mockito.verify(keyRepository, Mockito.never()).save(Mockito.any());
  }
}
