package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimate;
import de.insulink.api.nutrition.estimate.MealImage;
import de.insulink.api.nutrition.estimate.MealEstimateProgress;
import de.insulink.api.nutrition.estimate.MealEstimateRun;
import de.insulink.api.nutrition.estimate.MealEstimateRuns;
import de.insulink.api.nutrition.estimate.MealEstimateStep;
import de.insulink.api.nutrition.estimate.MealPhoto;
import de.insulink.api.nutrition.estimate.MealPhotoEstimator;
import de.insulink.api.nutrition.estimate.MealPhotoRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Estimates a meal from a photo. The app puts the carbs into the bolus
 * calculator's editable field; nothing here doses or stores a meal. The photo is
 * kept with its raw estimate for documentation.
 */
@RestController
public final class MealEstimateController extends AppRestController {
  private static final Logger LOGGER =
    LoggerFactory.getLogger(MealEstimateController.class);

  private final MealPhotoEstimator estimator;
  private final MealPhotoRepository mealPhotoRepository;
  private final MealEstimateProgress progress;
  private final MealEstimateRuns runs;

  private MealEstimateController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, MealPhotoEstimator estimator,
    MealPhotoRepository mealPhotoRepository, MealEstimateProgress progress,
    MealEstimateRuns runs
  ) {
    super(authenticationKey, userRepository);
    this.estimator = estimator;
    this.mealPhotoRepository = mealPhotoRepository;
    this.progress = progress;
    this.runs = runs;
  }

  /**
   * Estimates per user and day. Each one runs the model on the server's
   * subscription and keeps a photo, so without a ceiling one account could use
   * up both.
   */
  private static final int MAXIMUM_ESTIMATES_PER_DAY = 30;

  /**
   * 1600 for a photo that is not a JPEG within the cap, 1601 when no estimate
   * came, 1603 once the day's estimates are used up, 1604 when the app called
   * the run off ({@code run_id}, see {@link MealEstimateCancelController}).
   */
  @AppEndpoint
  @RequestMapping(path = "/nutrition/estimate/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> estimateMeal(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var image = MealImage.fromBase64(body.getString("image"));
    if (!image.valid()) {
      return ApiResponse.error(1600, "invalid image").future();
    }
    var userId = findUserId(request);
    var dayAgo = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
    return mealPhotoRepository.countByUserIdAndTakenAtGreaterThan(userId, dayAgo)
      .thenCompose(today -> today >= MAXIMUM_ESTIMATES_PER_DAY ?
        ApiResponse.error(1603, "daily limit reached").future() :
        estimateMeal(userId, image, body.getString("language", 2),
          runId(body)));
  }

  /** The app's id for this run; empty from an app too old to cancel one. */
  private String runId(ApiRequestBody body) {
    return body.has("run_id") ? body.getString("run_id", 64) : "";
  }

  /**
   * The step the user's running estimate is at ({@code step}, {@code detail}),
   * polled by the app while it waits; no step when none runs.
   */
  @AppEndpoint
  @RequestMapping(path = "/nutrition/estimate/progress/", method = RequestMethod.GET)
  public ApiResponse estimateProgress(HttpServletRequest request) {
    return ApiResponse.success(progress.current(findUserId(request))
      .map(MealEstimateStep::information).orElse(Map.of()));
  }

  private CompletableFuture<ApiResponse> estimateMeal(
    UUID userId, MealImage image, String language, String runId
  ) {
    progress.report(userId, MealEstimateStep.looking());
    var run = runs.open(userId, runId);
    return estimator.estimate(image, language, run,
        step -> progress.report(userId, step))
      .whenComplete((estimate, failure) -> finish(userId, run))
      .thenCompose(estimate -> keep(userId, image, estimate))
      .thenApply(this::estimateResponse)
      .exceptionally(failure -> estimateFailed(run, failure));
  }

  private void finish(UUID userId, MealEstimateRun run) {
    progress.finish(userId);
    runs.close(userId, run);
  }

  /**
   * Logs why there is no estimate (the CLI's answer, or the database refusing
   * the photo) and answers 1601. The app can only say that it failed, so the
   * reason has to be in the server log. A run the app called off is no failure
   * and answers 1604, to an app that no longer listens.
   */
  private ApiResponse estimateFailed(MealEstimateRun run, Throwable failure) {
    if (run.cancelled()) {
      return ApiResponse.error(1604, "estimate cancelled");
    }
    LOGGER.error("Meal photo estimate failed", failure);
    return ApiResponse.error(1601, "estimate unavailable");
  }

  /** Stores the photo with its estimate. */
  private CompletableFuture<MealPhoto> keep(
    UUID userId, MealImage image, MealEstimate estimate
  ) {
    return mealPhotoRepository.generateAvailableId(UUID::randomUUID)
      .thenCompose(id -> mealPhotoRepository.save(
        MealPhoto.of(id, userId, image, estimate)));
  }

  /**
   * The estimate plus the stored photo's {@code photo_id}, which the app keeps on
   * the meal entry so the photo can be opened again from the meal log.
   */
  private ApiResponse estimateResponse(MealPhoto photo) {
    return ApiResponse.success(photo.estimate().information())
      .expand(Map.of("photo_id", photo.id().toString()));
  }
}
