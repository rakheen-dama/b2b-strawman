package io.b2mash.b2b.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * CSRF token request handler for Single-Page Applications. Uses deferred CSRF tokens with XOR-based
 * BREACH mitigation. The cookie-based token repository sets an XSRF-TOKEN cookie readable by
 * JavaScript; the SPA sends it back as the X-XSRF-TOKEN header on mutating requests.
 *
 * <p>This is the standard Spring Security 6+ pattern for SPA CSRF protection. See:
 * https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final CsrfTokenRequestAttributeHandler plain = new CsrfTokenRequestAttributeHandler();
  private final XorCsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    // Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection
    xor.handle(request, response, csrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    // If the token is submitted via header (SPA pattern), use XOR resolution for BREACH protection.
    // Otherwise, fall back to plain resolution (e.g., form submissions).
    if (headerValue != null) {
      return xor.resolveCsrfTokenValue(request, csrfToken);
    }
    return plain.resolveCsrfTokenValue(request, csrfToken);
  }
}
