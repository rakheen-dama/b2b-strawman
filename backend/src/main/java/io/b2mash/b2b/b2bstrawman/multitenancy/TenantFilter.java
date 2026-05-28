package io.b2mash.b2b.b2bstrawman.multitenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final ObjectProvider<TenantProvisioningService> provisioningService;
  private final boolean jitProvisioningEnabled;
  private final Cache<String, TenantMapping> tenantCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(1)).build();

  public TenantFilter(
      OrgSchemaMappingRepository mappingRepository,
      ObjectProvider<TenantProvisioningService> provisioningService,
      @Value("${app.jit-provisioning.enabled:false}") boolean jitProvisioningEnabled) {
    this.mappingRepository = mappingRepository;
    this.provisioningService = provisioningService;
    this.jitProvisioningEnabled = jitProvisioningEnabled;
  }

  /** Evicts the cached schema name for the given Clerk org ID. */
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
      // JwtUtils.extractOrgId handles both Clerk v2 and Keycloak formats
      String orgId = JwtUtils.extractOrgId(jwt);

      if (orgId != null) {
        TenantMapping mapping = resolveTenant(orgId);
        if (mapping != null) {
          ScopedFilterChain.runScoped(buildCarrier(mapping, orgId), filterChain, request, response);
          return;
        } else {
          // Schema not found — attempt JIT provisioning if enabled and user is admin/owner
          TenantProvisioningService svc =
              jitProvisioningEnabled ? provisioningService.getIfAvailable() : null;
          if (svc != null) {
            // Any authenticated user can trigger JIT provisioning — the first user
            // gets promoted to owner by MemberFilter (founding user logic)
            mapping = attemptJitProvisioning(jwt, orgId, svc);
          }
          if (mapping != null) {
            ScopedFilterChain.runScoped(
                buildCarrier(mapping, orgId), filterChain, request, response);
            return;
          }
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

  private static ScopedValue.Carrier buildCarrier(TenantMapping mapping, String orgId) {
    ScopedValue.Carrier carrier =
        ScopedValue.where(RequestScopes.TENANT_ID, mapping.schemaName())
            .where(RequestScopes.ORG_ID, orgId);
    if (mapping.shardId() != null && !mapping.shardId().isBlank()) {
      carrier = carrier.where(RequestScopes.SHARD_ID, mapping.shardId());
    }
    return carrier;
  }

  private TenantMapping resolveTenant(String clerkOrgId) {
    // Caffeine's cache.get(key, loader) throws NPE if loader returns null.
    // Use getIfPresent + manual put to handle unprovisioned orgs gracefully.
    TenantMapping cached = tenantCache.getIfPresent(clerkOrgId);
    if (cached != null) {
      return cached;
    }
    TenantMapping mapping = lookupTenant(clerkOrgId);
    if (mapping != null) {
      tenantCache.put(clerkOrgId, mapping);
    }
    return mapping;
  }

  private TenantMapping lookupTenant(String clerkOrgId) {
    return mappingRepository
        .findByClerkOrgId(clerkOrgId)
        .map(m -> new TenantMapping(m.getSchemaName(), m.getExternalOrgId(), m.getShardId()))
        .orElse(null);
  }

  private TenantMapping attemptJitProvisioning(
      Jwt jwt, String orgId, TenantProvisioningService svc) {
    String orgSlug = JwtUtils.extractOrgSlug(jwt);
    String orgName = orgSlug != null ? orgSlug : orgId;
    try {
      log.info("JIT provisioning tenant for org {}", orgId);
      svc.provisionTenant(orgId, orgName, null);
      // Provisioning succeeded — retry lookup (bypasses cache)
      TenantMapping mapping = lookupTenant(orgId);
      if (mapping == null) {
        log.error("JIT provisioning completed but schema mapping not found for org {}", orgId);
      } else {
        tenantCache.put(orgId, mapping);
      }
      return mapping;
    } catch (Exception e) {
      // Could be a concurrent provisioning race — retry lookup
      log.warn("JIT provisioning failed for org {}, retrying lookup: {}", orgId, e.getMessage());
      TenantMapping mapping = lookupTenant(orgId);
      if (mapping != null) {
        tenantCache.put(orgId, mapping);
        return mapping;
      }
      log.error("JIT provisioning and retry lookup both failed for org {}", orgId, e);
      return null;
    }
  }
}
