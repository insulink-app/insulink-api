package de.insulink.api.nutrition.estimate;

import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the headless Claude Code CLI installed in the api container on one meal
 * photo. The CLI signs in through {@code CLAUDE_CODE_OAUTH_TOKEN} from the
 * container environment (see {@code docs/MEAL_PHOTO_ESTIMATE.md}).
 *
 * Estimates run one after another on a single thread: every run is a process of
 * its own, and the server has little memory to spare.
 * ponytail: one run at a time, a small pool if several users photograph at once.
 */
@RequiredArgsConstructor(staticName = "create")
public final class MealPhotoEstimator {
  /** Room for a product lookup on the web; matches the request timeout. */
  private static final long TIMEOUT_SECONDS = 120;
  private static final File WORKING_DIRECTORY =
    new File(System.getProperty("java.io.tmpdir"));

  /** Photos allowed to wait for the one running estimate; more are refused. */
  private static final int MAXIMUM_WAITING = 5;

  private final ExecutorService queue = new ThreadPoolExecutor(1, 1, 0,
    TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAXIMUM_WAITING));

  /**
   * The estimate, or a failed future when the CLI gave none in time or the
   * queue is full. A full queue answers at once instead of piling up photos in
   * memory that would each wait minutes. Each step the CLI reaches goes to
   * {@code onStep} while it runs.
   */
  public CompletableFuture<MealEstimate> estimate(
    MealImage image, String language, Consumer<MealEstimateStep> onStep
  ) {
    var prompt = MealEstimatePrompt.create(image, language);
    var stream = MealEstimateStream.create(onStep);
    try {
      return CompletableFuture.supplyAsync(() -> run(prompt, stream), queue)
        .thenApply(this::parse);
    } catch (RejectedExecutionException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  /** The estimate, or a failure that carries what the CLI said instead. */
  private MealEstimate parse(String stdout) {
    var reply = MealEstimateReply.create(stdout);
    return reply.estimate()
      .orElseThrow(() -> new CompletionException(
        new IllegalStateException("no estimate, CLI said: " + reply.lastLine())));
  }

  /**
   * Starts the CLI, writes the photo message to its stdin and reads stdout to
   * the end, line by line so {@code stream} sees each event as it comes. A run past
   * the timeout is killed, which closes the pipe and ends the read with
   * whatever came so far.
   */
  private String run(MealEstimatePrompt prompt, MealEstimateStream stream) {
    try {
      var process = new ProcessBuilder(prompt.command())
        .directory(WORKING_DIRECTORY)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
      CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .execute(process::destroyForcibly);
      writeStdin(process, prompt.stdinLine());
      return readStdout(process, stream);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private String readStdout(Process process, MealEstimateStream stream)
    throws IOException {
    var stdout = new StringBuilder();
    try (var reader = new BufferedReader(new InputStreamReader(
      process.getInputStream(), StandardCharsets.UTF_8))) {
      reader.lines().forEach(line -> {
        stream.accept(line);
        stdout.append(line).append('\n');
      });
    }
    return stdout.toString();
  }

  private void writeStdin(Process process, String line) throws IOException {
    try (var stdin = process.getOutputStream()) {
      stdin.write(line.getBytes(StandardCharsets.UTF_8));
    }
  }
}
