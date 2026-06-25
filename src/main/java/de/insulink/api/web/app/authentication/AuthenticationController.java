package de.insulink.api.web.app.authentication;

import de.insulink.api.web.security.app.AppRestController;
import de.insulink.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
public class AuthenticationController extends AppRestController {
  private final Key verificationKey;
  private final Key authenticationKey;
  private final Key refreshKey;

  protected AuthenticationController(
    Key verificationKey, Key authenticationKey, Key refreshKey,
    UserRepository userRepository
  ) {
    super(authenticationKey, userRepository);
    this.verificationKey = verificationKey;
    this.authenticationKey = authenticationKey;
    this.refreshKey = refreshKey;
  }

  private static final long MAXIMUM_VERIFICATION_EXPIRATION_TIME =
    1000L * 60 * 60;

  protected String generateVerificationToken(String phoneNumber) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_VERIFICATION_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("phone_number", phoneNumber)
      .signWith(verificationKey)
      .compact();
  }

  private static final long MAXIMUM_AUTHENTICATION_EXPIRATION_TIME =
    1000L * 60 * 10;

  protected String generateAuthenticationToken(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_AUTHENTICATION_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(authenticationKey)
      .compact();
  }

  private static final long MAXIMUM_REFRESH_EXPIRATION_TIME =
    1000L * 60 * 60 * 24 * 365;

  protected String generateRefreshToken(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_REFRESH_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(refreshKey)
      .compact();
  }

  protected Claims verifyToken(Key key, String token) {
    try {
      return Jwts.parser()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getPayload();
    } catch (Exception exception) {
      return null;
    }
  }
}
