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
 * A recorded (or auto-detected) endurance training: type, start/end, distance
 * and the GPS route kept as the app's JSON array in {@code track}.
 * {@code clientId} is the app's own id.
 */
@Entity
@Table(name = "sport_trainings")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class CardioTraining {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "client_id", nullable = false, updatable = false)
  private String clientId;
  @Column(name = "type", nullable = false, updatable = false)
  private String type;
  @Column(name = "started_at", nullable = false, updatable = false)
  private long startedAt;
  @Column(name = "ended_at", nullable = false, updatable = false)
  private long endedAt;
  @Column(name = "distance", nullable = false, updatable = false)
  private double distance;
  @Column(name = "detected", nullable = false, updatable = false)
  private boolean detected;
  @Column(name = "track", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String track;
}
