package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.delivery.PumpDelivery;
import de.insulink.api.insulin.delivery.PumpDeliveryRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * A pushed delivery is read field by field into a row owned by the caller.
 */
@AppControllerTest(PumpDeliveryController.class)
final class PumpDeliveryControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private PumpDeliveryRepository deliveryRepository;

  @Test
  void aPushedDeliveryIsStoredForTheCaller() throws Exception {
    Mockito.when(deliveryRepository.findByUserIdOrderByDeliveredAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(deliveryRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    AsyncEndpoint.on(mockMvc).call(post("/insulin/delivery/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveries\": [{\"at\": 1700000000000, \"kind\": \"bolus\","
          + " \"source\": \"notification\", \"units\": 1.25, \"confirmed\": false}]}"))
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(PumpDelivery.class);
    Mockito.verify(deliveryRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("notification", saved.getValue().source());
    Assertions.assertEquals(1.25, saved.getValue().units());
    Assertions.assertFalse(saved.getValue().confirmed());
  }
}
