package de.insulink.api.web.app.event;

import de.insulink.api.event.Event;
import de.insulink.api.event.EventRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class EventReportController extends AppRestController {
  private final EventRepository eventRepository;

  private EventReportController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, EventRepository eventRepository
  ) {
    super(authenticationKey, userRepository);
    this.eventRepository = eventRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/event/report/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> reportEvents(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var entries = body.getObjectList("entries");
    return eventRepository.findByUserId(userId)
      .thenCompose(existing -> saveEntries(userId, entries, existing));
  }

  private CompletableFuture<ApiResponse> saveEntries(
    UUID userId, List<ApiRequestBody> entries, List<Event> existing
  ) {
    var seen = existing.stream()
      .map(EventReportController::seenKey).collect(Collectors.toCollection(HashSet::new));
    var fresh = entries.stream()
      .filter(entry -> seen.add(seenKey(entry))).toList();
    return AsyncIterator.execute(fresh, event -> saveEntry(userId, event))
      .thenApply(_ -> ApiResponse.success());
  }

  private CompletableFuture<Event> saveEntry(UUID userId, ApiRequestBody entry) {
    var type = entry.getString("type");
    var data = entry.has("data") ? entry.getString("data") : "";
    var recordedAt = entry.getLong("time");
    return eventRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> Event.create(id, userId, type, data, recordedAt))
      .thenCompose(eventRepository::save);
  }

  private static String seenKey(Event event) {
    return event.type() + "@" + event.recordedAt();
  }

  private static String seenKey(ApiRequestBody entry) {
    return entry.getString("type") + "@" + entry.getLong("time");
  }
}
