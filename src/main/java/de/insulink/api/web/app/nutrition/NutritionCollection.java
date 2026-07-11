package de.insulink.api.web.app.nutrition;

import de.insulink.api.database.DatabaseRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces a user's rows in a per-user nutrition collection in one shot: delete
 * the existing rows, then insert the fresh set. The app always pushes the
 * complete current list, so replace-all keeps deletes/edits correct without
 * per-item endpoints. {@code ponytail:} not transactional across the async steps
 * — a crash mid-replace is repaired by the next push (best-effort sync).
 */
@RequiredArgsConstructor(staticName = "create")
final class NutritionCollection<T> {
  private final DatabaseRepository<T, UUID> repository;

  CompletableFuture<ApiResponse> replace(List<T> existing, List<T> fresh) {
    return AsyncIterator.execute(existing, repository::delete)
      .thenCompose(_ -> AsyncIterator.execute(fresh, repository::save))
      .thenApply(_ -> ApiResponse.success());
  }
}
