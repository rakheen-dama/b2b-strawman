package io.b2mash.b2b.b2bstrawman.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts org claims from Keycloak JWT tokens.
 *
 * <p>Keycloak format (built-in org scope): {@code { "organization": ["org-alias"] }} or rich
 * format: {@code { "organization": { "org-alias": { "id": "uuid", "roles": ["owner"] } } }}
 */
public final class JwtUtils {

  private static final String KEYCLOAK_ORG_CLAIM = "organization";
  private static final String GROUPS_CLAIM = "groups";

  /** Extracts the org ID from JWT (Keycloak format). */
  public static String extractOrgId(Jwt jwt) {
    return extractKeycloakOrgId(jwt);
  }

  /** Extracts the org role from JWT (Keycloak format). */
  public static String extractOrgRole(Jwt jwt) {
    return extractKeycloakOrgRole(jwt);
  }

  /** Extracts the org slug from JWT (Keycloak format — the alias IS the slug). */
  public static String extractOrgSlug(Jwt jwt) {
    return extractKeycloakOrgId(jwt);
  }

  /**
   * Extracts the groups claim from a JWT. Keycloak JWTs may include a top-level {@code groups}
   * claim (e.g., {@code ["platform-admins"]}).
   *
   * @return the set of groups, never null
   */
  @SuppressWarnings("unchecked")
  public static Set<String> extractGroups(Jwt jwt) {
    Object groupsClaim = jwt.getClaim(GROUPS_CLAIM);
    if (groupsClaim instanceof List<?> list && !list.isEmpty()) {
      return Collections.unmodifiableSet(new LinkedHashSet<>((List<String>) list));
    }
    return Collections.emptySet();
  }

  /** Returns true if this JWT uses Keycloak format (has "organization" claim). */
  public static boolean isKeycloakJwt(Jwt jwt) {
    Object claim = jwt.getClaim(KEYCLOAK_ORG_CLAIM);
    return claim instanceof List<?> || claim instanceof Map<?, ?>;
  }

  /**
   * Returns true if the JWT is Keycloak format with a flat list (no inline roles). This means the
   * role came from a default, not from the token itself.
   */
  public static boolean isKeycloakFlatListFormat(Jwt jwt) {
    Object orgClaim = jwt.getClaim(KEYCLOAK_ORG_CLAIM);
    return orgClaim instanceof List<?>;
  }

  // ── Keycloak extraction ───────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static String extractKeycloakOrgId(Jwt jwt) {
    Object orgClaim = jwt.getClaim(KEYCLOAK_ORG_CLAIM);
    // List<String> format: ["org-alias"]
    if (orgClaim instanceof List<?> list && !list.isEmpty()) {
      return (String) list.getFirst();
    }
    // Rich map format: { "org-alias": { "id": "...", "roles": [...] } }
    if (orgClaim instanceof Map<?, ?> map && !map.isEmpty()) {
      return (String) map.keySet().iterator().next();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static String extractKeycloakOrgRole(Jwt jwt) {
    Object orgClaim = jwt.getClaim(KEYCLOAK_ORG_CLAIM);
    // Rich map format with roles
    if (orgClaim instanceof Map<?, ?> map && !map.isEmpty()) {
      var entry = map.values().iterator().next();
      if (entry instanceof Map<?, ?> orgData) {
        Object roles = orgData.get("roles");
        if (roles instanceof List<?> roleList && !roleList.isEmpty()) {
          String role = (String) roleList.getFirst();
          // Normalize: "org:owner" -> "owner"
          return role.startsWith("org:") ? role.substring(4) : role;
        }
      }
    }
    // Check for org_role claim (user attribute mapper workaround for KC 26.x)
    String orgRoleClaim = jwt.getClaimAsString("org_role");
    if (orgRoleClaim != null && !orgRoleClaim.isBlank()) {
      return orgRoleClaim.startsWith("org:") ? orgRoleClaim.substring(4) : orgRoleClaim;
    }
    // List format has no roles — default to member.
    // The first user to log into a newly-provisioned tenant gets promoted to owner
    // by MemberFilter (see lazyCreateMember).
    if (orgClaim instanceof List<?> list && !list.isEmpty()) {
      return Roles.ORG_MEMBER;
    }
    return null;
  }

  private JwtUtils() {}
}
