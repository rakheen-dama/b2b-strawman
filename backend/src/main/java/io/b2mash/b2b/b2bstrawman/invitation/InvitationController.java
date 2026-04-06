package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.CreateInvitationRequest;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.InvitationListResponse;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.PendingInvitationResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

  private final InvitationService invitationService;

  public InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<PendingInvitationResponse> createInvitation(
      @Valid @RequestBody CreateInvitationRequest request) {
    var response = invitationService.createInvitation(request, RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/invitations/" + response.id())).body(response);
  }

  @GetMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<InvitationListResponse> listInvitations(
      @RequestParam(required = false) InvitationStatus status) {
    return ResponseEntity.ok(invitationService.listInvitations(status));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> revokeInvitation(@PathVariable UUID id) {
    invitationService.revokeInvitation(id);
    return ResponseEntity.noContent().build();
  }
}
