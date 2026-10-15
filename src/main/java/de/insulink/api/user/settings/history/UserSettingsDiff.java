package de.insulink.api.user.settings.history;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compares two settings blobs key by key. The app always pushes the complete
 * blob, so only the keys whose value actually differs become a change; values
 * are compared as text because the panel may send a boolean or number where the
 * app sends the same value as a string.
 */
@RequiredArgsConstructor(staticName = "create")
public final class UserSettingsDiff {
  private final UUID userId;
  private final long changedAt;

  public List<UserSettingsChange> changes(String oldContent, String newContent) {
    var oldSettings = new JSONObject(oldContent);
    var newSettings = new JSONObject(newContent);
    var keys = new HashSet<>(oldSettings.keySet());
    keys.addAll(newSettings.keySet());
    return keys.stream().sorted()
      .map(key -> change(key, text(oldSettings, key), text(newSettings, key)))
      .filter(Objects::nonNull)
      .toList();
  }

  /** The change for one key, or {@code null} when its value stayed the same. */
  private UserSettingsChange change(String key, String oldValue, String newValue) {
    if (Objects.equals(oldValue, newValue)) {
      return null;
    }
    return UserSettingsChange.create(
      UUID.randomUUID(), userId, key, oldValue, newValue, changedAt);
  }

  private String text(JSONObject settings, String key) {
    return settings.has(key) ? String.valueOf(settings.get(key)) : null;
  }
}
