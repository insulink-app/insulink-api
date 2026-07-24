package de.insulink.api.web.app.user;

import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The language on the user decides which locale the api answers a notification
 * in, so changing it has to hit the user the token belongs to and nobody else.
 */
@AppControllerTest(UserLanguageController.class)
final class UserLanguageControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;

  private AsyncEndpoint endpoint;
  private User user;

  @BeforeEach
  void stubGermanUser() {
    endpoint = AsyncEndpoint.on(mockMvc);
    user = User.create(USER_ID, "Lukas", "hash", "de", true, 0L);
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(userRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private void changeLanguage(String language) throws Exception {
    endpoint.call(post("/user/language/change/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"language\": \"" + language + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void theNewLanguageIsStoredOnTheUserFromTheToken() throws Exception {
    changeLanguage("en");
    var saved = ArgumentCaptor.forClass(User.class);
    Mockito.verify(userRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().id());
    Assertions.assertEquals("en", saved.getValue().language());
  }

  @Test
  void theRestOfTheUserIsLeftAloneByALanguageChange() throws Exception {
    changeLanguage("en");
    Assertions.assertEquals("Lukas", user.name());
    Assertions.assertEquals("hash", user.password());
    Assertions.assertTrue(user.compliant());
  }
}
