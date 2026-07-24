package de.insulink.api.web;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * The signing key a sliced controller test runs on, plus the tokens to go with
 * it. Mirrors how {@code WebModule} derives the key from {@code config.ini},
 * only from a fixed secret, so the controllers parse a real JWT instead of a
 * stubbed one and a wrongly signed or expired key fails the same way it would
 * in production.
 */
@TestConfiguration
public class TestAuthentication {
  private static final String SECRET =
    "insulink-test-authentication-secret-key-long-enough-for-hs256";
  private static final String REFRESH_SECRET =
    "insulink-test-refresh-secret-key-long-enough-for-hs256-as-well";
  private static final String OTHER_SECRET =
    "some-other-secret-key-that-is-also-long-enough-for-hs256-here";

  public static final Key KEY = key(SECRET);
  public static final Key REFRESH_KEY = key(REFRESH_SECRET);

  private static Key key(String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
      SignatureAlgorithm.HS256.getJcaName());
  }

  @Bean("authenticationKey")
  Key authenticationKey() {
    return KEY;
  }

  @Bean("refreshKey")
  Key refreshKey() {
    return REFRESH_KEY;
  }

  /**
   * A refresh token as {@code /refresh/} expects it: same claims, signed with
   * the separate refresh key.
   */
  public static String refreshToken(UUID userId, UUID sessionId) {
    return token(REFRESH_KEY, userId, sessionId,
      new Date(System.currentTimeMillis() + 3_600_000L));
  }

  /**
   * A refresh token signed with the authentication key instead — well-formed,
   * but not something {@code /refresh/} may accept.
   */
  public static String refreshTokenWithWrongKey(UUID userId, UUID sessionId) {
    return token(KEY, userId, sessionId,
      new Date(System.currentTimeMillis() + 3_600_000L));
  }

  /**
   * A valid bearer header for the given user, carrying the session id the
   * session endpoints read back out of it.
   */
  public static String bearer(UUID userId, UUID sessionId) {
    return "Bearer " + token(KEY, userId, sessionId,
      new Date(System.currentTimeMillis() + 3_600_000L));
  }

  public static String bearer(UUID userId) {
    return bearer(userId, UUID.randomUUID());
  }

  /**
   * A token that is well-formed but signed with a key the api does not know.
   */
  public static String bearerWithWrongSignature(UUID userId) {
    return "Bearer " + token(key(OTHER_SECRET), userId, UUID.randomUUID(),
      new Date(System.currentTimeMillis() + 3_600_000L));
  }

  public static String expiredBearer(UUID userId) {
    return "Bearer " + token(KEY, userId, UUID.randomUUID(),
      new Date(System.currentTimeMillis() - 1_000L));
  }

  private static String token(
    Key signingKey, UUID userId, UUID sessionId, Date expiration
  ) {
    return Jwts.builder()
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .expiration(expiration)
      .signWith(signingKey)
      .compact();
  }
}
