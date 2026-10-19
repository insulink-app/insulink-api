package de.insulink.api.web.security.app;

import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import de.insulink.api.web.security.EndpointRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.UUID;

@Component
public final class AppAuthenticationFilter extends OncePerRequestFilter {
  private final Key authenticationKey;
  private final EndpointRepository endpointRepository;
  private final UserSessionRepository sessionRepository;

  private AppAuthenticationFilter(
    @Qualifier("authenticationKey") Key authenticationKey,
    EndpointRepository endpointRepository,
    UserSessionRepository sessionRepository
  ) {
    this.authenticationKey = authenticationKey;
    this.endpointRepository = endpointRepository;
    this.sessionRepository = sessionRepository;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var apiKey = request.getHeader("Authorization");
    if (apiKey == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    var validation = validateApiKey(apiKey.replace("Bearer ", ""));
    if (validation != HttpServletResponse.SC_ACCEPTED) {
      response.setStatus(validation);
      return;
    }
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !endpointRepository.routesToAnnotated(request, AppEndpoint.class);
  }

  /**
   * 202 for a valid token of a session that is still open, 417 for an expired
   * one (the app refreshes and retries), 403 for anything else, which ends the
   * session on the client. Checking the session makes a logout or a password
   * change take effect at once instead of when the token runs out.
   */
  private int validateApiKey(String apiKey) {
    try {
      var claims = Jwts.parser()
        .setSigningKey(authenticationKey)
        .build()
        .parseClaimsJws(apiKey)
        .getPayload();
      return isSessionOpen(claims) ? HttpServletResponse.SC_ACCEPTED :
        HttpServletResponse.SC_FORBIDDEN;
    } catch (ExpiredJwtException exception) {
      return HttpServletResponse.SC_EXPECTATION_FAILED;
    } catch (Exception exception) {
      return HttpServletResponse.SC_FORBIDDEN;
    }
  }

  private boolean isSessionOpen(Claims claims) {
    var userId = UUID.fromString(claims.get("id", String.class));
    var sessionId = UUID.fromString(claims.get("session", String.class));
    return sessionRepository.findById(sessionId).join()
      .filter(session -> session.userId().equals(userId))
      .map(UserSession::status)
      .filter(UserSessionStatus::isActive)
      .isPresent();
  }
}
