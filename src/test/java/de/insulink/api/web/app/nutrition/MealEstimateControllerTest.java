package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimate;
import de.insulink.api.nutrition.estimate.MealEstimateItem;
import de.insulink.api.nutrition.estimate.MealEstimateProgress;
import de.insulink.api.nutrition.estimate.MealEstimateRun;
import de.insulink.api.nutrition.estimate.MealEstimateRuns;
import de.insulink.api.nutrition.estimate.MealEstimateStep;
import de.insulink.api.nutrition.estimate.MealPhoto;
import de.insulink.api.nutrition.estimate.MealPhotoEstimator;
import de.insulink.api.nutrition.estimate.MealPhotoRepository;
import de.insulink.api.user.UserRepository;
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

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The estimate itself comes from the CLI; this endpoint owns the edge: only a
 * JPEG gets through, the photo is kept with its estimate against the user, and a
 * CLI that gave nothing becomes an error code rather than a plate of zeros.
 */
@AppControllerTest(MealEstimateController.class)
final class MealEstimateControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PHOTO_ID = UUID.randomUUID();
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private MealPhotoEstimator estimator;
  @MockitoBean
  private MealPhotoRepository mealPhotoRepository;
  @MockitoBean
  private MealEstimateProgress progress;
  @MockitoBean
  private MealEstimateRuns runs;
  private final MealEstimateRun run = MealEstimateRun.create("run");

  @BeforeEach
  void stubStorage() {
    Mockito.when(mealPhotoRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(PHOTO_ID));
    Mockito.when(mealPhotoRepository.save(Mockito.any()))
      .thenAnswer(call -> CompletableFuture.completedFuture(call.getArgument(0)));
    estimatesToday(0L);
    Mockito.when(runs.open(Mockito.eq(USER_ID), Mockito.any())).thenReturn(run);
  }

  private void estimatesToday(long count) {
    Mockito.when(mealPhotoRepository.countByUserIdAndTakenAtGreaterThan(
        Mockito.eq(USER_ID), Mockito.anyLong()))
      .thenReturn(CompletableFuture.completedFuture(count));
  }

  private void estimatorAnswers(CompletableFuture<MealEstimate> answer) {
    Mockito.when(estimator.estimate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(answer);
  }

  private ResultActions send(byte[] image) throws Exception {
    return AsyncEndpoint.on(mockMvc).call(post("/nutrition/estimate/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(new JSONObject()
        .put("image", Base64.getEncoder().encodeToString(image))
        .put("language", "de").toString()));
  }

  @Test
  void theEstimateIsReturnedAndThePhotoKeptForTheUser() throws Exception {
    estimatorAnswers(CompletableFuture.completedFuture(
      MealEstimate.create("Spaghetti", List.of(
        MealEstimateItem.create("Spaghetti", 0, 350, 72, 14, 18, 490)))));
    send(JPEG)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Spaghetti"))
      .andExpect(jsonPath("$.carbs").value(72.0))
      .andExpect(jsonPath("$.photo_id").value(PHOTO_ID.toString()))
      .andExpect(jsonPath("$.items[0].name").value("Spaghetti"));
    var saved = ArgumentCaptor.forClass(MealPhoto.class);
    Mockito.verify(mealPhotoRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertArrayEquals(JPEG, saved.getValue().image());
    Assertions.assertEquals(72, saved.getValue().carbs());
  }

  @Test
  void anImageThatIsNotAJpegNeverReachesTheEstimator() throws Exception {
    send(new byte[]{0x01, 0x02, 0x03, 0x04})
      .andExpect(jsonPath("$.error.code").value(1600));
    Mockito.verifyNoInteractions(estimator);
  }

  /** The model runs on the server's subscription; one account cannot use it up. */
  @Test
  void theDailyLimitStopsFurtherEstimatesBeforeTheEstimatorRuns() throws Exception {
    estimatesToday(30L);
    send(JPEG).andExpect(jsonPath("$.error.code").value(1603));
    Mockito.verifyNoInteractions(estimator);
  }

  @Test
  void aFailedEstimateIsAnErrorAndStoresNothing() throws Exception {
    estimatorAnswers(CompletableFuture.failedFuture(new IllegalStateException()));
    send(JPEG).andExpect(jsonPath("$.error.code").value(1601));
    Mockito.verify(mealPhotoRepository, Mockito.never()).save(Mockito.any());
  }

  /** The app has moved on; a run it called off is no failure. */
  @Test
  void aCancelledRunAnswersCancelledAndIsForgotten() throws Exception {
    run.cancel();
    estimatorAnswers(CompletableFuture.failedFuture(new IllegalStateException()));
    send(JPEG).andExpect(jsonPath("$.error.code").value(1604));
    Mockito.verify(runs).close(USER_ID, run);
  }

  /** The app polls this while it waits, so the step has to outlive no run. */
  @Test
  void aRunReportsItsFirstStepAndFinishesItsProgress() throws Exception {
    estimatorAnswers(CompletableFuture.failedFuture(new IllegalStateException()));
    send(JPEG);
    Mockito.verify(progress).report(Mockito.eq(USER_ID), Mockito.any());
    Mockito.verify(progress).finish(USER_ID);
  }

  @Test
  void theProgressAnswersTheCurrentStep() throws Exception {
    Mockito.when(progress.current(USER_ID)).thenReturn(Optional.of(
      MealEstimateStep.create("searching", "Kinder Country nutrition")));
    mockMvc.perform(get("/nutrition/estimate/progress/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.step").value("searching"))
      .andExpect(jsonPath("$.detail").value("Kinder Country nutrition"));
  }
}
