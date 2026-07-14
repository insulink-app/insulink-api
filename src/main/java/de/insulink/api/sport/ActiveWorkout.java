package de.insulink.api.sport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * The user's single in-progress workout, held as the app's own snapshot JSON in
 * {@code data} (pointers, timestamps and the sets logged so far). A user runs at
 * most one workout at a time, so one row per user is the whole model: whichever
 * device drives the workout pushes the snapshot, and any other device — the app
 * after a restart, or the web panel — resumes from it. The row is deleted when
 * the workout finishes. {@code updatedAt} is stamped by the server, so it orders
 * writes from devices whose clocks disagree.
 */
@Entity
@Table(name = "sport_active_workouts", uniqueConstraints = @UniqueConstraint(
  name = "uq_sport_active_workout_user", columnNames = {"user_id"}))
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class ActiveWorkout {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  @Column(name = "data", nullable = false, columnDefinition = "TEXT")
  private String data;
  @Column(name = "updated_at", nullable = false)
  private long updatedAt;
}
