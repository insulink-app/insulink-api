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
  @Column(name = "open_time", nullable = false)
  private long openTime;
  @Column(name = "refresh_token")
  private String lastRefreshToken;
  @Column(name = "last_refresh")
  private long lastRefresh;

  public void close() {
    this.status = UserSessionStatus.CLOSED;
  }

  public void updateRefreshToken(String refreshToken) {
    this.lastRefreshToken = refreshToken;
    this.lastRefresh = System.currentTimeMillis();
  }
}

