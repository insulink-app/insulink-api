package de.insulink.api;

import de.insulink.api.application.ApplicationLaunchEvent;
import de.insulink.api.event.EventExecutor;
import de.insulink.api.event.HookRegistry;
import de.insulink.api.log.Log;
import de.insulink.api.web.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collections;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"de.insulink.api"})
public class ApiApplication {
  /**
   * The starting point where the application is executed
   *
   * @param args The arguments that are passed into the application
   */
  public static void main(String[] args) {
    try (var apiContext = new AnnotationConfigApplicationContext(ApiModule.class)) {
      var log = apiContext.getBean(Log.class);
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
        log.processError(throwable));
      try {
        log.info("Initializing Insulink - Api");
        var application = new SpringApplication(ApiApplication.class);
        var apiConfiguration = apiContext.getBean(WebConfiguration.class);
        application.setDefaultProperties(Collections.singletonMap("server.port",
          apiConfiguration.port()));
        log.info("Booting Spring...");
        var applicationContext = application.run(args);
        var eventExecutor = applicationContext.getBean(EventExecutor.class);
        registerHooks(applicationContext);
        log.info("Spring successfully booted");
        log.info("Successfully booted Insulink - Api");
        eventExecutor.execute(ApplicationLaunchEvent.create());
      } catch (Exception exception) {
        log.processError(exception);
      }
    }
  }

  private static void registerHooks(ConfigurableApplicationContext context) {
    var hookRegistry = context.getBean(HookRegistry.class);
  }
}