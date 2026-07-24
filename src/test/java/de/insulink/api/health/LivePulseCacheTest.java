package de.insulink.api.health;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Pins the liveness rule the panel's vitals bar leans on: a reading is handed
 * out only while it is current, so a band that goes quiet reads as "no pulse"
 * instead of freezing its last bpm on screen for the rest of the workout.
 */
final class LivePulseCacheTest {
  @Test
  void readingStaysFreshUpToAndIncludingMaxAge() {
    var pulse = LivePulse.create(72, 10_000L);
    Assertions.assertTrue(pulse.isFresh(10_000L, 30_000L));
    Assertions.assertTrue(pulse.isFresh(40_000L, 30_000L));
    Assertions.assertFalse(pulse.isFresh(40_001L, 30_000L));
  }

  @Test
  void findReturnsTheReadingJustRecorded() {
    var cache = new LivePulseCache();
    var userId = UUID.randomUUID();
    cache.record(userId, 88);
    Assertions.assertEquals(88, cache.find(userId).orElseThrow().bpm());
  }

  @Test
  void recordingReplacesThePreviousReading() {
    var cache = new LivePulseCache();
    var userId = UUID.randomUUID();
    cache.record(userId, 88);
    cache.record(userId, 143);
    Assertions.assertEquals(143, cache.find(userId).orElseThrow().bpm());
  }

  @Test
  void usersDoNotSeeEachOthersPulse() {
    var cache = new LivePulseCache();
    var runner = UUID.randomUUID();
    cache.record(runner, 88);
    Assertions.assertTrue(cache.find(UUID.randomUUID()).isEmpty());
  }

  @Test
  void noViewerUntilSomeoneReads() {
    var cache = new LivePulseCache();
    var userId = UUID.randomUUID();
    cache.record(userId, 88);
    Assertions.assertFalse(cache.isWatched(userId));
    cache.find(userId);
    Assertions.assertTrue(cache.isWatched(userId));
  }
}
