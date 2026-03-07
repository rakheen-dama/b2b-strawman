package io.b2mash.b2b.b2bstrawman.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts org claims from JWT tokens. Supports both Clerk v2 format and Keycloak format.
 *
 * <p>Clerk v2 format: {@code { "o": { "id": "org_xxx", "rol": "owner", "slg": "my-org" } }}
 *
 * <p>Keycloak format (built-in org scope): {@code { "organization": ["org-alias"] }} or rich
 * format: {@code { "organization": { "org-alias": { "id": "uuid", "roles": ["owner"] } } }}
 */
public final class ClerkJwtUtils {

  private static final String CLERK_ORG_CLAIM = "o";
  private static final String KEYCLOAK_ORG_CLAIM = "organization";
  private static final String GROUPS_CLAIM = "groups";

  /** Extracts the org ID from JWT. Tries Clerk format first, then Keycloak. */
  public static String extractOrgId(Jwt jwt) {
    // Clerk v2: o.id
    String clerkId = extractClerkClaim(jwt, "id");
    if (clerkId != null) return clerkId;
    // Keycloak: first alias from organization claim
    return extractKeycloakOrgId(jwt);
  }

  /** Extracts the org role from JWT. Tries Clerk format first, then Keycloak. */
  public static String extractOrgRole(Jwt jwt) {
    // Clerk v2: o.rol
    String clerkRole = extractClerkClaim(jwt, "rol");
    if (clerkRole != null) return clerkRole;
    // Keycloak: check rich format for roles, or default to owner for org members
    return extractKeycloakOrgRole(jwt);
  }

  /** Extracts the org slug from JWT. Tries Clerk format first, then Keycloak. */
  public static String extractOrgSlug(Jwt jwt) {
    // Clerk v2: o.slg
    String clerkSlug = extractClerkClaim(jwt, "slg");
    if (clerkSlug != null) return clerkSlug;
    // Keycloak: the alias IS the slug
    return extractKeycloakOrgId(jwt);
  }

  /**
   * Extracts the groups claim from a JWT. Keycloak JWTs may include a top-level {@code groups}
   * claim (e.g., {@code ["platform-admins"]}). Clerk JWTs do not support groups.
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

  /** Returns true if this JWT uses Clerk format (has "o" claim). */
  public static boolean isClerkJwt(Jwt jwt) {
    return jwt.getClaim(CLERK_ORG_CLAIM) instanceof Map<?, ?>;
  }

  /** Returns true if this JWT uses Keycloak format (has "organization" claim). */
  public static boolean isKeycloakJwt(Jwt jwt) {
    Object claim = jwt.getClaim(KEYCLOAK_ORG_CLAIM);
    return claim instanceof List<?> || claim instanceof Map<?, ?>;
  }

  // ── Clerk extraction ──────────────────────────────────────────────────

  private static String extractClerkClaim(Jwt jwt, String key) {
    Object orgClaim = jwt.getClaim(CLERK_ORG_CLAIM);
    if (orgClaim instanceof Map<?, ?> map) {
      Object value = map.get(key);
      if (value instanceof String str) {
        return str;
      }
    }
    return null;
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
    // List format has no roles — default to owner for org members.
    // In Keycloak JIT mode, the first user creates the org and should be owner.
    // TODO: Implement proper role mapping via Keycloak Script Mapper (ADR-139)
    if (orgClaim instanceof List<?> list && !list.isEmpty()) {
      return Roles.ORG_OWNER;
    }
    return null;
  }

  private ClerkJwtUtils() {}
}
