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
    Map<String, Object> orgClaim = user.getClaim("organization");
    if (orgClaim == null || orgClaim.isEmpty()) {
      return null;
    }
    var entry = orgClaim.entrySet().iterator().next();
    String slug = entry.getKey();
    Map<String, Object> orgData = (Map<String, Object>) entry.getValue();
    String id = (String) orgData.get("id");
    List<String> roles = (List<String>) orgData.get("roles");
    String role = (roles != null && !roles.isEmpty()) ? roles.getFirst() : null;
    return new OrgInfo(slug, id, role);
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
