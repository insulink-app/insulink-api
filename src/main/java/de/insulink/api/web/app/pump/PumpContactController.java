package de.insulink.api.web.app.pump;

import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.pump.contact.PumpContact;
import de.insulink.api.pump.contact.PumpContactRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The moments the app reached the pump, as minute-aligned epoch ms oldest first
 * ({@code {contacts: [...]}}). {@code sync} inserts the pushed times not stored
 * yet and never deletes, so the pump's uptime history outlives the app's
 * two-day local log and every pod change.
 */
@RestController
public final class PumpContactController extends AppRestController {
  /** Matches the app's local cap (250 contacts). */
  private static final int MAX_CONTACTS = 250;

  private final PumpContactRepository contactRepository;

  private PumpContactController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpContactRepository contactRepository
  ) {
    super(authenticationKey, userRepository);
    this.contactRepository = contactRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/contact/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findContacts(HttpServletRequest request) {
    return contactRepository.findByUserIdOrderByContactedAt(findUserId(request))
      .thenApply(contacts -> contacts.stream().map(PumpContact::contactedAt).toList())
      .thenApply(contacts -> ApiResponse.success(Map.of("contacts", contacts)));
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/contact/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncContacts(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var pushed = ApiRequestBody.of(payload, response).<Number>getList("contacts")
      .stream().limit(MAX_CONTACTS).map(Number::longValue).toList();
    return contactRepository.findByUserIdOrderByContactedAt(userId)
      .thenCompose(existing -> storeUnseen(userId, existing, pushed));
  }

  /** Saves every pushed time not stored yet; a re-sent one is skipped. */
  private CompletableFuture<ApiResponse> storeUnseen(
    UUID userId, List<PumpContact> existing, List<Long> pushed
  ) {
    var stored = existing.stream().map(PumpContact::contactedAt).collect(Collectors.toSet());
    var unseen = pushed.stream().distinct().filter(time -> !stored.contains(time))
      .map(time -> PumpContact.create(UUID.randomUUID(), userId, time)).toList();
    return AsyncIterator.execute(unseen, contactRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }
}
