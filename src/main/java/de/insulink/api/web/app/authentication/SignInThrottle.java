package de.insulink.api.web.app.authentication;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed sign-ins per account name and per client address and refuses
 * further attempts once either has failed too often within the window, so a
 * password cannot be guessed by trying. An address gets more attempts than an
 * account, because many phones can share one carrier address.
 *
 * ponytail: in memory, so it resets with a restart and only covers one api
 * instance; move it to the database if the api ever runs more than once.
 */
@Component
public final class SignInThrottle {
  private static final int MAXIMUM_ACCOUNT_FAILURES = 5;
  private static final int MAXIMUM_ADDRESS_FAILURES = 20;
  private static final long WINDOW_MS = 15 * 60 * 1000L;
  private static final int MAXIMUM_TRACKED = 10_000;

  private final ConcurrentHashMap<String, Failures> failures =
    new ConcurrentHashMap<>();

  public boolean isBlocked(String name, String address) {
    return count(accountKey(name)) >= MAXIMUM_ACCOUNT_FAILURES
      || count(addressKey(address)) >= MAXIMUM_ADDRESS_FAILURES;
  }

  public void recordFailure(String name, String address) {
    forgetExpiredWhenFull();
    increment(accountKey(name));
    increment(addressKey(address));
  }

  public void recordSuccess(String name) {
    failures.remove(accountKey(name));
  }

  private int count(String key) {
    var entry = failures.get(key);
    return entry == null || entry.isExpired() ? 0 : entry.count();
  }

  private void increment(String key) {
    failures.merge(key, Failures.first(), Failures::next);
  }

  /** Keeps a flood of made-up names from growing the map without bound. */
  private void forgetExpiredWhenFull() {
    if (failures.size() < MAXIMUM_TRACKED) {
      return;
    }
    failures.values().removeIf(Failures::isExpired);
  }

  private String accountKey(String name) {
    return "name:" + String.valueOf(name).strip().toLowerCase();
  }

  private String addressKey(String address) {
    return "address:" + address;
  }

  private record Failures(int count, long firstAt) {
    static Failures first() {
      return new Failures(1, System.currentTimeMillis());
    }

    Failures next(Failures ignored) {
      return isExpired() ? first() : new Failures(count + 1, firstAt);
    }

    boolean isExpired() {
      return System.currentTimeMillis() - firstAt > WINDOW_MS;
    }
  }
}
