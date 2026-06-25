package de.insulink.api.user.device;

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

@Entity
@Table(name = "user_devices")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class UserDevice {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "device_id", nullable = false, updatable = false)
  private String deviceId;
  @Column(name = "operating_system", nullable = false, updatable = false)
  private String operatingSystem;
  @Column(name = "operating_system_version", nullable = false)
  private String operatingSystemVersion;
  @Column(name = "device_brand", nullable = false, updatable = false)
  private String deviceBrand;
  @Column(name = "device_model", nullable = false, updatable = false)
  private String deviceModel;
  @Column(name = "device_name", nullable = false)
  private String deviceName;
}