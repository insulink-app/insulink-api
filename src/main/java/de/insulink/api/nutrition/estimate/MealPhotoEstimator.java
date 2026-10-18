package de.insulink.api.nutrition.estimate;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
  private static final long TIMEOUT_SECONDS = 60;
  private static final File WORKING_DIRECTORY =
    new File(System.getProperty("java.io.tmpdir"));

  private final ExecutorService queue = Executors.newSingleThreadExecutor();

  /** The estimate, or a failed future when the CLI gave none in time. */
  public CompletableFuture<MealEstimate> estimate(MealImage image, String language) {
    var prompt = MealEstimatePrompt.create(image, language);
    return CompletableFuture.supplyAsync(() -> run(prompt), queue)
      .thenApply(this::parse);
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
   * the end. A run past the timeout is killed, which closes the pipe and ends
   * the read with whatever came so far.
   */
  private String run(MealEstimatePrompt prompt) {
    try {
      var process = new ProcessBuilder(prompt.command())
        .directory(WORKING_DIRECTORY)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
      CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .execute(process::destroyForcibly);
      writeStdin(process, prompt.stdinLine());
      return new String(process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private void writeStdin(Process process, String line) throws IOException {
    try (var stdin = process.getOutputStream()) {
      stdin.write(line.getBytes(StandardCharsets.UTF_8));
    }
  }
}
