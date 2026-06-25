package de.insulink.api.iterator;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
}
