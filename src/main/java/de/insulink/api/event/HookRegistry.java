package de.insulink.api.event;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor(staticName = "create")
public final class HookRegistry {
  private final List<Hook> hooks = Lists.newArrayList();

  /**
   * Registers a new hook
   * @param hook The hook that is to be registered
   */
  public void register(Hook hook) {
    hooks.add(hook);
  }

  /**
   * Unregisters a hook
   * @param hook The hook that is to be unregistered
   */
  public void unregister(Hook hook) {
    hooks.remove(hook);
  }

  /**
   * Unregisters a hook by the hook class
   * @param hookClass The class of the hook that is to be unregistered
   */
  public void unregister(Class<? extends Hook> hookClass) {
    findByClass(hookClass).ifPresent(this::unregister);
  }

  /**
   * Is used to find a hook by class
   * @param hookClass The class of the hook
   * @return The hook if it could be found
   */
  public Optional<Hook> findByClass(Class<? extends Hook> hookClass) {
    return hooks.stream()
      .filter(hook -> hook.getClass().equals(hookClass))
      .findFirst();
  }

  /**
   * Lists all registered hook
   * @return The hooks
   */
  public List<Hook> findAll() {
    return List.copyOf(hooks);
  }
}
