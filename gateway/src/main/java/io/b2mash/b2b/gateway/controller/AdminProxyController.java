package io.b2mash.b2b.gateway.controller;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin proxy endpoints that relay team management operations to the Keycloak Admin REST API.
 * Requires ADMIN or OWNER org role.
 */
@RestController
@RequestMapping("/bff/admin")
public class AdminProxyController {

  private static final Logger log = LoggerFactory.getLogger(AdminProxyController.class);
  private static final Set<String> ALLOWED_ROLES = Set.of("member", "admin", "owner");

  /** Request body for inviting a member. */
  public record InviteRequest(
      @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
          String email,
      @NotBlank(message = "Role is required") String role) {}

  /** Request body for updating a member's role. */
  public record UpdateRoleRequest(@NotBlank(message = "Role is required") String role) {}

  private final KeycloakAdminClient keycloakAdminClient;
  private final JdbcTemplate jdbcTemplate;
  private final String frontendUrl;

  public AdminProxyController(
      KeycloakAdminClient keycloakAdminClient,
      JdbcTemplate jdbcTemplate,
      @Value("${gateway.frontend-url}") String frontendUrl) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.jdbcTemplate = jdbcTemplate;
    this.frontendUrl = frontendUrl;
  }

  @PostMapping("/invite")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Map<String, Object>> invite(
      @AuthenticationPrincipal OidcUser user, @Valid @RequestBody InviteRequest request) {
    validateRole(request.role());
    String orgId = resolveOrgId(user);
    keycloakAdminClient.inviteMember(orgId, request.email(), request.role(), frontendUrl);
    savePendingInvitation(user, request.email(), request.role());
    return ResponseEntity.ok(Map.of("email", request.email(), "role", request.role()));
  }

  @GetMapping("/invitations")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<List<Map<String, Object>>> listInvitations(
      @AuthenticationPrincipal OidcUser user) {
    String orgId = resolveOrgId(user);
    return ResponseEntity.ok(keycloakAdminClient.listInvitations(orgId));
  }

  @DeleteMapping("/invitations/{id}")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Void> revokeInvitation(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Invalid invitation ID")
          String id) {
    String orgId = resolveOrgId(user);
    keycloakAdminClient.revokeInvitation(orgId, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/members")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<List<Map<String, Object>>> listMembers(
      @AuthenticationPrincipal OidcUser user) {
    String orgId = resolveOrgId(user);
    List<Map<String, Object>> raw = keycloakAdminClient.listOrgMembers(orgId);

    // Look up the org creator as fallback for members without org_role attribute
    String creatorUserId = extractCreatorUserId(orgId);

    // Transform Keycloak user representation to BffMember shape
    List<Map<String, Object>> members =
        raw.stream()
            .map(
                m -> {
                  String firstName = stringOrEmpty(m.get("firstName"));
                  String lastName = stringOrEmpty(m.get("lastName"));
                  String name = (firstName + " " + lastName).trim();
                  String memberId = (String) m.get("id");
                  String role = extractMemberRole(m, memberId, creatorUserId);
                  return Map.<String, Object>of(
                      "id",
                      m.getOrDefault("id", ""),
                      "email",
                      m.getOrDefault("email", ""),
                      "name",
                      name.isEmpty() ? stringOrEmpty(m.get("username")) : name,
                      "role",
                      role);
                })
            .toList();
    return ResponseEntity.ok(members);
  }

  private static String stringOrEmpty(Object obj) {
    return obj instanceof String s ? s : "";
  }

  @PatchMapping("/members/{id}/role")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Void> updateMemberRole(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Invalid member ID") String id,
      @Valid @RequestBody UpdateRoleRequest request) {
    validateRole(request.role());
    String orgId = resolveOrgId(user);
    keycloakAdminClient.updateMemberRole(orgId, id, request.role());
    return ResponseEntity.noContent().build();
  }

  /** Resolves the org alias from OIDC claims to a Keycloak org UUID. */
  private String resolveOrgId(OidcUser user) {
    String aliasOrId = BffUserInfoExtractor.extractOrgId(user);
    return keycloakAdminClient.resolveOrgId(aliasOrId);
  }

  /**
   * Extracts the creator's user ID from the organization's custom attributes. Returns null if not
   * set (e.g. orgs created before this attribute was added).
   */
  @SuppressWarnings("unchecked")
  private String extractCreatorUserId(String orgId) {
    try {
      Map<String, Object> org = keycloakAdminClient.getOrganization(orgId);
      if (org != null && org.get("attributes") instanceof Map<?, ?> attrs) {
        Object creatorAttr = attrs.get("creatorUserId");
        if (creatorAttr instanceof List<?> list && !list.isEmpty()) {
          return (String) list.getFirst();
        }
      }
    } catch (Exception e) {
      // Fall through — org attributes unavailable
    }
    return null;
  }

  /**
   * Extracts a member's role from their Keycloak user attributes. Falls back to creatorUserId check
   * for legacy orgs where the org_role attribute was never set.
   */
  private String extractMemberRole(
      Map<String, Object> member, String memberId, String creatorUserId) {
    if (member.get("attributes") instanceof Map<?, ?> attrs) {
      Object orgRoleAttr = attrs.get("org_role");
      if (orgRoleAttr instanceof List<?> list && !list.isEmpty()) {
        return (String) list.getFirst();
      }
    }
    // Fallback: org creator is owner (for orgs created before org_role attribute existed)
    if (memberId != null && memberId.equals(creatorUserId)) {
      return "owner";
    }
    return "member";
  }

  /**
   * Persists the intended role so MemberFilter can assign it when the user first logs in. Best
   * effort — if the DB is unavailable, the invitation still proceeds (role defaults to member).
   */
  private void savePendingInvitation(OidcUser user, String email, String role) {
    String orgSlug = BffUserInfoExtractor.extractOrgSlug(user);
    if (orgSlug == null) {
      log.warn("Cannot save pending invitation — org slug not found in claims");
      return;
    }
    try {
      jdbcTemplate.update(
          "DELETE FROM pending_invitations WHERE org_slug = ? AND lower(email) = lower(?)",
          orgSlug,
          email);
      jdbcTemplate.update(
          "INSERT INTO pending_invitations (org_slug, email, role) VALUES (?, ?, ?)",
          orgSlug,
          email,
          role);
    } catch (Exception e) {
      log.warn("Could not save pending invitation for {}: {}", email, e.getMessage());
    }
  }

  private void validateRole(String role) {
    if (!ALLOWED_ROLES.contains(role)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Role must be one of: " + ALLOWED_ROLES);
    }
  }
}
