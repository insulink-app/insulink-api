package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimateRuns;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.TestAuthentication;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The cancel reaches the caller's own run, by the id the app gave it. */
@AppControllerTest(MealEstimateCancelController.class)
final class MealEstimateCancelControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private MealEstimateRuns runs;

  @Test
  void theCallersRunIsCancelled() throws Exception {
    mockMvc.perform(post("/nutrition/estimate/cancel/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(new JSONObject().put("run_id", "photo-1").toString()))
      .andExpect(status().isOk());
    Mockito.verify(runs).cancel(USER_ID, "photo-1");
  }
}
