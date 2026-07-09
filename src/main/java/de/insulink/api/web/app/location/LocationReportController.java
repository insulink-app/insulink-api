package de.insulink.api.web.app.location;

import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.location.LocationEntry;
import de.insulink.api.location.LocationRepository;
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
public final class LocationReportController extends AppRestController {
  private final LocationRepository locationRepository;

  private LocationReportController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, LocationRepository locationRepository
  ) {
    super(authenticationKey, userRepository);
    this.locationRepository = locationRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/location/report/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> reportLocation(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var entries = body.getObjectList("entries");
    return locationRepository.findByUserId(userId)
      .thenCompose(existing -> saveEntries(userId, entries, existing));
  }

  private CompletableFuture<ApiResponse> saveEntries(
    UUID userId, List<ApiRequestBody> entries, List<LocationEntry> existing
  ) {
    var seen = existing.stream()
      .map(LocationEntry::recordedAt).collect(Collectors.toCollection(HashSet::new));
    var fresh = entries.stream()
      .filter(entry -> seen.add(entry.getLong("time"))).toList();
    return AsyncIterator.execute(fresh, entry -> saveEntry(userId, entry))
      .thenApply(_ -> ApiResponse.success());
  }

  private CompletableFuture<LocationEntry> saveEntry(UUID userId, ApiRequestBody entry) {
    var latitude = entry.getDouble("lat");
    var longitude = entry.getDouble("lng");
    var recordedAt = entry.getLong("time");
    return locationRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> LocationEntry.create(id, userId, latitude, longitude, recordedAt))
      .thenCompose(locationRepository::save);
  }
}
