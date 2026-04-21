package io.b2mash.b2b.b2bstrawman.portal;

/**
 * Thrown when a portal-authenticated request reaches a handler that requires PORTAL_CONTACT_ID, but
 * the filter chain did not bind it (e.g. the authenticated customer has no associated PortalContact
 * row, which {@link CustomerAuthFilter} tolerates for backward compatibility).
 *
 * <p>Handled by {@code GlobalExceptionHandler} → HTTP 401 so the portal UI routes the caller back
 * through the login flow rather than surfacing a 500.
 */
public class PortalContactContextNotBoundException extends RuntimeException {

  public PortalContactContextNotBoundException() {
    super("Portal contact context not available — PORTAL_CONTACT_ID not bound by filter chain");
  }
}
