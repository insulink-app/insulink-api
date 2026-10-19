package de.insulink.api.user.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class UserSettings {
  /**
   * Many times the size of a real settings blob. Every changed key becomes a
   * history row, so an unbounded blob would let one request write any number.
   */
  public static final int MAXIMUM_CONTENT_LENGTH = 64 * 1024;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;
  @Column(name = "last_updated_at", nullable = false)
  private long lastUpdatedAt;
}