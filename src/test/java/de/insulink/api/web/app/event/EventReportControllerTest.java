package de.insulink.api.web.app.event;

import de.insulink.api.event.Event;
import de.insulink.api.event.EventRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App-side events (a low alarm, a lost signal) are reported in batches the app
 * resends until they stick, so they are deduplicated by type + time: the same
 * alarm never lands twice, while two different alarms in the same second both
 * do. The optional data blob defaults to empty rather than null.
 */
@AppControllerTest(EventReportController.class)
final class EventReportControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long ALARM_TIME = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private EventRepository eventRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyHistory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(eventRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(eventRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private void report(String payload) throws Exception {
    endpoint.call(post("/event/report/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  private List<Event> saved() {
    var captor = ArgumentCaptor.forClass(Event.class);
    Mockito.verify(eventRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void aReportedEventIsStoredWithItsTypeDataAndTime() throws Exception {
    report("""
      {"entries": [
        {"type": "glucose_low", "data": "54", "time": 1700000000000}
      ]}""");
    var event = saved().getFirst();
    Assertions.assertEquals(USER_ID, event.userId());
    Assertions.assertEquals("glucose_low", event.type());
    Assertions.assertEquals("54", event.data());
    Assertions.assertEquals(ALARM_TIME, event.recordedAt());
  }

  @Test
  void anEventWithoutDataIsStoredWithAnEmptyBlobNotNull() throws Exception {
    report("""
      {"entries": [{"type": "signal_loss", "time": 1700000000000}]}""");
    Assertions.assertEquals("", saved().getFirst().data());
  }

  @Test
  void anEventAlreadyOnTheServerIsNotStoredAgain() throws Exception {
    Mockito.when(eventRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(Event.create(
        UUID.randomUUID(), USER_ID, "glucose_low", "54", ALARM_TIME))));
    report("""
      {"entries": [{"type": "glucose_low", "data": "54", "time": 1700000000000}]}""");
    Mockito.verify(eventRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void theSameEventRepeatedInsideOneBatchIsStoredOnce() throws Exception {
    report("""
      {"entries": [
        {"type": "glucose_low", "time": 1700000000000},
        {"type": "glucose_low", "time": 1700000000000}
      ]}""");
    Assertions.assertEquals(1, saved().size());
  }

  /**
   * Two alarms can genuinely fire in the same second — only the pair of type
   * and time makes an event the same event.
   */
  @Test
  void twoDifferentEventsAtTheSameInstantAreBothStored() throws Exception {
    report("""
      {"entries": [
        {"type": "glucose_low", "time": 1700000000000},
        {"type": "signal_loss", "time": 1700000000000}
      ]}""");
    Assertions.assertEquals(2, saved().size());
  }

  @Test
  void anEmptyBatchIsAcceptedAndStoresNothing() throws Exception {
    report("{\"entries\": []}");
    Mockito.verify(eventRepository, Mockito.never()).save(Mockito.any());
  }
}
