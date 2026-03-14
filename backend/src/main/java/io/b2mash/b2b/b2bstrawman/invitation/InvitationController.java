package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InvitationRequest request) {
    return ResponseEntity.ok(invitationService.invite(request.email(), request.orgRoleId()));
  }

  @GetMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<InvitationResponse>> list() {
    return ResponseEntity.ok(invitationService.listAll());
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> revoke(@PathVariable UUID id) {
    invitationService.revoke(id);
    return ResponseEntity.noContent().build();
  }
}
