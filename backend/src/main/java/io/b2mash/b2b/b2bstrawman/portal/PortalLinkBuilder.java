package io.b2mash.b2b.b2bstrawman.portal;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The one place a client-facing portal URL is built (Phase 88 U11, §13.6.6, ADR-351). An
 * authenticated portal path always carries {@code orgId} (the org's external id — the Keycloak
 * alias, e.g. {@code mathebula-partners}) so a deep link lands branded even in a fresh browser; the
 * exchange link always carries {@code token} then {@code orgId}; the acceptance link is the public
 * {@code /accept/{token}} page with no org. {@code PortalLinkBuilderArchTest} forbids any other
 * class from reading {@code docteams.app.portal-base-url} (except {@code security.PortalOrigin},
 * which derives a CSP origin, not a link). Firm-app links ({@code orgAppUrl}) are not portal links.
 */
@Component
public class PortalLinkBuilder {

  private final String portalBaseUrl;

  public PortalLinkBuilder(
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
    this.portalBaseUrl =
        portalBaseUrl.endsWith("/")
            ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1)
            : portalBaseUrl;
  }

  /** {@code {base}{path}(?|&)orgId={orgId}} — an authenticated portal route. */
  public String authenticated(String orgId, String path) {
    requireText(orgId, "orgId is required for an authenticated portal link");
    if (path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("portal path must start with '/': " + path);
    }
    String separator = path.contains("?") ? "&" : "?";
    return portalBaseUrl + path + separator + "orgId=" + query(orgId);
  }

  /** {@code {base}/auth/exchange?token={token}&orgId={orgId}} — the magic-link exchange page. */
  public String exchange(String orgId, String token) {
    requireText(orgId, "orgId is required for a portal exchange link");
    requireText(token, "token is required for a portal exchange link");
    return portalBaseUrl + "/auth/exchange?token=" + query(token) + "&orgId=" + query(orgId);
  }

  /** {@code {base}/accept/{token}} — the public acceptance page (the token resolves the firm). */
  public String acceptance(String token) {
    requireText(token, "token is required for an acceptance link");
    return portalBaseUrl + "/accept/" + UriUtils.encodePathSegment(token, StandardCharsets.UTF_8);
  }

  private static String query(String value) {
    return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
