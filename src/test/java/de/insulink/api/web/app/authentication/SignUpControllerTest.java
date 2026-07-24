package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.device.UserDevice;
import de.insulink.api.user.device.UserDeviceRepository;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.settings.UserSettings;
import de.insulink.api.user.settings.UserSettingsRepository;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating an account. One request sets up the user, their device and their
 * settings and signs them straight in. The password is only ever stored hashed,
 * the displayed name goes through the sanitizer, and the two things that make an
 * account invalid — no accepted legal notice, no usable name — are refused with
 * nothing written at all.
 */
@AppControllerTest(SignUpController.class)
final class SignUpControllerTest {
  private static final String PASSWORD = "correct horse battery staple";
  private static final String HASH = "$argon2i$hashed";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserDeviceRepository deviceRepository;
  @MockitoBean
  private UserSessionRepository sessionRepository;
  @MockitoBean
  private UserSettingsRepository settingsRepository;
  @MockitoBean
  private DatabaseReader geoDatabaseReader;
  @MockitoBean
  private Hashing hashing;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyDatabase() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(hashing.hash(PASSWORD)).thenReturn(HASH);
    Mockito.when(userRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(deviceRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(sessionRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(userRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(deviceRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(settingsRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(sessionRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private ResultActions signUp(String name, boolean legalAccepted)
    throws Exception {
    var payload = """
      {"name": "%s", "password": "%s", "language": "de",
       "legal_accepted": %s, "device_id": "device-1",
       "operating_system": "Android", "operating_system_version": "15",
       "device_brand": "Google", "device_model": "Pixel 9",
       "device_name": "Lukas' Pixel", "settings": "{}"}"""
      .formatted(name, PASSWORD, legalAccepted);
    return endpoint.call(post("/signup/")
      .header("X-Real-IP", "1.2.3.4")
      .header("User-Agent", "Insulink/1.0 (Android 15)")
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void aNewAccountIsSignedInRightAway() throws Exception {
    signUp("Lukas", true)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.name").value("Lukas"))
      .andExpect(jsonPath("$.authentication_token").isNotEmpty())
      .andExpect(jsonPath("$.refresh_token").isNotEmpty());
  }

  @Test
  void thePasswordIsStoredOnlyAsItsHash() throws Exception {
    signUp("Lukas", true);
    var saved = ArgumentCaptor.forClass(User.class);
    Mockito.verify(userRepository).save(saved.capture());
    Assertions.assertEquals(HASH, saved.getValue().password());
    Assertions.assertNotEquals(PASSWORD, saved.getValue().password());
    Mockito.verify(hashing).hash(PASSWORD);
  }

  @Test
  void theDeviceAndTheSettingsAreCreatedForTheSameNewUser() throws Exception {
    signUp("Lukas", true);
    var user = ArgumentCaptor.forClass(User.class);
    var device = ArgumentCaptor.forClass(UserDevice.class);
    var settings = ArgumentCaptor.forClass(UserSettings.class);
    Mockito.verify(userRepository).save(user.capture());
    Mockito.verify(deviceRepository).save(device.capture());
    Mockito.verify(settingsRepository).save(settings.capture());
    Assertions.assertEquals(user.getValue().id(), device.getValue().userId());
    Assertions.assertEquals(user.getValue().id(), settings.getValue().userId());
    Assertions.assertEquals("device-1", device.getValue().deviceId());
    Assertions.assertEquals("Pixel 9", device.getValue().deviceModel());
    Assertions.assertEquals("{}", settings.getValue().content());
  }

  @Test
  void theAcceptedLegalNoticeIsRecordedOnTheUser() throws Exception {
    signUp("Lukas", true);
    var saved = ArgumentCaptor.forClass(User.class);
    Mockito.verify(userRepository).save(saved.capture());
    Assertions.assertTrue(saved.getValue().compliant());
    Assertions.assertEquals("de", saved.getValue().language());
  }

  @Test
  void anAccountIsNotCreatedWithoutTheAcceptedLegalNotice() throws Exception {
    signUp("Lukas", false)
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(deviceRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(settingsRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aBlankNameIsRefused() throws Exception {
    signUp("   ", true).andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * The name is rendered in the app and the panel, so markup has to be stripped
   * before it is stored — and a name that is nothing but markup is left blank
   * and therefore refused.
   */
  @Test
  void aNameThatIsOnlyMarkupIsStrippedToNothingAndRefused() throws Exception {
    signUp("<script>alert(1)</script>", true)
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
  }
}
