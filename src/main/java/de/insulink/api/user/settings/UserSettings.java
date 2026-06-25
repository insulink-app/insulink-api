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
  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "content", nullable = false)
  private String content;
  @Column(name = "last_updated_at", nullable = false)
  private long lastUpdatedAt;
}