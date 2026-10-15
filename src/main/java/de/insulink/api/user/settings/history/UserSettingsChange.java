package de.insulink.api.user.settings.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * One key of the settings blob changing value at one moment. {@code oldValue}
 * is null when the key was added, {@code newValue} null when it was removed.
 */
@Entity
@Table(name = "user_settings_changes",
  indexes = @Index(columnList = "user_id, changed_at"))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class UserSettingsChange {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  @Column(name = "setting_key", nullable = false, updatable = false)
  private String settingKey;
  @Column(name = "old_value", updatable = false, columnDefinition = "TEXT")
  private String oldValue;
  @Column(name = "new_value", updatable = false, columnDefinition = "TEXT")
  private String newValue;
  @Column(name = "changed_at", nullable = false, updatable = false)
  private long changedAt;
}
