package de.insulink.api.web.app.user;

import de.insulink.api.user.UserRepository;
import de.insulink.api.user.settings.history.UserSettingsChange;
import de.insulink.api.user.settings.history.UserSettingsChangeRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * The history hands back the caller's recorded changes as the repository orders
 * them (newest first), with a key added for the first time reading back a JSON
 * null as its old value.
 */
@AppControllerTest(UserSettingsHistoryController.class)
final class UserSettingsHistoryControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private UserSettingsChangeRepository changeRepository;

  @Test
  void theCallersChangesAreListedWithOldAndNewValue() throws Exception {
    Mockito.when(changeRepository.findByUserIdOrderByChangedAtDesc(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        UserSettingsChange.create(UUID.randomUUID(), USER_ID, "bolus_carb_factor",
          "15", "12", 1_700_000_100_000L),
        UserSettingsChange.create(UUID.randomUUID(), USER_ID, "bolus_max",
          null, "10", 1_700_000_000_000L))));
    AsyncEndpoint.on(mockMvc).call(get("/user/settings/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.changes[0].key").value("bolus_carb_factor"))
      .andExpect(jsonPath("$.changes[0].old_value").value("15"))
      .andExpect(jsonPath("$.changes[0].new_value").value("12"))
      .andExpect(jsonPath("$.changes[0].time").value(1_700_000_100_000L))
      .andExpect(jsonPath("$.changes[1].old_value").isEmpty());
  }
}
