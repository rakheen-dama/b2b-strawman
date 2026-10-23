package io.b2mash.b2b.b2bstrawman.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The portal's origin (scheme + host [+ port]) derived once at startup from {@code
 * docteams.app.portal-base-url} (env {@code PORTAL_BASE_URL}). Used as the only allowed {@code
 * frame-ancestors} source for the acceptance PDF (ADR-352). Any path, query or fragment is ignored.
 * A malformed value fails bean creation, so a misconfigured deployment cannot silently open the PDF
 * to arbitrary framers.
 */
@Component
public class PortalOrigin {

  /**
   * CSP3 host-source host-part without wildcards: dot-separated labels of ALPHA / DIGIT / "-".
   * Rejects hosts {@link URI} accepts but CSP cannot express (bracketed IPv6, trailing dot).
   */
  private static final Pattern CSP_HOST = Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*");

  private final String value;

  public PortalOrigin(
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
    this.value = parse(portalBaseUrl);
  }

  /** The origin, e.g. {@code http://localhost:3002} or {@code https://portal-dev.heykazi.com}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  static String parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("docteams.app.portal-base-url must be set to the portal URL");
    }
    URI uri;
    try {
      uri = new URI(raw.trim());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("docteams.app.portal-base-url is malformed: " + raw, e);
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        || host == null
        || host.isBlank()
        || uri.getRawUserInfo() != null) {
      throw new IllegalStateException(
          "docteams.app.portal-base-url must be an absolute http(s) URL with a host: " + raw);
    }
    if (!CSP_HOST.matcher(host).matches()) {
      throw new IllegalStateException(
          "docteams.app.portal-base-url host cannot be a CSP frame-ancestors source: " + raw);
    }
    int port = uri.getPort();
    return scheme.toLowerCase(Locale.ROOT)
        + "://"
        + host.toLowerCase(Locale.ROOT)
        + (port == -1 ? "" : ":" + port);
  }
}
