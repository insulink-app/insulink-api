package de.insulink.api.iterator;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class AsyncIterator<T, U> extends DynamicIterator<T, List<U>> {
  /**
   * Creates and executes an async iterator that performs async transformation
   * on entries and wait till all entries have be transformed
   * @param list The list of entries
   * @param transformation The transformation that should be applied to the entries
   * @return The transformed entries
   * @param <T> Entry input type
   * @param <U> Entry output type
   */
  public static <T, U> CompletableFuture<List<U>> execute(
    List<T> list, Function<T, CompletableFuture<U>> transformation
  ) {
    var iterator = AsyncIterator.<T, U>create(list, transformation);
    return iterator.execute();
  }


  /**
   * Creates an async iterator that performs async transformation
   * on entries and wait till all entries have be transformed
   * @param list The list of entries
   * @param transformation The transformation that should be applied to the entries
   * @return The transformed entries
   * @param <T> Entry input type
   * @param <U> Entry output type
   */
  public static <T, U> AsyncIterator<T, U> create(
    List<T> list, Function<T, CompletableFuture<U>> transformation
  ) {
    return new AsyncIterator<T, U>(list, transformation);
  }

  private final Function<T, CompletableFuture<U>> transformation;
  private final List<U> result = Collections.synchronizedList(Lists.newArrayList());

  private AsyncIterator(
    List<T> list, Function<T, CompletableFuture<U>> transformation
  ) {
    super(list);
    this.transformation = transformation;
  }

  @Override
  protected CompletableFuture<?> entryFuture(T entry) {
    return transformation.apply(entry).thenAccept(result::add);
  }

  @Override
  protected List<U> result() {
    return result;
  }
}