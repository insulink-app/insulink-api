package de.insulink.api.iterator;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Same contract as {@link AsyncIteratorTest}, for the variant that flattens a
 * list per entry into one result list. Flattening is where order is easiest to
 * lose, so the chunks here finish out of order on purpose.
 */
final class AsyncListIteratorTest {
  @Test
  void testAsyncListIterator() {
    var original = Lists.newArrayList(Lists.newArrayList(0, 1, 2),
      Lists.newArrayList(4, 5, 6), Lists.newArrayList(8, 9, 10));
    var futureResponse = AsyncListIterator.execute(original, entry ->
      CompletableFuture.completedFuture(Stream.concat(entry.stream(),
        Stream.of(entry.get(entry.size() - 1) + 1)).toList()));
    futureResponse.thenAccept(this::checkIteratorChange).join();
  }

  void checkIteratorChange(List<Integer> transformed) {
    for (var i = 0; i < 12; i++) {
      Assertions.assertTrue(transformed.contains(i));
    }
  }

  /**
   * The first chunk finishes last, so a result assembled as the chunks land
   * would start with the third chunk's entries.
   */
  @Test
  @Timeout(20)
  void chunksAreFlattenedInInputOrder() throws Exception {
    var input = Lists.newArrayList(1, 2, 3);
    var result = AsyncListIterator.<Integer, Integer>execute(input, entry ->
      CompletableFuture.supplyAsync(() -> {
        sleep((4 - entry) * 60L);
        return List.of(entry * 10, entry * 10 + 1);
      })).get(15, TimeUnit.SECONDS);
    Assertions.assertEquals(List.of(10, 11, 20, 21, 30, 31), result);
  }

  @Test
  @Timeout(20)
  void emptyChunksContributeNothingButBreakNothing() throws Exception {
    var result = AsyncListIterator.<Integer, Integer>execute(
      Lists.newArrayList(1, 2, 3), entry ->
        CompletableFuture.completedFuture(entry == 2 ? List.of() : List.of(entry))
    ).get(15, TimeUnit.SECONDS);
    Assertions.assertEquals(List.of(1, 3), result);
  }

  @Test
  @Timeout(20)
  void emptyInputCompletesWithAnEmptyResult() throws Exception {
    var result = AsyncListIterator.<Integer, Integer>execute(List.of(), entry ->
      CompletableFuture.completedFuture(List.of(entry))).get(15, TimeUnit.SECONDS);
    Assertions.assertTrue(result.isEmpty());
  }

  @Test
  @Timeout(20)
  void aFailingChunkFailsTheBatchInsteadOfHangingForever() {
    var future = AsyncListIterator.<Integer, Integer>execute(List.of(1, 2), entry ->
      entry == 2
        ? CompletableFuture.failedFuture(new IllegalStateException("boom"))
        : CompletableFuture.completedFuture(List.of(entry)));
    var thrown = Assertions.assertThrows(
      ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS)
    );
    Assertions.assertInstanceOf(IllegalStateException.class, thrown.getCause());
  }

  private void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}
