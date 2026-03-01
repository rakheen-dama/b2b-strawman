package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.proposal.PortalProposalService.PortalAcceptResponse;
import io.b2mash.b2b.b2bstrawman.proposal.PortalProposalService.PortalDeclineResponse;
import io.b2mash.b2b.b2bstrawman.proposal.PortalProposalService.PortalProposalDetail;
import io.b2mash.b2b.b2bstrawman.proposal.PortalProposalService.PortalProposalSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal-facing REST controller for proposal viewing, acceptance, and decline. Authenticated via
 * {@code CustomerAuthFilter} which binds {@code CUSTOMER_ID}, {@code PORTAL_CONTACT_ID}, {@code
 * TENANT_ID}, and {@code ORG_ID} from the portal JWT.
 */
@RestController
@RequestMapping("/portal/api/proposals")
public class PortalProposalController {

  private final PortalProposalService portalProposalService;

  public PortalProposalController(PortalProposalService portalProposalService) {
    this.portalProposalService = portalProposalService;
  }

  @GetMapping
  public ResponseEntity<List<PortalProposalSummary>> listProposals() {
    var result =
        portalProposalService.listProposals(
            RequestScopes.requireCustomerId(), RequestScopes.requirePortalContactId());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PortalProposalDetail> getProposalDetail(@PathVariable UUID id) {
    var result = portalProposalService.getProposalDetail(id, RequestScopes.requireCustomerId());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/{id}/accept")
  public ResponseEntity<PortalAcceptResponse> acceptProposal(@PathVariable UUID id) {
    var result =
        portalProposalService.acceptProposal(
            id, RequestScopes.requireCustomerId(), RequestScopes.requirePortalContactId());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/{id}/decline")
  public ResponseEntity<PortalDeclineResponse> declineProposal(
      @PathVariable UUID id, @RequestBody(required = false) DeclineRequest request) {
    String reason = request != null ? request.reason() : null;
    var result =
        portalProposalService.declineProposal(
            id, RequestScopes.requireCustomerId(), RequestScopes.requirePortalContactId(), reason);
    return ResponseEntity.ok(result);
  }

  public record DeclineRequest(String reason) {}
}
