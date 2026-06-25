package de.insulink.api.iterator;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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
}