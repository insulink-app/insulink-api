package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
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
public final class GlucoseReportController extends AppRestController {
  private final GlucoseRepository glucoseRepository;

  private GlucoseReportController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, GlucoseRepository glucoseRepository
  ) {
    super(authenticationKey, userRepository);
    this.glucoseRepository = glucoseRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/glucose/report/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> reportGlucose(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var entries = body.getObjectList("entries");
    return glucoseRepository.findByUserId(userId)
      .thenCompose(existing -> saveEntries(userId, entries, existing));
  }
  private CompletableFuture<ApiResponse> saveEntries(
    UUID userId, List<ApiRequestBody> entries, List<GlucoseEntry> existing
  ) {
    var seen = existing.stream()
      .map(GlucoseEntry::recordedAt).collect(Collectors.toCollection(HashSet::new));
    var fresh = entries.stream()
      .filter(entry -> seen.add(entry.getLong("time"))).toList();
    return AsyncIterator.execute(fresh, entry -> saveEntry(userId, entry))
      .thenApply(_ -> ApiResponse.success());
  }

  private CompletableFuture<GlucoseEntry> saveEntry(UUID userId, ApiRequestBody entry) {
    var glucose = entry.getFloat("glucose");
    var recordedAt = entry.getLong("time");
    return glucoseRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> GlucoseEntry.create(id, userId, glucose, recordedAt))
      .thenCompose(glucoseRepository::save);
  }
}