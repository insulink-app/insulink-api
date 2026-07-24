package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing in. A wrong password and an unknown account answer identically, so
 * the endpoint cannot be used to find out which names exist. A successful sign
 * in opens a session row and hands out the token pair; the session records where
 * the request came from, but a device the geo database cannot place still signs
 * in rather than failing.
 */
@AppControllerTest(SignInController.class)
final class SignInControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String PASSWORD = "correct horse battery staple";
  private static final String STORED_HASH = "$argon2i$stored";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserSessionRepository sessionRepository;
  @MockitoBean
  private DatabaseReader geoDatabaseReader;
  @MockitoBean
  private Hashing hashing;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubKnownUser() {
    endpoint = AsyncEndpoint.on(mockMvc);
    var user = User.create(USER_ID, "Lukas", STORED_HASH, "de", true, 0L);
    Mockito.when(userRepository.findByName("Lukas"))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
    Mockito.when(userRepository.findByName("Unbekannt"))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Mockito.when(hashing.matches(PASSWORD, STORED_HASH)).thenReturn(true);
    Mockito.when(sessionRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(sessionRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private ResultActions signIn(String name, String password) throws Exception {
    return endpoint.call(post("/signin/")
      .header("X-Real-IP", "1.2.3.4")
      .header("User-Agent", "Insulink/1.0 (Android 15)")
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"name\": \"" + name + "\", \"password\": \"" + password + "\"}"));
  }

  @Test
  void theRightCredentialsHandOutTheTokenPairAndTheAccount() throws Exception {
    signIn("Lukas", PASSWORD)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.user").value(USER_ID.toString()))
      .andExpect(jsonPath("$.name").value("Lukas"))
      .andExpect(jsonPath("$.authentication_token").isNotEmpty())
      .andExpect(jsonPath("$.refresh_token").isNotEmpty());
  }

  @Test
  void aSuccessfulSignInOpensASessionHoldingTheIssuedRefreshToken()
    throws Exception {
    signIn("Lukas", PASSWORD);
    var saved = ArgumentCaptor.forClass(UserSession.class);
    Mockito.verify(sessionRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertTrue(saved.getValue().status().isActive());
    Assertions.assertNotNull(saved.getValue().lastRefreshToken());
    Assertions.assertEquals("1.2.3.4", saved.getValue().ipAddress());
  }

  @Test
  void theWrongPasswordIsRefused() throws Exception {
    signIn("Lukas", "wrong")
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(sessionRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * Same answer as the wrong password on purpose — otherwise the endpoint
   * enumerates account names.
   */
  @Test
  void anUnknownAccountIsRefusedTheSameWayAsAWrongPassword() throws Exception {
    signIn("Unbekannt", PASSWORD)
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));
  }

  /**
   * The location is decoration on the session row; it must never be the reason
   * a sign in fails.
   */
  @Test
  void aSignInStillSucceedsWhenTheLocationCannotBeResolved() throws Exception {
    Mockito.when(geoDatabaseReader.city(Mockito.any()))
      .thenThrow(new IllegalStateException("no geo database"));
    signIn("Lukas", PASSWORD).andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(UserSession.class);
    Mockito.verify(sessionRepository).save(saved.capture());
    Assertions.assertEquals("", saved.getValue().country());
    Assertions.assertEquals("", saved.getValue().city());
  }
}
