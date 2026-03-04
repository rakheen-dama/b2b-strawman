package io.b2mash.b2b.gateway.controller;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Security expression helper for BFF admin endpoints. Used via {@code @PreAuthorize}. */
@Component("bffSecurity")
public class BffSecurity {

  /** Returns true if the user has an admin or owner org role in their OIDC claims. */
  public boolean isAdmin(OidcUser user) {
    if (user == null) {
      return false;
    }
    String role = BffUserInfoExtractor.extractOrgRole(user);
    return "admin".equals(role) || "owner".equals(role);
  }
}
