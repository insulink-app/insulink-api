package de.insulink.api.nutrition.estimate;

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
 * A meal photo kept for documentation, with the estimate Claude gave for it.
 * {@code image} is the compressed JPEG exactly as the app sent it; the values are
 * the raw estimate, before the user corrected anything in the editor.
 * {@code takenAt} is epoch milliseconds.
 */
@Entity
@Table(name = "nutrition_meal_photos")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class MealPhoto {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;
  @Column(name = "taken_at", nullable = false, updatable = false)
  private long takenAt;
  @Column(name = "image", nullable = false, updatable = false)
  private byte[] image;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "grams", nullable = false, updatable = false)
  private double grams;
  @Column(name = "carbs", nullable = false, updatable = false)
  private double carbs;
  @Column(name = "fat", nullable = false, updatable = false)
  private double fat;
  @Column(name = "protein", nullable = false, updatable = false)
  private double protein;
  @Column(name = "kcal", nullable = false, updatable = false)
  private double kcal;

  /** The record for one photo and the estimate it produced. */
  public static MealPhoto of(UUID id, UUID userId, MealImage image,
    MealEstimate estimate) {
    return create(id, userId, System.currentTimeMillis(), image.bytes(),
      estimate.name(), estimate.grams(), estimate.carbs(), estimate.fat(),
      estimate.protein(), estimate.kcal());
  }
}
