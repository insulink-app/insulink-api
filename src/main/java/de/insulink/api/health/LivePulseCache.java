package de.insulink.api.health;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The newest heart-rate reading per user, held in memory only. The band streams
 * about once a second — far too fast, and far too worthless once a second old,
 * to be worth a row each. So the live reading is relayed, not stored: the phone
 * pushes here, other devices read here, and nothing survives a restart. The
 * durable minute-by-minute curve is {@link PulseSample}, pushed on its own
 * slower path.
 * <p>
 * Readings are dropped once stale, so a user who stops pushing leaves nothing
 * behind. {@code ponytail:} in-memory means per-instance — with more than one
 * API replica a reader could hit an instance the writer never reached, and this
 * moves to a shared store (Redis) or the reading rides a push channel instead.
 */
@Component
public final class LivePulseCache {
  /**
   * How long a reading counts as current. Generous next to the band's ~1 Hz, so
   * a hiccup in the phone's upload does not blink the value out mid-set, but
   * short enough that a band taken off reads as gone within seconds.
   */
  public static final long MAX_AGE_MS = 30_000L;

  private final ConcurrentHashMap<UUID, LivePulse> readings = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Long> watchedAt = new ConcurrentHashMap<>();

  /**
   * Takes the phone's newest reading, replacing whatever it last pushed. Stamped
   * on arrival, so freshness never depends on the phone's clock being right.
   */
  public void record(UUID userId, int bpm) {
    readings.put(userId, LivePulse.create(bpm, System.currentTimeMillis()));
  }

  /**
   * The user's current reading, empty when the band went quiet. Evicts on the
   * way out, so a user who stopped pushing is forgotten rather than kept as a
   * stale entry nobody reads again. A read also marks a live viewer, so the
   * phone can relay at 1 Hz only while something is actually watching.
   */
  public Optional<LivePulse> find(UUID userId) {
    watchedAt.put(userId, System.currentTimeMillis());
    var pulse = readings.get(userId);
    if (pulse == null) {
      return Optional.empty();
    }
    if (!pulse.isFresh(System.currentTimeMillis(), MAX_AGE_MS)) {
      readings.remove(userId, pulse);
      return Optional.empty();
    }
    return Optional.of(pulse);
  }

  /**
   * Whether any device polled {@link #find} within {@link #MAX_AGE_MS} — i.e. a
   * live viewer (the panel's running-routine vitals bar) is currently watching.
   * The phone reads this off its push response to decide between a 1 Hz relay
   * and a slow keepalive, so it only pins the radio up while someone looks. Only
   * the panel ever reads, so a viewer is never the pushing phone itself.
   */
  public boolean isWatched(UUID userId) {
    var at = watchedAt.get(userId);
    if (at == null) {
      return false;
    }
    if (System.currentTimeMillis() - at > MAX_AGE_MS) {
      watchedAt.remove(userId, at);
      return false;
    }
    return true;
  }
}
