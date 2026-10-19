package de.insulink.api.user;

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
@Table(name = "users")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "create")
public final class User {
  /**
   * The shortest password a new account or a password change may set. Signing
   * in with an older, shorter one keeps working.
   */
  public static final int MINIMUM_PASSWORD_LENGTH = 10;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;
  @Column(name = "name", nullable = false, unique = true)
  private String name;
  @Column(name = "password", nullable = false)
  private String password;
  @Column(name = "language", nullable = false)
  private String language;
  @Column(name = "compliant", nullable = false)
  private boolean compliant;
  @Column(name = "joined_at", nullable = false, updatable = false)
  private long joinedAt;

  public void changeName(String newName) {
    this.name = newName;
  }

  public void changePassword(String newPassword) {
    this.password = newPassword;
  }

  public void changeLanguage(String newLanguage) {
    this.language = newLanguage;
  }
}
