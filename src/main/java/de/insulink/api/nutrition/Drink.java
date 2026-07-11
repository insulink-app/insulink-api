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
 * A logged drink for the hydration tracker: how many millilitres of which kind,
 * and when. Synced replace-all like the meals.
 */
@Entity
@Table(name = "nutrition_drinks")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Drink {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "drinked_at", nullable = false, updatable = false)
  private long drinkedAt;
  @Column(name = "ml", nullable = false, updatable = false)
  private int ml;
  @Column(name = "kind", nullable = false, updatable = false)
  private String kind;
}
