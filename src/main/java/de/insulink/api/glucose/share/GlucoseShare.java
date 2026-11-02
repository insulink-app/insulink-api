package de.insulink.api.glucose.share;

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

/**
 * A link that shows the owner's glucose, and nothing else, to whoever holds it.
 * The random {@code id} IS the secret in the link; {@code name} only tells the
 * owner whom they gave it to. Deleting the row revokes the link.
 */
@Entity
@Table(name = "glucose_shares")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class GlucoseShare {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "created_at", nullable = false, updatable = false)
  private long createdAt;
}
