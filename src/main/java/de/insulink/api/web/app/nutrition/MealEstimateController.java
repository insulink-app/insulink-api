package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimate;
import de.insulink.api.nutrition.estimate.MealImage;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
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
  private final MealPhotoEstimator estimator;
  private final MealPhotoRepository mealPhotoRepository;

  private MealEstimateController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, MealPhotoEstimator estimator,
    MealPhotoRepository mealPhotoRepository
  ) {
    super(authenticationKey, userRepository);
    this.estimator = estimator;
    this.mealPhotoRepository = mealPhotoRepository;
  }

  /** 1600 for a photo that is not a JPEG within the cap, 1601 when no estimate came. */
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
    return estimator.estimate(image, body.getString("language", 2))
      .thenCompose(estimate -> keep(userId, image, estimate))
      .thenApply(this::estimateResponse)
      .exceptionally(_ -> ApiResponse.error(1601, "estimate unavailable"));
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
