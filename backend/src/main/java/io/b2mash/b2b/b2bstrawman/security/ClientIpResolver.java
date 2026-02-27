package io.b2mash.b2b.b2bstrawman.security;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client IP address from a servlet request, handling reverse proxy headers. */
public final class ClientIpResolver {

  private ClientIpResolver() {}

  /**
   * Extracts the client IP from the request. Checks X-Forwarded-For (first IP), then X-Real-IP,
   * then falls back to {@code request.getRemoteAddr()}.
   */
  public static String resolve(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp.trim();
    }
    return request.getRemoteAddr();
  }
}
