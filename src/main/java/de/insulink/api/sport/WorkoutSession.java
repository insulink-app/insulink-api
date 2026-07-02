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
 * A completed workout (the logbook): which routine, when it started, and the
 * logged sets kept as the app's JSON array in {@code sets}. {@code clientId} and
 * {@code routineClientId} are the app's own ids.
 */
@Entity
@Table(name = "sport_workouts")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class WorkoutSession {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "client_id", nullable = false, updatable = false)
  private String clientId;
  @Column(name = "routine_client_id", nullable = false, updatable = false)
  private String routineClientId;
  @Column(name = "started_at", nullable = false, updatable = false)
  private long startedAt;
  @Column(name = "sets", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String sets;
}
