package de.insulink.api.sport;

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
 * A routine template: a name and its ordered exercise items, kept as the app's
 * JSON array in {@code items} (the items are value objects owned by the
 * routine, not a separate table). {@code clientId} is the app's own id.
 */
@Entity
@Table(name = "sport_routines")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class SportRoutine {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "client_id", nullable = false, updatable = false)
  private String clientId;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "items", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String items;
  @Column(name = "order_index", nullable = false, updatable = false)
  private int orderIndex;
}
