package de.insulink.api.user.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
   * Whether the refresh token may still be redeemed: the current one, or the
   * one it replaced. The replaced one stays good until the next rotation
   * because a refresh whose response was lost on a bad connection has already
   * rotated here, and the app, never having seen the new token, can only
   * come back with the old one. Refusing it logged the user out.
   */
  public boolean acceptsRefreshToken(String refreshToken) {
    return !status.isClosed() && (refreshToken.equals(lastRefreshToken) ||
      refreshToken.equals(previousRefreshToken));
  }

  public void updateRefreshToken(String refreshToken) {
    this.previousRefreshToken = this.lastRefreshToken;
    this.lastRefreshToken = refreshToken;
    this.lastRefreshedAt = System.currentTimeMillis();
  }
}

