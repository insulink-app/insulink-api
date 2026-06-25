package de.insulink.api.event;

import de.insulink.api.log.Log;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor(staticName = "create")
public final class EventExecutor {
  private final HookRegistry registry;
  private final Log log;

  /**
   * Executes an event
   * @param event The event that is to be executed
   */
  @SneakyThrows
  public void execute(Event event) {
    try {
      event.call(registry);
    } catch (Exception exception) {
      log.processError(exception);
    }
  }

  /**
   * This function executes an event without catching any exception
   * @param event The event that is to be executed
   * @throws Exception
   */
  public void executeUncaught(Event event) throws Exception {
    event.call(registry);
  }
}
