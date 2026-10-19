package de.insulink.api.web.app.user;

import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.AccountEraser;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Deleting an account needs the password on top of the session, so a stolen
 * session cannot wipe someone's history.
 */
@AppControllerTest(UserDeletionController.class)
final class UserDeletionControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String PASSWORD = "correct horse battery staple";
  private static final String STORED_HASH = "$argon2i$stored";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private Hashing hashing;
  @MockitoBean
  private AccountEraser eraser;

  @BeforeEach
  void stubUser() {
    var user = User.create(USER_ID, "Lukas", STORED_HASH, "de", true, 0L);
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(hashing.matches(PASSWORD, STORED_HASH)).thenReturn(true);
    Mockito.when(eraser.erase(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private ResultActions delete(String password) throws Exception {
    return AsyncEndpoint.on(mockMvc).call(post("/user/delete/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"password\": \"" + password + "\"}"));
  }

  @Test
  void theRightPasswordErasesTheAccount() throws Exception {
    delete(PASSWORD).andExpect(jsonPath("$.success").value(true));
    Mockito.verify(eraser).erase(USER_ID);
  }

  @Test
  void aWrongPasswordErasesNothing() throws Exception {
    delete("wrong").andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verifyNoInteractions(eraser);
  }
}
