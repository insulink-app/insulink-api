package de.insulink.api.database;

import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces a user's rows in a per-user collection (meals, drinks, products,
 * health days) in one shot: one bulk delete of the user's rows, then insert the
 * fresh set. The app always pushes the complete current list, so replace-all
 * keeps deletes/edits correct without per-item endpoints. {@code ponytail:} not
 * transactional across the async steps — a crash mid-replace, or two
 * overlapping pushes leaving a duplicate, is repaired by the next push.
 */
@RequiredArgsConstructor(staticName = "create")
public final class UserCollection<T> {
  private final UserOwnedRepository<T, UUID> repository;

  public CompletableFuture<ApiResponse> replace(UUID userId, List<T> fresh) {
    return repository.deleteAllOfUser(userId)
      .thenCompose(_ -> AsyncIterator.execute(fresh, repository::save))
      .thenApply(_ -> ApiResponse.success());
  }
}
