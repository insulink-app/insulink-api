package de.insulink.api.user.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "user_session")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class UserSession {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private UserSessionStatus status;
  @Column(name = "device_platform")
  private String devicePlatform;
  @Column(name = "ip_address")
  private String ipAddress;
  @Column(name = "country")
  private String country;
  @Column(name = "city")
  private String city;
  @Column(name = "opened_at", nullable = false, updatable = false)
  private long openedAt;
  @Column(name = "refresh_token", nullable = false)
  private String lastRefreshToken;
  @Column(name = "last_refreshed_at", nullable = false)
  private long lastRefreshedAt;
  @Column(name = "previous_refresh_token")
  private String previousRefreshToken;

  public void close() {
    this.status = UserSessionStatus.CLOSED;
  }

  /**
   * How long before the latest rotation a token may have been issued and still
   * be redeemed. A burst of refreshes (the app and its background service at
   * once, retries after lost responses) hands out several tokens within
   * seconds, and two saves of this row overwrite each other, so the token a
   * caller ended up holding is not necessarily the stored one.
   */
  private static final long ROTATION_GRACE_MILLIS = 1000L * 60 * 5;

  /**
   * Whether the refresh token may still be redeemed: the current one, the one
   * it replaced, or any issued in the same burst as the current one. Refusing
   * any of those closed a perfectly good session and logged the user out. A
   * token from before that burst still counts as a stolen or traded-in copy.
   */
  public boolean acceptsRefreshToken(String refreshToken, Date issuedAt) {
    return !status.isClosed() && (matches(lastRefreshToken, refreshToken) ||
      matches(previousRefreshToken, refreshToken) ||
      issuedInLatestBurst(issuedAt));
  }

  /** Tokens from before issue dates were stamped have none and never qualify. */
  private boolean issuedInLatestBurst(Date issuedAt) {
    return issuedAt != null &&
      issuedAt.getTime() >= lastRefreshedAt - ROTATION_GRACE_MILLIS;
  }

  /**
   * Stores only a digest of the new token, so a copy of the table hands out no
   * usable session. The one it replaces is kept as it was stored.
   */
  public void updateRefreshToken(String refreshToken) {
    this.previousRefreshToken = this.lastRefreshToken;
    this.lastRefreshToken = digest(refreshToken);
    this.lastRefreshedAt = System.currentTimeMillis();
  }

  /**
   * A stored value matches its digest, or the token itself for rows written
   * before tokens were stored as digests; those age out with their next
   * rotation.
   */
  private boolean matches(String stored, String refreshToken) {
    return stored != null && (stored.equals(digest(refreshToken)) ||
      stored.equals(refreshToken));
  }

  private String digest(String refreshToken) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}

