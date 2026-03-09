package io.b2mash.b2b.gateway.controller;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF endpoint that exposes the current user's identity and organization info from the session. */
@RestController
@RequestMapping("/bff")
public class BffController {

  private static final Logger log = LoggerFactory.getLogger(BffController.class);

  private final KeycloakAdminClient keycloakAdmin;
  private final boolean selfServiceOrgCreationEnabled;

  public BffController(
      KeycloakAdminClient keycloakAdmin,
      @Value("${app.self-service-org-creation.enabled:false}")
          boolean selfServiceOrgCreationEnabled) {
    this.keycloakAdmin = keycloakAdmin;
    this.selfServiceOrgCreationEnabled = selfServiceOrgCreationEnabled;
  }

  /** Response DTO for the /bff/me endpoint. */
  public record BffUserInfo(
      boolean authenticated,
      String userId,
      String email,
      String name,
      String picture,
      String orgId,
      String orgSlug,
      String orgRole,
      List<String> groups) {

    /** Factory for unauthenticated response. */
    public static BffUserInfo unauthenticated() {
      return new BffUserInfo(false, null, null, null, null, null, null, null, List.of());
    }
  }

  /** Request DTO for creating an organization. */
  public record CreateOrgRequest(String name) {}

  /** Response DTO after creating an organization. */
  public record CreateOrgResponse(String orgId, String slug) {}

  /** Returns the current CSRF token so the SPA can perform form POSTs (e.g., logout). */
  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken == null) {
      return ResponseEntity.ok(Map.of());
    }
    // Force lazy token generation
    String token = csrfToken.getToken();
    return ResponseEntity.ok(
        Map.of(
            "token", token,
            "parameterName", csrfToken.getParameterName(),
            "headerName", csrfToken.getHeaderName()));
  }

  @GetMapping("/me")
  public ResponseEntity<BffUserInfo> me(@AuthenticationPrincipal OidcUser user) {
    if (user == null) {
      return ResponseEntity.ok(BffUserInfo.unauthenticated());
    }

    log.info("BFF /me claims: {}", user.getClaims());
    BffUserInfoExtractor.OrgInfo orgInfo = BffUserInfoExtractor.extractOrgInfo(user);
    List<String> groups = extractGroups(user);

    return ResponseEntity.ok(
        new BffUserInfo(
            true,
            user.getSubject(),
            user.getEmail(),
            user.getFullName(),
            Objects.toString(user.getPicture(), ""),
            orgInfo != null ? orgInfo.id() : null,
            orgInfo != null ? orgInfo.slug() : null,
            orgInfo != null ? orgInfo.role() : null,
            groups));
  }

  /**
   * Creates an organization in Keycloak and adds the current user as a member. After this call, the
   * frontend should redirect to /oauth2/authorization/keycloak to refresh the session with updated
   * org claims.
   */
  @PostMapping("/orgs")
  public ResponseEntity<CreateOrgResponse> createOrg(
      @AuthenticationPrincipal OidcUser user, @RequestBody CreateOrgRequest request) {
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (request.name() == null || request.name().isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    if (!selfServiceOrgCreationEnabled) {
      List<String> groups = extractGroups(user);
      if (!groups.contains("platform-admins")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    String slug = toSlug(request.name());
    log.info("Creating org '{}' (slug: {}) for user {}", request.name(), slug, user.getSubject());

    // Create organization in Keycloak (stores creator's user ID as an org attribute)
    String orgId = keycloakAdmin.createOrganization(request.name(), slug, user.getSubject());
    if (orgId == null) {
      // Fallback: look up by alias
      var org = keycloakAdmin.findOrganizationByAlias(slug);
      orgId = org != null ? (String) org.get("id") : null;
    }
    if (orgId == null) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    // Add current user to the organization
    keycloakAdmin.addMember(orgId, user.getSubject());

    // Assign owner role — best-effort; if it fails, the list-format fallback in
    // BffUserInfoExtractor defaults to owner for the org creator's session.
    try {
      keycloakAdmin.updateMemberRole(orgId, user.getSubject(), "owner");
      log.info("Added user {} as owner to org {} ({})", user.getSubject(), orgId, slug);
    } catch (Exception e) {
      log.warn(
          "Failed to assign owner role to user {} in org {} — will use fallback: {}",
          user.getSubject(),
          orgId,
          e.getMessage());
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(new CreateOrgResponse(orgId, slug));
  }

  /** Extracts the groups claim from the OidcUser, defaulting to an empty list. */
  private static List<String> extractGroups(OidcUser user) {
    Object raw = user.getClaim("groups");
    if (raw instanceof List<?> list) {
      return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    return List.of();
  }

  /** Converts an org name to a URL-safe slug. */
  private static String toSlug(String name) {
    return name.toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
