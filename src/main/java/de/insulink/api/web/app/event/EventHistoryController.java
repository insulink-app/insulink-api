package de.insulink.api.web.app.event;

import de.insulink.api.event.Event;
import de.insulink.api.event.EventRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class EventHistoryController extends AppRestController {
  private final EventRepository eventRepository;

  private EventHistoryController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, EventRepository eventRepository
  ) {
    super(authenticationKey, userRepository);
    this.eventRepository = eventRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/event/history/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findEventHistory(
    HttpServletRequest request
  ) {
    return eventRepository.findByUserId(findUserId(request))
      .thenApply(entries -> entries.stream()
        .map(this::eventInformation).toList())
      .thenApply(entries -> ApiResponse.success(Map.of("entries", entries)));
  }

  private Map<String, Object> eventInformation(Event event) {
    return Map.of("type", event.type(), "time", event.recordedAt(),
      "data", event.data());
  }
}
