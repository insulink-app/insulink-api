package de.insulink.api.health;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A heart-rate reading as it arrives from the worn band: a bpm and the instant
 * this server took it. The instant is ours, not the phone's — the reading is
 * relayed within milliseconds of being measured, so the arrival time is the
 * measurement time for every purpose here, and no device's clock has to be
 * trusted to decide whether a pulse is current.
 * <p>
 * Deliberately not an entity: the live reading lives in {@link LivePulseCache}
 * and is never stored. The durable curve is {@link PulseSample}.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(staticName = "create")
public final class LivePulse {
  private final int bpm;
  private final long receivedAt;

  /**
   * Whether the reading is still current. A band that stops delivering (taken
   * off, out of range, app closed) must read as "no pulse" rather than freeze
   * the last bpm on screen forever.
   */
  public boolean isFresh(long now, long maxAgeMs) {
    return now - receivedAt <= maxAgeMs;
  }
}
