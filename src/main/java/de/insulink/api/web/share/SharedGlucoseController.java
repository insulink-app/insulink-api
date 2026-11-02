package de.insulink.api.web.share;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.glucose.share.GlucoseShare;
import de.insulink.api.glucose.share.GlucoseShareRepository;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What a share link shows: the owner's glucose of the last 24 h, plus the two
 * names its greeting needs (whom the link was given to, and the owner's name).
 * Public on purpose (no {@code @AppEndpoint}): the link's random id is the only
 * credential, and deleting the share revokes it. See {@code docs/GLUCOSE_SHARE.md}.
 */
@RestController
@RequiredArgsConstructor
public final class SharedGlucoseController {
  private static final long WINDOW_MS = Duration.ofHours(24).toMillis();

  private final GlucoseShareRepository shareRepository;
  private final GlucoseRepository glucoseRepository;
  private final UserRepository userRepository;

  /** 1000 for a link that does not exist (never did, or was deleted). */
  @RequestMapping(path = "/share/{shareId}/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findSharedGlucose(
    @PathVariable String shareId
  ) {
    return parseShareId(shareId)
      .map(shareRepository::findById)
      .orElse(CompletableFuture.completedFuture(Optional.empty()))
      .thenCompose(this::sharedGlucose);
  }

  /** A malformed id is just an unknown link, not a server error. */
  private Optional<UUID> parseShareId(String shareId) {
    try {
      return Optional.of(UUID.fromString(shareId));
    } catch (IllegalArgumentException _) {
      return Optional.empty();
    }
  }

  private CompletableFuture<ApiResponse> sharedGlucose(Optional<GlucoseShare> share) {
    if (share.isEmpty()) {
      return ApiResponse.error(1000).future();
    }
    return userRepository.findById(share.get().userId())
      .thenCombine(recentEntries(share.get()), (owner, entries) ->
        sharedInformation(share.get(), owner, entries));
  }

  private CompletableFuture<List<Map<String, Object>>> recentEntries(GlucoseShare share) {
    return glucoseRepository
      .findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
        share.userId(), System.currentTimeMillis() - WINDOW_MS)
      .thenApply(entries -> entries.stream().map(this::entryInformation).toList());
  }

  private ApiResponse sharedInformation(
    GlucoseShare share, Optional<User> owner, List<Map<String, Object>> entries
  ) {
    return ApiResponse.success(Map.of("name", share.name(),
      "owner", owner.map(User::name).orElse(""), "entries", entries));
  }

  private Map<String, Object> entryInformation(GlucoseEntry entry) {
    return Map.of("value", entry.value(), "time", entry.recordedAt());
  }
}
