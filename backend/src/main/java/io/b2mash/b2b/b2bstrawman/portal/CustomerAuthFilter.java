package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.ScopedFilterChain;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentication filter for portal requests ({@code /portal/**}). Extracts the Bearer token,
 * verifies it as a portal JWT via {@link PortalJwtService}, and binds {@link
 * RequestScopes#CUSTOMER_ID}, {@link RequestScopes#TENANT_ID}, and {@link RequestScopes#ORG_ID}.
 *
 * <p>Unauthenticated portal paths (e.g., {@code /portal/auth/**}) are excluded via {@link
 * #shouldNotFilter}.
 */
@Component
public class CustomerAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(CustomerAuthFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final PortalJwtService portalJwtService;
  private final OrgSchemaMappingRepository mappingRepository;

  public CustomerAuthFilter(
      PortalJwtService portalJwtService, OrgSchemaMappingRepository mappingRepository) {
    this.portalJwtService = portalJwtService;
    this.mappingRepository = mappingRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization");
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length());
    PortalJwtService.PortalClaims claims;
    try {
      claims = portalJwtService.verifyToken(token);
    } catch (PortalAuthException e) {
      log.debug("Portal auth failed: {}", e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }

    // Resolve tenant from org ID
    TenantInfo tenantInfo =
        mappingRepository.findTenantInfoByClerkOrgId(claims.clerkOrgId()).orElse(null);
    if (tenantInfo == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Organization not provisioned");
      return;
    }

    // Bind scoped values: CUSTOMER_ID, TENANT_ID, ORG_ID
    var carrier =
        ScopedValue.where(RequestScopes.CUSTOMER_ID, claims.customerId())
            .where(RequestScopes.TENANT_ID, tenantInfo.schemaName())
            .where(RequestScopes.ORG_ID, claims.clerkOrgId());

    ScopedFilterChain.runScoped(carrier, filterChain, request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // Only filter authenticated portal paths, not the auth endpoints themselves
    if (!path.startsWith("/portal/")) {
      return true;
    }
    // Allow unauthenticated access to /portal/auth/** (magic link request/verify)
    return path.startsWith("/portal/auth/");
  }
}
