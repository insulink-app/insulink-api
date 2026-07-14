package de.insulink.api.iterator;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

public final class AsyncListIterator<T, U> extends DynamicIterator<T, List<U>> {
  /**
   * Creates and executes an async iterator that performs async transformation
   * on lists of entries and wait till all entries have be transformed
   * @param list The list of entry lists
   * @param transformation The transformation that should be applied to the entries
   * @return The transformed entries flattened into one single list, in the order
   *         of the input list
   * @param <T> Entry input type
   * @param <U> Entry output type
   */
  public static <T, U> CompletableFuture<List<U>> execute(
    List<T> list, Function<T, CompletableFuture<List<U>>> transformation
  ) {
    var iterator = AsyncListIterator.<T, U>create(list, transformation);
    return iterator.execute();
  }

  /**
   * Creates an async iterator that performs async transformation
   * on lists of entries and wait till all entries have be transformed
   * @param list The list of entry lists
   * @param transformation The transformation that should be applied to the entries
   * @return The transformed entries flattened into one single list, in the order
   *         of the input list
   * @param <T> Entry input type
   * @param <U> Entry output type
   */
  public static <T, U> AsyncListIterator<T, U> create(
    List<T> list, Function<T, CompletableFuture<List<U>>> transformation
  ) {
    return new AsyncListIterator<T, U>(list, transformation);
  }

  private final Function<T, CompletableFuture<List<U>>> transformation;
  private final AtomicReferenceArray<List<U>> slots;

  private AsyncListIterator(
    List<T> list, Function<T, CompletableFuture<List<U>>> transformation
  ) {
    super(list);
    this.transformation = transformation;
    this.slots = new AtomicReferenceArray<List<U>>(list.size());
  }

  @Override
  protected CompletableFuture<?> entryFuture(T entry, int index) {
    return transformation.apply(entry).thenAccept(value -> slots.set(index, value));
  }

  @Override
  protected List<U> result() {
    var result = Lists.<U>newArrayList();
    for (var index = 0; index < slots.length(); index++) {
      result.addAll(slots.get(index));
    }
    return result;
  }
}
