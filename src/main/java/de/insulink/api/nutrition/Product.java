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
 * A product in the user's food database with its per-100 g/ml nutrition, keyed
 * for the app by {@code barcode}. {@code serving} is the natural serving size in
 * the product's unit (nullable). {@code orderIndex} preserves the user's order
 * across the round trip. Synced replace-all like the meals.
 */
@Entity
@Table(name = "nutrition_products")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Product {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "barcode", nullable = false, updatable = false)
  private String barcode;
  @Column(name = "name", nullable = false, updatable = false)
  private String name;
  @Column(name = "brand", nullable = false, updatable = false)
  private String brand;
  @Column(name = "unit", nullable = false, updatable = false)
  private String unit;
  @Column(name = "serving", updatable = false)
  private Double serving;
  @Column(name = "serving_label", nullable = false, updatable = false)
  private String servingLabel;
  @Column(name = "carbs", nullable = false, updatable = false)
  private double carbs;
  @Column(name = "fat", nullable = false, updatable = false)
  private double fat;
  @Column(name = "protein", nullable = false, updatable = false)
  private double protein;
  @Column(name = "kcal", nullable = false, updatable = false)
  private double kcal;
  @Column(name = "order_index", nullable = false, updatable = false)
  private int orderIndex;
}
