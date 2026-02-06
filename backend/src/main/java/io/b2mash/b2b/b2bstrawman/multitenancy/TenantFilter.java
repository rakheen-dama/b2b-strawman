package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

  private final OrgSchemaMappingRepository mappingRepository;
  private final Map<String, String> schemaCache = new ConcurrentHashMap<>();

  public TenantFilter(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (authentication instanceof JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        String orgId = jwt.getClaimAsString("org_id");

        if (orgId != null) {
          String schemaName = resolveSchema(orgId);
          if (schemaName != null) {
            TenantContext.setTenantId(schemaName);
          } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Organization not provisioned");
            return;
          }
        }
      }

      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/") || path.startsWith("/actuator/");
  }

  private String resolveSchema(String clerkOrgId) {
    return schemaCache.computeIfAbsent(clerkOrgId, this::lookupSchema);
  }

  private String lookupSchema(String clerkOrgId) {
    return mappingRepository
        .findByClerkOrgId(clerkOrgId)
        .map(OrgSchemaMapping::getSchemaName)
        .orElse(null);
  }
}
