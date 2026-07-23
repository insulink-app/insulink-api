package de.insulink.api.web.app.statistic;

import de.insulink.api.statistic.StatisticConfiguration;
import de.insulink.api.statistic.installation.AppInstallation;
import de.insulink.api.statistic.installation.AppInstallationRepository;
import de.insulink.api.statistic.opening.AppOpening;
import de.insulink.api.statistic.opening.AppOpeningRepository;
import de.insulink.api.user.UserRepository;
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
 * Install and open counters, the only endpoints without a user behind them.
 * They are guarded by a shared key instead, compared in constant time so the
 * comparison cannot be timed out character by character, and a wrong key is
 * simply not counted. The reported version string is user-controlled input and
 * is sanitized and length-capped before it is stored.
 */
@AppControllerTest(StatisticController.class)
final class StatisticControllerTest {
  private static final String KEY = "statistic-key";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private AppInstallationRepository installationRepository;
  @MockitoBean
  private AppOpeningRepository openingRepository;
  @MockitoBean
  private StatisticConfiguration statisticConfiguration;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubCounters() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(statisticConfiguration.statisticKey()).thenReturn(KEY);
    Mockito.when(installationRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(openingRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(installationRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(openingRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private ResultActions note(String path, String payload) throws Exception {
    return endpoint.call(post(path)
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void anInstallationIsCountedWithTheRightKey() throws Exception {
    note("/statistic/app/installation/", "{\"key\": \"" + KEY + "\"}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(AppInstallation.class);
    Mockito.verify(installationRepository).save(saved.capture());
    Assertions.assertTrue(saved.getValue().joinedAt() > 0L);
  }

  @Test
  void anOpeningIsCountedWithTheReportedVersion() throws Exception {
    note("/statistic/app/opening/",
      "{\"key\": \"" + KEY + "\", \"version\": \"1.4.2\"}")
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(AppOpening.class);
    Mockito.verify(openingRepository).save(saved.capture());
    Assertions.assertEquals("1.4.2", saved.getValue().version());
    Assertions.assertTrue(saved.getValue().openedAt() > 0L);
  }

  @Test
  void aWrongKeyCountsNothing() throws Exception {
    note("/statistic/app/installation/", "{\"key\": \"wrong\"}")
      .andExpect(jsonPath("$.success").value(false));
    note("/statistic/app/opening/", "{\"key\": \"wrong\", \"version\": \"1.4.2\"}")
      .andExpect(jsonPath("$.success").value(false));
    Mockito.verify(installationRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(openingRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * A prefix of the key must fail exactly like anything else — the comparison
   * is constant time and length aware.
   */
  @Test
  void aKeyThatIsOnlyAPrefixOfTheRightOneIsRefused() throws Exception {
    note("/statistic/app/installation/", "{\"key\": \"statistic\"}")
      .andExpect(jsonPath("$.success").value(false));
    Mockito.verify(installationRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aMissingKeyIsRefused() throws Exception {
    note("/statistic/app/installation/", "{}")
      .andExpect(jsonPath("$.success").value(false));
  }

  /**
   * The version is whatever the client claims, and it ends up on a dashboard —
   * so it is sanitized and capped rather than stored as sent.
   */
  @Test
  void aVersionStringIsCappedInLength() throws Exception {
    note("/statistic/app/opening/",
      "{\"key\": \"" + KEY + "\", \"version\": \"" + "9".repeat(200) + "\"}")
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(AppOpening.class);
    Mockito.verify(openingRepository).save(saved.capture());
    Assertions.assertTrue(saved.getValue().version().length() <= 32,
      saved.getValue().version());
  }
}
