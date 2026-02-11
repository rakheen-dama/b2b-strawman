package io.b2mash.b2b.b2bstrawman.multitenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.security.ClerkJwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

  private final OrgSchemaMappingRepository mappingRepository;
  private final Cache<String, TenantInfo> tenantCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(1)).build();

  public TenantFilter(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  /** Evicts the cached tenant info for the given Clerk org ID. */
  public void evictSchema(String clerkOrgId) {
    tenantCache.invalidate(clerkOrgId);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String orgId = ClerkJwtUtils.extractOrgId(jwt);

      if (orgId != null) {
        TenantInfo info = resolveTenant(orgId);
        if (info != null) {
          ScopedFilterChain.runScoped(
              ScopedValue.where(RequestScopes.TENANT_ID, info.schemaName())
                  .where(RequestScopes.ORG_ID, orgId),
              filterChain,
              request,
              response);
          return;
        } else {
          response.sendError(HttpServletResponse.SC_FORBIDDEN, "Organization not provisioned");
          return;
        }
      }
    }

    // No JWT or no org claim — continue unbound (actuator, unauthenticated paths)
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/")
        || path.startsWith("/actuator/")
        || path.startsWith("/portal/");
  }

  private TenantInfo resolveTenant(String clerkOrgId) {
    // Caffeine's cache.get(key, loader) throws NPE if loader returns null.
    // Use getIfPresent + manual put to handle unprovisioned orgs gracefully.
    TenantInfo cached = tenantCache.getIfPresent(clerkOrgId);
    if (cached != null) {
      return cached;
    }
    TenantInfo info = lookupTenant(clerkOrgId);
    if (info != null) {
      tenantCache.put(clerkOrgId, info);
    }
    return info;
  }

  private TenantInfo lookupTenant(String clerkOrgId) {
    return mappingRepository.findTenantInfoByClerkOrgId(clerkOrgId).orElse(null);
  }
}
