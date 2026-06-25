package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    var glucose = body.getFloat("glucose");
    var userId = findUserId(request);
    return glucoseRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> GlucoseEntry.create(id, userId, glucose,
        System.currentTimeMillis()))
      .thenCompose(glucoseRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }
}