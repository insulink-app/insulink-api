package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.session.UserAgent;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppRestController;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.net.InetAddress;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Accessors(fluent = true)
public class AuthenticationController extends AppRestController {
  private final UserSessionRepository sessionRepository;
  private final Key refreshKey;
  private final DatabaseReader geoDatabaseReader;

  protected AuthenticationController(
    Key authenticationKey, Key refreshKey, UserRepository userRepository,
    UserSessionRepository sessionRepository, DatabaseReader geoDatabaseReader
  ) {
    super(authenticationKey, userRepository);
    this.sessionRepository = sessionRepository;
    this.refreshKey = refreshKey;
    this.geoDatabaseReader = geoDatabaseReader;
  }

  private static final long MAXIMUM_AUTHENTICATION_EXPIRATION_TIME =
    1000L * 60 * 10;

  protected String generateAuthenticationToken(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_AUTHENTICATION_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(authenticationKey())
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

  protected CompletableFuture<ApiResponse> authenticationResponse(
    HttpServletRequest request, User user
  ) {
    return sessionRepository.generateAvailableId(UUID::randomUUID)
      .thenComposeAsync(sessionId -> authenticationResponse(request,
        user, sessionId));
  }

  private CompletableFuture<ApiResponse> authenticationResponse(
    HttpServletRequest request, User user, UUID sessionId
  ) {
    var authenticationToken = generateAuthenticationToken(user.id(), sessionId);
    var refreshToken = generateRefreshToken(user.id(), sessionId);
    return storeSession(request, user.id(), sessionId, refreshToken)
      .thenApply(_ -> ApiResponse.success(Map.of("user", user.id(),
        "name", user.name(), "authentication_token", authenticationToken,
        "refresh_token", refreshToken)));
  }

  private CompletableFuture<UserSession> storeSession(
    HttpServletRequest request, UUID userId, UUID sessionId, String refreshToken
  ) {
    var country = "";
    var city = "";
    var platform = "";
    var ipAddress = request.getHeader("X-Real-IP");
    try {
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      country = location.country().name();
      city = location.city().name();
      platform = UserAgent.create(request.getHeader("User-Agent")).findPlatform();
    } catch (Exception ignored) {
    }
    var session = UserSession.create(sessionId, userId,
      UserSessionStatus.ACTIVE, platform, ipAddress, country, city,
      System.currentTimeMillis(), refreshToken, System.currentTimeMillis());
    return sessionRepository.save(session);
  }
}
