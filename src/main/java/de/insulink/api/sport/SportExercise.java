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
 * An exercise template in the user's library. {@code clientId} is the app's own
 * id (kept so routines can reference it); {@code orderIndex} preserves the
 * user's custom order across the round trip.
 */
@Entity
@Table(name = "sport_exercises")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class SportExercise {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "client_id", nullable = false, updatable = false)
  private String clientId;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "kind", nullable = false, updatable = false)
  private String kind;
  @Column(name = "order_index", nullable = false, updatable = false)
  private int orderIndex;
}
