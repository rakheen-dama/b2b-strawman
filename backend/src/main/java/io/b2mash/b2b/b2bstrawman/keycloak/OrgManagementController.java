package io.b2mash.b2b.b2bstrawman.keycloak;

import io.b2mash.b2b.b2bstrawman.keycloak.dto.CreateOrgRequest;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.CreateOrgResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.InvitationResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.InviteRequest;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.UserOrgResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for organization management in Keycloak auth mode. Provides endpoints for org
 * creation, listing, invitations, and cancellation. Conditionally activated only when {@code
 * keycloak.admin.enabled=true}.
 */
@RestController
@RequestMapping("/api/orgs")
@ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true")
public class OrgManagementController {

  private final OrgManagementService orgManagementService;

  public OrgManagementController(OrgManagementService orgManagementService) {
    this.orgManagementService = orgManagementService;
  }

  /**
   * Creates an organization, provisions tenant schema, and adds the authenticated user as owner.
   */
  @PostMapping
  public ResponseEntity<CreateOrgResponse> createOrganization(
      @Valid @RequestBody CreateOrgRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orgManagementService.createOrganization(
                request.name(),
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")));
  }

  /** Lists all organizations the authenticated user belongs to. */
  @GetMapping("/mine")
  public ResponseEntity<List<UserOrgResponse>> listMyOrganizations(
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(orgManagementService.listUserOrganizations(jwt.getSubject()));
  }

  /** Invites a user to an organization by email. */
  @PostMapping("/{id}/invite")
  public ResponseEntity<Void> inviteToOrganization(
      @PathVariable String id, @Valid @RequestBody InviteRequest request) {
    orgManagementService.inviteToOrganization(id, request.email());
    return ResponseEntity.noContent().build();
  }

  /** Lists pending invitations for an organization. */
  @GetMapping("/{id}/invitations")
  public ResponseEntity<List<InvitationResponse>> listInvitations(@PathVariable String id) {
    return ResponseEntity.ok(orgManagementService.listInvitations(id));
  }

  /** Cancels a pending invitation. */
  @DeleteMapping("/{id}/invitations/{invId}")
  public ResponseEntity<Void> cancelInvitation(
      @PathVariable String id, @PathVariable String invId) {
    orgManagementService.cancelInvitation(id, invId);
    return ResponseEntity.noContent().build();
  }
}
