package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimateRuns;
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

/**
 * Calls off a running meal photo estimate. The app sends it when the user takes
 * a new photo or leaves the camera while the server still looks at the old one:
 * dropping the request alone would leave the model running, spending tokens on
 * an answer nobody reads.
 */
@RestController
public final class MealEstimateCancelController extends AppRestController {
  private final MealEstimateRuns runs;

  private MealEstimateCancelController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, MealEstimateRuns runs
  ) {
    super(authenticationKey, userRepository);
    this.runs = runs;
  }

  /**
   * Cancels the run {@code run_id} of the caller, now or as soon as it opens.
   * Succeeds whether or not one ran: a run already over has nothing to stop.
   */
  @AppEndpoint
  @RequestMapping(path = "/nutrition/estimate/cancel/", method = RequestMethod.POST)
  public ApiResponse cancelEstimate(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var runId = ApiRequestBody.of(payload, response).getString("run_id", 64);
    runs.cancel(findUserId(request), runId);
    return ApiResponse.success();
  }
}
