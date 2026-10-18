package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.estimate.MealEstimate;
import de.insulink.api.nutrition.estimate.MealEstimateItem;
import de.insulink.api.nutrition.estimate.MealImage;
import de.insulink.api.nutrition.estimate.MealPhoto;
import de.insulink.api.nutrition.estimate.MealPhotoRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * A meal photo is health data: it goes back only to the user it was taken by,
 * and someone else's id looks exactly like one that does not exist.
 */
@AppControllerTest(MealPhotoController.class)
final class MealPhotoControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PHOTO_ID = UUID.randomUUID();
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private MealPhotoRepository mealPhotoRepository;

  private void storedFor(UUID owner) {
    var photo = MealPhoto.of(PHOTO_ID, owner, MealImage.create(JPEG),
      MealEstimate.create("Spaghetti", List.of(
        MealEstimateItem.create("Spaghetti", 0, 350, 72, 14, 18, 490))));
    Mockito.when(mealPhotoRepository.findById(PHOTO_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(photo)));
  }

  private ResultActions find(String id) throws Exception {
    return AsyncEndpoint.on(mockMvc).call(post("/nutrition/photos/find/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(new JSONObject().put("id", id).toString()));
  }

  @Test
  void theOwnerGetsThePhotoBack() throws Exception {
    storedFor(USER_ID);
    find(PHOTO_ID.toString())
      .andExpect(jsonPath("$.image").value(Base64.getEncoder().encodeToString(JPEG)));
  }

  @Test
  void someoneElsesPhotoIsNotFound() throws Exception {
    storedFor(UUID.randomUUID());
    find(PHOTO_ID.toString()).andExpect(jsonPath("$.error.code").value(1602));
  }

  @Test
  void anIdThatIsNoUuidIsNotFound() throws Exception {
    find("not-an-id").andExpect(jsonPath("$.error.code").value(1602));
    Mockito.verifyNoInteractions(mealPhotoRepository);
  }
}
