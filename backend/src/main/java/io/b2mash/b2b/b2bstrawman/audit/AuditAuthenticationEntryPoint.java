package io.b2mash.b2b.b2bstrawman.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom {@link AuthenticationEntryPoint} that logs structured authentication failure warnings
 * before delegating to {@link BearerTokenAuthenticationEntryPoint} for the actual 401 response.
 *
 * <p>Auth failures are logged via {@code log.warn()} only -- NOT written to the audit database.
 * Authentication failures happen before tenant context is bound (no valid JWT = no tenant schema to
 * write to).
 */
@Component
public class AuditAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(AuditAuthenticationEntryPoint.class);

  private final BearerTokenAuthenticationEntryPoint delegate;

  public AuditAuthenticationEntryPoint() {
    this.delegate = new BearerTokenAuthenticationEntryPoint();
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) {
    log.warn(
        "security.auth_failed: path={}, method={}, reason={}, remote_addr={}",
        request.getRequestURI(),
        request.getMethod(),
        authException.getMessage(),
        request.getRemoteAddr());

    delegate.commence(request, response, authException);
  }
}
