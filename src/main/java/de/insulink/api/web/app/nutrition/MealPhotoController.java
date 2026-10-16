package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealPhoto;
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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hands a stored meal photo back to the user it belongs to, so the meal log can
 * show it again. A POST like the forecast reads: the id travels in the body.
 */
@RestController
public final class MealPhotoController extends AppRestController {
  private final MealPhotoRepository mealPhotoRepository;

  private MealPhotoController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, MealPhotoRepository mealPhotoRepository
  ) {
    super(authenticationKey, userRepository);
    this.mealPhotoRepository = mealPhotoRepository;
  }

  /** 1602 when there is no such photo, or it belongs to someone else. */
  @AppEndpoint
  @RequestMapping(path = "/nutrition/photos/find/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> findPhoto(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var photoId = ApiRequestBody.of(payload, response).getUUID("id");
    if (photoId == null) {
      return ApiResponse.error(1602, "photo not found").future();
    }
    var userId = findUserId(request);
    return mealPhotoRepository.findById(photoId)
      .thenApply(photo -> photoResponse(photo, userId));
  }

  private ApiResponse photoResponse(Optional<MealPhoto> photo, UUID userId) {
    return photo.filter(found -> found.userId().equals(userId))
      .map(found -> ApiResponse.success(Map.of(
        "image", Base64.getEncoder().encodeToString(found.image()))))
      .orElseGet(() -> ApiResponse.error(1602, "photo not found"));
  }
}
