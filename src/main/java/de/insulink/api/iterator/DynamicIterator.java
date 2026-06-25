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
  private int counter = 0;

  /**
   * Executes the iterator process
   * @return A future response that contains the transformed data
   */
  public CompletableFuture<U> execute() {
    if (list.isEmpty()) {
      return CompletableFuture.completedFuture(result());
    }
    var futureResponse = new CompletableFuture<U>();
    for (var entry : list) {
      entryFuture(entry).thenAccept(value -> counter++)
        .thenAccept(value -> checkCompletion(futureResponse));
    }
    return futureResponse;
  }

  private void checkCompletion(CompletableFuture<U> futureResponse) {
    if (counter == list.size()) {
      futureResponse.complete(result());
    }
  }

  /**
   * Is used to apply some transformation to the entry and register the result
   * @param entry The target erntry
   * @return The future response
   */
  protected abstract CompletableFuture<?> entryFuture(T entry);

  /**
   * The result, that can be returned when execution is finished
   * @return The result
   */
  protected abstract U result();
}
