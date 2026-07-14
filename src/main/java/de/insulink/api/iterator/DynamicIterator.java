package de.insulink.api.iterator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DynamicIterator<T, U> {
  private final List<T> list;

  /**
   * Executes the iterator process. Every entry is started right away and the
   * returned future completes once all of them are done. A failing entry fails
   * the returned future with its cause; an empty list completes immediately.
   * @return A future response that contains the transformed data
   */
  public CompletableFuture<U> execute() {
    var futures = new CompletableFuture<?>[list.size()];
    for (var index = 0; index < list.size(); index++) {
      futures[index] = entryFuture(list.get(index), index);
    }
    return CompletableFuture.allOf(futures).thenApply(_ -> result());
  }

  /**
   * Is used to apply some transformation to the entry and register the result
   * @param entry The target entry
   * @param index The position of the entry in the input, so the implementation
   *              can keep its result in input order rather than completion order
   * @return The future response
   */
  protected abstract CompletableFuture<?> entryFuture(T entry, int index);

  /**
   * The result, that can be returned when execution is finished. Only read once
   * every entry future has completed, so implementations need no locking of
   * their own — completion of those futures publishes their writes.
   * @return The result
   */
  protected abstract U result();
}
