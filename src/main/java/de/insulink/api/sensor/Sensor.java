package de.insulink.api.sensor;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(name = "sensors")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class Sensor {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private SensorType type;
  @Column(name = "data", nullable = false, updatable = false)
  private String data;
  @Column(name = "registered_at", nullable = false, updatable = false)
  private long registeredAt;
  @Column(name = "expires_at", nullable = false, updatable = false)
  private long expiresAt;

  public void updateData(String newData) {
    this.data = newData;
  }
}