package io.b2mash.b2b.gateway.controller;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  private static final Set<String> ALLOWED_ROLES = Set.of("member", "admin", "owner");

  /** Request body for inviting a member. */
  public record InviteRequest(
      @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
          String email,
      @NotBlank(message = "Role is required") String role) {}

  /** Request body for updating a member's role. */
  public record UpdateRoleRequest(@NotBlank(message = "Role is required") String role) {}

  private final KeycloakAdminClient keycloakAdminClient;
  private final String frontendUrl;

  public AdminProxyController(
      KeycloakAdminClient keycloakAdminClient,
      @Value("${gateway.frontend-url}") String frontendUrl) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.frontendUrl = frontendUrl;
  }

  @PostMapping("/invite")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Map<String, Object>> invite(
      @AuthenticationPrincipal OidcUser user, @Valid @RequestBody InviteRequest request) {
    validateRole(request.role());
    String orgId = resolveOrgId(user);
    keycloakAdminClient.inviteMember(orgId, request.email(), request.role(), frontendUrl);
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

    // Look up the org creator from the organization's custom attributes
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
                  String role =
                      memberId != null && memberId.equals(creatorUserId) ? "owner" : "member";
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

  private void validateRole(String role) {
    if (!ALLOWED_ROLES.contains(role)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Role must be one of: " + ALLOWED_ROLES);
    }
  }
}
