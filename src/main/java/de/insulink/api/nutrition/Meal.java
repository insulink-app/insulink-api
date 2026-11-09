package de.insulink.api.nutrition;

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
 * A logged meal: its carbs, the glucose and bolus it was dosed with, when, and
 * the picked products kept as the app's JSON array in {@code entries} (empty for
 * a manual carb entry). Synced replace-all like the sport collections.
 *
 * <p>{@code deliveredByPump} marks a dose the pod gave rather than one the user
 * injected. {@code splitOf} links a later part of a split bolus to the meal it
 * belongs to, by that meal's {@code time}; null for every other meal. Both have
 * to come back exactly as the app sent them: the app merges a pull with its own
 * log by content, so a field lost here doubles the meal and its insulin.
 */
@Entity
@Table(name = "nutrition_meals")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Meal {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "time", nullable = false, updatable = false)
  private long time;
  @Column(name = "carbs", nullable = false, updatable = false)
  private double carbs;
  @Column(name = "glucose", nullable = false, updatable = false)
  private int glucose;
  @Column(name = "bolus", nullable = false, updatable = false)
  private double bolus;
  @Column(name = "entries", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String entries;
  @Column(name = "delivered_by_pump", nullable = false, updatable = false,
    columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
  private boolean deliveredByPump;
  @Column(name = "split_of", updatable = false)
  private Long splitOf;
}
