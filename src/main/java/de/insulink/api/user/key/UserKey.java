package de.insulink.api.user.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;
import java.util.UUID;

/**
 * The user's data key, wrapped on the device with a key derived from their
 * password. The server only keeps it so a reinstalled app can unwrap it again
 * at sign-in; it never sees the password-derived key, so the stored text is
 * opaque here and the pod keys it protects stay unreadable on the server.
 */
@Entity
@Table(name = "user_keys")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class UserKey {
  /** Generous for the small JSON envelope, small enough to refuse anything else. */
  public static final int MAXIMUM_CONTENT_LENGTH = 4096;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;
  @Column(name = "updated_at", nullable = false)
  private long updatedAt;

  /** The key to store, or empty when the text is not a JSON object of sane size. */
  public static Optional<UserKey> of(UUID userId, String content) {
    if (content.length() > MAXIMUM_CONTENT_LENGTH || !isJsonObject(content)) {
      return Optional.empty();
    }
    return Optional.of(create(userId, content, System.currentTimeMillis()));
  }

  private static boolean isJsonObject(String content) {
    try {
      new JSONObject(content);
      return true;
    } catch (JSONException _) {
      return false;
    }
  }
}
