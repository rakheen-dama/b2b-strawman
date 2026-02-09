package io.b2mash.b2b.b2bstrawman.security;

import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts Clerk JWT v2 org claims from the nested "o" object.
 *
 * <p>Clerk v2 format: {@code { "o": { "id": "org_xxx", "rol": "owner", "slg": "my-org" } }}
 */
public final class ClerkJwtUtils {

  private static final String ORG_CLAIM = "o";

  /** Extracts the org ID ({@code o.id}) from a Clerk JWT v2 token. */
  public static String extractOrgId(Jwt jwt) {
    return extractNestedClaim(jwt, "id");
  }

  /** Extracts the org role ({@code o.rol}) from a Clerk JWT v2 token. */
  public static String extractOrgRole(Jwt jwt) {
    return extractNestedClaim(jwt, "rol");
  }

  private static String extractNestedClaim(Jwt jwt, String key) {
    Object orgClaim = jwt.getClaim(ORG_CLAIM);
    if (orgClaim instanceof Map<?, ?> map) {
      Object value = map.get(key);
      if (value instanceof String str) {
        return str;
      }
    }
    return null;
  }

  private ClerkJwtUtils() {}
}
