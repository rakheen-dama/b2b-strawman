package io.b2mash.b2b.gateway.controller;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  public AdminProxyController(KeycloakAdminClient keycloakAdminClient) {
    this.keycloakAdminClient = keycloakAdminClient;
  }

  @PostMapping("/invite")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Map<String, Object>> invite(
      @AuthenticationPrincipal OidcUser user, @Valid @RequestBody InviteRequest request) {
    validateRole(request.role());
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    return ResponseEntity.ok(
        keycloakAdminClient.inviteMember(orgId, request.email(), request.role()));
  }

  @GetMapping("/invitations")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<List<Map<String, Object>>> listInvitations(
      @AuthenticationPrincipal OidcUser user) {
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    return ResponseEntity.ok(keycloakAdminClient.listInvitations(orgId));
  }

  @DeleteMapping("/invitations/{id}")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Void> revokeInvitation(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Invalid invitation ID")
          String id) {
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    keycloakAdminClient.revokeInvitation(orgId, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/members")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<List<Map<String, Object>>> listMembers(
      @AuthenticationPrincipal OidcUser user) {
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    return ResponseEntity.ok(keycloakAdminClient.listOrgMembers(orgId));
  }

  @PatchMapping("/members/{id}/role")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Void> updateMemberRole(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Invalid member ID") String id,
      @Valid @RequestBody UpdateRoleRequest request) {
    validateRole(request.role());
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    keycloakAdminClient.updateMemberRole(orgId, id, request.role());
    return ResponseEntity.noContent().build();
  }

  private void validateRole(String role) {
    if (!ALLOWED_ROLES.contains(role)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Role must be one of: " + ALLOWED_ROLES);
    }
  }
}
