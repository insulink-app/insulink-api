package de.insulink.api.iterator;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class AsyncListIterator<T, U> extends DynamicIterator<T, List<U>> {
  /**
   * Creates and executes an async iterator that performs async transformation
   * on lists of entries and wait till all entries have be transformed
   * @param list The list of entry lists
   * @param transformation The transformation that should be applied to the entries
   * @return The transformed entries (only one single list)
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
   * @return The transformed entries (only one single list)
   * @param <T> Entry input type
   * @param <U> Entry output type
   */
  public static <T, U> AsyncListIterator<T, U> create(
    List<T> list, Function<T, CompletableFuture<List<U>>> transformation
  ) {
    return new AsyncListIterator<T, U>(list, transformation);
  }

  private final Function<T, CompletableFuture<List<U>>> transformation;
  private final List<U> result = Collections.synchronizedList(Lists.newArrayList());

  private AsyncListIterator(
    List<T> list, Function<T, CompletableFuture<List<U>>> transformation
  ) {
    super(list);
    this.transformation = transformation;
  }

  @Override
  protected CompletableFuture<?> entryFuture(T entry) {
    return transformation.apply(entry).thenAccept(result::addAll);
  }

  @Override
  protected List<U> result() {
    return result;
  }
}
