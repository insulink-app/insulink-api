package de.insulink.api.iterator;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Covers the batch plumbing every controller chains through. The transformations
 * here finish out of order and on other threads on purpose — with transformations
 * that complete inline, completion order equals input order and the interesting
 * failures cannot show up at all. Every test is timeout-bounded, because the
 * failure mode being guarded against is a future that never completes.
 */
final class AsyncIteratorTest {
  @Test
  void testAsyncIterator() {
    var original = Lists.newArrayList(1, 2, 3);
    var futureResponse = AsyncIterator.execute(original, entry ->
      CompletableFuture.completedFuture(entry + 1));
    futureResponse.thenAccept(transformed ->
      checkIteratorChange(original, transformed)).join();
  }

  void checkIteratorChange(List<Integer> original, List<Integer> transformed) {
    for (var i = 0; i < original.size(); i++) {
      Assertions.assertEquals(original.get(i), transformed.get(i) - 1);
    }
  }

  /**
   * The entries finish in reverse: the first entry sleeps longest. The result
   * still has to follow the input, not the finishing line.
   */
  @Test
  @Timeout(20)
  void resultFollowsInputOrderNotCompletionOrder() throws Exception {
    var input = Lists.newArrayList(1, 2, 3, 4, 5);
    var result = AsyncIterator.<Integer, Integer>execute(input, entry ->
      CompletableFuture.supplyAsync(() -> {
        sleep((6 - entry) * 40L);
        return entry * 10;
      })).get(15, TimeUnit.SECONDS);
    Assertions.assertEquals(List.of(10, 20, 30, 40, 50), result);
  }

  @Test
  @Timeout(20)
  void aFailingEntryFailsTheBatchInsteadOfHangingForever() {
    var future = AsyncIterator.<Integer, Integer>execute(List.of(1, 2, 3), entry ->
      entry == 2
        ? CompletableFuture.failedFuture(new IllegalStateException("boom"))
        : CompletableFuture.completedFuture(entry));
    var thrown = Assertions.assertThrows(
      ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS)
    );
    Assertions.assertInstanceOf(IllegalStateException.class, thrown.getCause());
    Assertions.assertEquals("boom", thrown.getCause().getMessage());
  }

  @Test
  @Timeout(20)
  void aThrowingTransformationDoesNotHangTheBatch() {
    var future = AsyncIterator.<Integer, Integer>execute(List.of(1, 2, 3), entry ->
      CompletableFuture.supplyAsync(() -> {
        if (entry == 3) {
          throw new IllegalArgumentException("nope");
        }
        return entry;
      }));
    var thrown = Assertions.assertThrows(
      ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS)
    );
    Assertions.assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
  }

  @Test
  @Timeout(20)
  void emptyInputCompletesWithAnEmptyResult() throws Exception {
    var result = AsyncIterator.<Integer, Integer>execute(List.of(), entry ->
      CompletableFuture.completedFuture(entry)).get(15, TimeUnit.SECONDS);
    Assertions.assertTrue(result.isEmpty());
  }

  @Test
  @Timeout(20)
  void nullResultsStillOccupyTheirSlot() throws Exception {
    var result = AsyncIterator.<Integer, Integer>execute(List.of(1, 2, 3), entry ->
      CompletableFuture.completedFuture(entry == 2 ? null : entry)
    ).get(15, TimeUnit.SECONDS);
    Assertions.assertEquals(Lists.newArrayList(1, null, 3), result);
  }

  /**
   * Releases every entry at the same instant, so the bookkeeping is hit by all
   * threads at once. Repeated, since a lost update would only show sometimes.
   */
  @Test
  @Timeout(60)
  void everyEntryIsAccountedForWhenTheyAllFinishAtOnce() throws Exception {
    var width = 64;
    var pool = Executors.newFixedThreadPool(width);
    try {
      for (var attempt = 0; attempt < 50; attempt++) {
        var input = Lists.<Integer>newArrayList();
        for (var entry = 0; entry < width; entry++) {
          input.add(entry);
        }
        var barrier = new CyclicBarrier(width);
        var result = AsyncIterator.<Integer, Integer>execute(input, entry ->
          CompletableFuture.supplyAsync(() -> {
            await(barrier);
            return entry;
          }, pool)).get(30, TimeUnit.SECONDS);
        Assertions.assertEquals(input, result);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  private void await(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (Exception _) {
      Thread.currentThread().interrupt();
    }
  }
}
