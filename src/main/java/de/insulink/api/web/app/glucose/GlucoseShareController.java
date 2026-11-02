package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.share.GlucoseShare;
import de.insulink.api.glucose.share.GlucoseShareRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The caller's glucose share links: create one, list them, delete one. What a
 * link shows is served by {@code SharedGlucoseController}, see
 * {@code docs/GLUCOSE_SHARE.md}.
 */
@RestController
public final class GlucoseShareController extends AppRestController {
  private final GlucoseShareRepository shareRepository;

  private GlucoseShareController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, GlucoseShareRepository shareRepository
  ) {
    super(authenticationKey, userRepository);
    this.shareRepository = shareRepository;
  }

  /** Creates a link named {@code name} (whom it is for) and returns it. */
  @AppEndpoint
  @RequestMapping(path = "/glucose/share/create/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> createShare(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var name = ApiRequestBody.of(payload, response).getSanitizedString("name", 64);
    var userId = findUserId(request);
    return shareRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(shareId -> GlucoseShare.create(
        shareId, userId, name, System.currentTimeMillis()))
      .thenCompose(shareRepository::save)
      .thenApply(share -> ApiResponse.success(Map.of("share", shareInformation(share))));
  }

  @AppEndpoint
  @RequestMapping(path = "/glucose/share/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findShares(HttpServletRequest request) {
    return shareRepository.findByUserIdOrderByCreatedAtDesc(findUserId(request))
      .thenApply(shares -> shares.stream().map(this::shareInformation).toList())
      .thenApply(shares -> ApiResponse.success(Map.of("shares", shares)));
  }

  /** Deletes the link {@code share_id}, which stops it working at once. */
  @AppEndpoint
  @RequestMapping(path = "/glucose/share/delete/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> deleteShare(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var shareId = ApiRequestBody.of(payload, response).getUUID("share_id");
    var userId = findUserId(request);
    return shareRepository.findById(shareId)
      .thenCompose(share -> deleteOwned(share, userId));
  }

  /**
   * 1000 for an unknown link, 1001 for one of another account: a guessed id
   * must not revoke somebody else's link.
   */
  private CompletableFuture<ApiResponse> deleteOwned(
    Optional<GlucoseShare> share, UUID userId
  ) {
    if (share.isEmpty()) {
      return ApiResponse.error(1000).future();
    }
    if (!share.get().userId().equals(userId)) {
      return ApiResponse.error(1001).future();
    }
    return shareRepository.delete(share.get()).thenApply(_ -> ApiResponse.success());
  }

  private Map<String, Object> shareInformation(GlucoseShare share) {
    return Map.of("id", share.id().toString(), "name", share.name(),
      "created_at", share.createdAt());
  }
}
