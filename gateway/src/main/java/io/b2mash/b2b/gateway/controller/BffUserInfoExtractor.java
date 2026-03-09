package io.b2mash.b2b.gateway.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Utility for extracting organization information from OidcUser claims. Assumes single-org
 * membership: extracts the first entry from the {@code organization} claim map.
 */
public final class BffUserInfoExtractor {

  /** Organization info extracted from OidcUser claims. */
  public record OrgInfo(String slug, String id, String role) {}

  private BffUserInfoExtractor() {}

  /**
   * Extracts organization info from the OidcUser's {@code organization} claim.
   *
   * @return OrgInfo or null if the claim is missing or empty
   */
  @SuppressWarnings("unchecked")
  public static OrgInfo extractOrgInfo(OidcUser user) {
    if (user == null) {
      return null;
    }
    Object raw = user.getClaim("organization");
    if (raw == null) {
      return null;
    }

    // Keycloak 26.x built-in org scope emits a List<String> of org aliases
    // (no roles in this format). Check the separate org_role claim (user attribute mapper)
    // before defaulting to member. The backend DB lookup is the ultimate fallback.
    if (raw instanceof List<?> list) {
      if (list.isEmpty()) return null;
      String alias = (String) list.getFirst();
      // org_role claim is set by oidc-usermodel-attribute-mapper (KC 26.x workaround)
      Object orgRoleClaim = user.getClaim("org_role");
      String role =
          (orgRoleClaim instanceof String r && !r.isBlank())
              ? (r.startsWith("org:") ? r.substring(4) : r)
              : "member";
      return new OrgInfo(alias, alias, role);
    }

    // Rich format: Map<alias, {id, roles}>
    if (raw instanceof Map<?, ?>) {
      Map<String, Object> orgClaim = (Map<String, Object>) raw;
      if (orgClaim.isEmpty()) return null;
      var entry = orgClaim.entrySet().iterator().next();
      String slug = entry.getKey();
      Map<String, Object> orgData = (Map<String, Object>) entry.getValue();
      String id = (String) orgData.getOrDefault("id", slug);
      List<String> roles = (List<String>) orgData.get("roles");
      String role = (roles != null && !roles.isEmpty()) ? roles.getFirst() : "member";
      return new OrgInfo(slug, id, role);
    }

    return null;
  }

  /** Extracts the organization slug from OidcUser claims, or null if absent. */
  public static String extractOrgSlug(OidcUser user) {
    OrgInfo info = extractOrgInfo(user);
    return info != null ? info.slug() : null;
  }

  /** Extracts the organization ID from OidcUser claims, or null if absent. */
  public static String extractOrgId(OidcUser user) {
    OrgInfo info = extractOrgInfo(user);
    return info != null ? info.id() : null;
  }

  /** Extracts the organization role from OidcUser claims, or null if absent. */
  public static String extractOrgRole(OidcUser user) {
    OrgInfo info = extractOrgInfo(user);
    return info != null ? info.role() : null;
  }
}
