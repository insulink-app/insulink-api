package de.insulink.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHook {
  /**
   * The call priority of the hook
   * (determines the call sequence, relative to the other hooks)
   * @return The priority
   */
  HookPriority priority() default HookPriority.NEUTRAL;
}
