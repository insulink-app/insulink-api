package de.insulink.api.web.app.user;

import de.insulink.api.user.UserRepository;
import de.insulink.api.user.settings.UserSettings;
import de.insulink.api.user.settings.UserSettingsRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User settings are stored as one opaque JSON blob the app owns, so the api
 * only checks that what it is handed parses as JSON at all — anything else it
 * writes through untouched. A user who never saved settings reads back an empty
 * object rather than null, so the app can always parse the answer.
 */
@AppControllerTest(UserSettingsController.class)
final class UserSettingsControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserSettingsRepository settingsRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubMissingSettings() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(settingsRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Mockito.when(settingsRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private void change(String payload, boolean expectedSuccess) throws Exception {
    endpoint.call(post("/user/settings/change/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(jsonPath("$.success").value(expectedSuccess));
  }

  @Test
  void aUserWithoutStoredSettingsReadsBackAnEmptyJsonObject() throws Exception {
    endpoint.call(get("/user/settings/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.settings").value("{}"));
  }

  @Test
  void storedSettingsAreHandedBackAsTheyWereSaved() throws Exception {
    Mockito.when(settingsRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(UserSettings
        .create(USER_ID, "{\"unit\":\"mgdl\"}", 1_700_000_000_000L))));
    endpoint.call(get("/user/settings/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.settings").value("{\"unit\":\"mgdl\"}"));
  }

  @Test
  void changedSettingsAreStoredAgainstTheUserFromTheToken() throws Exception {
    change("{\"settings\": \"{\\\"unit\\\":\\\"mmol\\\"}\"}", true);
    var saved = ArgumentCaptor.forClass(UserSettings.class);
    Mockito.verify(settingsRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("{\"unit\":\"mmol\"}", saved.getValue().content());
    Assertions.assertTrue(saved.getValue().lastUpdatedAt() > 0L);
  }

  @Test
  void settingsThatAreNotJsonAreRejectedWithoutBeingStored() throws Exception {
    change("{\"settings\": \"not json at all\"}", false);
    Mockito.verify(settingsRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aRejectedChangeCarriesTheErrorCodeTheAppSwitchesOn() throws Exception {
    endpoint.call(post("/user/settings/change/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"settings\": \"[]\"}"))
      .andExpect(jsonPath("$.error.code").value(1000));
  }
}
