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
  public record OrgInfo(String slug, String id) {}

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
    if (raw instanceof List<?> list) {
      if (list.isEmpty()) return null;
      String alias = (String) list.getFirst();
      return new OrgInfo(alias, alias);
    }

    // Rich format: Map<alias, {id, roles}>
    if (raw instanceof Map<?, ?>) {
      Map<String, Object> orgClaim = (Map<String, Object>) raw;
      if (orgClaim.isEmpty()) return null;
      var entry = orgClaim.entrySet().iterator().next();
      String slug = entry.getKey();
      Map<String, Object> orgData = (Map<String, Object>) entry.getValue();
      String id = (String) orgData.getOrDefault("id", slug);
      return new OrgInfo(slug, id);
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
}
