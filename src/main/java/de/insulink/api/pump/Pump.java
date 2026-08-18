package de.insulink.api.pump;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(name = "pumps")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Pump {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private PumpType type;
  /**
   * The opaque pairing blob the app writes and reads back. Deliberately
   * updatable, unlike the other columns: a pod's stored state changes while it
   * runs (its command and session counters advance), so the app rewrites this.
   * A column marked {@code updatable = false} is excluded from generated UPDATE
   * statements, which would make {@link #updateData(String)} a silent no-op.
   */
  @Column(name = "data", nullable = false, columnDefinition = "TEXT")
  private String data;
  @Column(name = "registered_at", nullable = false, updatable = false)
  private long registeredAt;
  @Column(name = "expires_at", nullable = false, updatable = false)
  private long expiresAt;

  public void updateData(String newData) {
    this.data = newData;
  }
}