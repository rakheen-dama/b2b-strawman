package io.b2mash.b2b.gateway.controller;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import java.util.List;
import java.util.Map;
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

/**
 * Admin proxy endpoints that relay team management operations to the Keycloak Admin REST API.
 * Requires ADMIN or OWNER org role.
 */
@RestController
@RequestMapping("/bff/admin")
public class AdminProxyController {

  /** Request body for inviting a member. */
  public record InviteRequest(String email, String role) {}

  /** Request body for updating a member's role. */
  public record UpdateRoleRequest(String role) {}

  private final KeycloakAdminClient keycloakAdminClient;

  public AdminProxyController(KeycloakAdminClient keycloakAdminClient) {
    this.keycloakAdminClient = keycloakAdminClient;
  }

  @PostMapping("/invite")
  @PreAuthorize("@bffSecurity.isAdmin(#user)")
  public ResponseEntity<Map<String, Object>> invite(
      @AuthenticationPrincipal OidcUser user, @RequestBody InviteRequest request) {
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
      @AuthenticationPrincipal OidcUser user, @PathVariable String id) {
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
      @PathVariable String id,
      @RequestBody UpdateRoleRequest request) {
    String orgId = BffUserInfoExtractor.extractOrgId(user);
    keycloakAdminClient.updateMemberRole(orgId, id, request.role());
    return ResponseEntity.noContent().build();
  }
}
