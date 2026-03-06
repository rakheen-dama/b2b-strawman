package io.b2mash.b2b.gateway.controller;

import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF endpoint that exposes the current user's identity and organization info from the session. */
@RestController
@RequestMapping("/bff")
public class BffController {

  /** Response DTO for the /bff/me endpoint. */
  public record BffUserInfo(
      boolean authenticated,
      String userId,
      String email,
      String name,
      String picture,
      String orgId,
      String orgSlug,
      String orgRole) {

    /** Factory for unauthenticated response. */
    public static BffUserInfo unauthenticated() {
      return new BffUserInfo(false, null, null, null, null, null, null, null);
    }
  }

  @GetMapping("/me")
  public ResponseEntity<BffUserInfo> me(@AuthenticationPrincipal OidcUser user) {
    if (user == null) {
      return ResponseEntity.ok(BffUserInfo.unauthenticated());
    }

    org.slf4j.LoggerFactory.getLogger(BffController.class)
        .info("BFF /me claims: {}", user.getClaims());
    BffUserInfoExtractor.OrgInfo orgInfo = BffUserInfoExtractor.extractOrgInfo(user);

    return ResponseEntity.ok(
        new BffUserInfo(
            true,
            user.getSubject(),
            user.getEmail(),
            user.getFullName(),
            Objects.toString(user.getPicture(), ""),
            orgInfo != null ? orgInfo.id() : null,
            orgInfo != null ? orgInfo.slug() : null,
            orgInfo != null ? orgInfo.role() : null));
  }
}
