package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal-facing service for proposal operations. Encapsulates all portal read-model queries and
 * tenant-context resolution for accept/decline flows. The controller delegates all logic here.
 */
@Service
public class PortalProposalService {

  private static final Logger log = LoggerFactory.getLogger(PortalProposalService.class);

  private final JdbcClient portalJdbc;
  private final ProposalOrchestrationService orchestrationService;
  private final ProposalService proposalService;

  public PortalProposalService(
      @Qualifier("portalJdbcClient") JdbcClient portalJdbc,
      ProposalOrchestrationService orchestrationService,
      ProposalService proposalService) {
    this.portalJdbc = portalJdbc;
    this.orchestrationService = orchestrationService;
    this.proposalService = proposalService;
  }

  /** Lists proposals visible to the authenticated portal contact's customer. */
  public List<PortalProposalSummary> listProposals(UUID customerId, UUID portalContactId) {
    return portalJdbc
        .sql(
            """
            SELECT id, proposal_number, title, status, fee_model, fee_amount, fee_currency, sent_at
            FROM portal.portal_proposals
            WHERE customer_id = ? AND portal_contact_id = ?
              AND status IN ('SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED')
            ORDER BY sent_at DESC
            """)
        .params(customerId, portalContactId)
        .query(
            (rs, rowNum) -> {
              var sentAtTs = rs.getTimestamp("sent_at");
              return new PortalProposalSummary(
                  rs.getObject("id", UUID.class),
                  rs.getString("proposal_number"),
                  rs.getString("title"),
                  rs.getString("status"),
                  rs.getString("fee_model"),
                  rs.getBigDecimal("fee_amount"),
                  rs.getString("fee_currency"),
                  sentAtTs != null ? sentAtTs.toInstant() : null);
            })
        .list();
  }

  /** Gets proposal detail for portal display. */
  public PortalProposalDetail getProposalDetail(
      UUID proposalId, UUID customerId, UUID portalContactId) {
    return portalJdbc
        .sql(
            """
            SELECT id, proposal_number, title, status, fee_model, fee_amount, fee_currency,
                   content_html, milestones_json, sent_at, expires_at,
                   org_name, org_logo, org_brand_color
            FROM portal.portal_proposals
            WHERE id = ? AND customer_id = ? AND portal_contact_id = ?
            """)
        .params(proposalId, customerId, portalContactId)
        .query(
            (rs, rowNum) -> {
              var sentAtTs = rs.getTimestamp("sent_at");
              var expiresAtTs = rs.getTimestamp("expires_at");
              return new PortalProposalDetail(
                  rs.getObject("id", UUID.class),
                  rs.getString("proposal_number"),
                  rs.getString("title"),
                  rs.getString("status"),
                  rs.getString("fee_model"),
                  rs.getBigDecimal("fee_amount"),
                  rs.getString("fee_currency"),
                  rs.getString("content_html"),
                  rs.getString("milestones_json"),
                  sentAtTs != null ? sentAtTs.toInstant() : null,
                  expiresAtTs != null ? expiresAtTs.toInstant() : null,
                  rs.getString("org_name"),
                  rs.getString("org_logo"),
                  rs.getString("org_brand_color"));
            })
        .optional()
        .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));
  }

  /**
   * Accept a proposal from the portal. Idempotent: re-accepting an ACCEPTED proposal returns
   * success.
   */
  @Transactional
  public PortalAcceptResponse acceptProposal(
      UUID proposalId, UUID customerId, UUID portalContactId) {
    // Read proposal from portal schema to validate ownership and status
    var portalRow = findPortalProposalRow(proposalId, customerId);

    // Validate portal contact matches
    if (!portalRow.portalContactId().equals(portalContactId)) {
      throw new ResourceNotFoundException("Proposal", proposalId);
    }

    // Idempotent: if already ACCEPTED, return success with actual acceptance timestamp
    if ("ACCEPTED".equals(portalRow.status())) {
      var acceptedAt = findAcceptedAt(proposalId);
      return new PortalAcceptResponse(
          proposalId, "ACCEPTED", acceptedAt, null, "This proposal has already been accepted.");
    }

    // Validate status is SENT
    if (!"SENT".equals(portalRow.status())) {
      throw new ResourceConflictException(
          "Proposal not actionable", "Cannot accept proposal in status " + portalRow.status());
    }

    // Execute orchestration using the already-bound tenant context from CustomerAuthFilter
    var result = orchestrationService.acceptProposal(proposalId, portalContactId);

    log.info("Portal accept completed for proposal {}, project {}", proposalId, result.projectId());

    return new PortalAcceptResponse(
        proposalId,
        "ACCEPTED",
        Instant.now(),
        null,
        "Thank you for accepting this proposal. Your project has been set up.");
  }

  /** Decline a proposal from the portal. */
  @Transactional
  public PortalDeclineResponse declineProposal(
      UUID proposalId, UUID customerId, UUID portalContactId, String reason) {
    // Read proposal from portal schema to validate ownership and status
    var portalRow = findPortalProposalRow(proposalId, customerId);

    // Validate portal contact matches
    if (!portalRow.portalContactId().equals(portalContactId)) {
      throw new ResourceNotFoundException("Proposal", proposalId);
    }

    // Validate status is SENT
    if (!"SENT".equals(portalRow.status())) {
      throw new ResourceConflictException(
          "Proposal not actionable", "Cannot decline proposal in status " + portalRow.status());
    }

    // Execute decline using the already-bound tenant context from CustomerAuthFilter
    proposalService.declineProposal(proposalId, reason);

    log.info("Portal decline completed for proposal {}", proposalId);

    return new PortalDeclineResponse(proposalId, "DECLINED", Instant.now());
  }

  // --- Private helpers ---

  private PortalProposalRow findPortalProposalRow(UUID proposalId, UUID customerId) {
    return portalJdbc
        .sql(
            """
            SELECT id, org_id, status, portal_contact_id
            FROM portal.portal_proposals
            WHERE id = ? AND customer_id = ?
            """)
        .params(proposalId, customerId)
        .query(
            (rs, rowNum) ->
                new PortalProposalRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("org_id"),
                    rs.getString("status"),
                    rs.getObject("portal_contact_id", UUID.class)))
        .optional()
        .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));
  }

  private Instant findAcceptedAt(UUID proposalId) {
    // Query the tenant-side proposal entity for the actual acceptance timestamp
    var proposal = proposalService.getProposal(proposalId);
    return proposal.getAcceptedAt();
  }

  // --- DTOs ---

  public record PortalProposalSummary(
      UUID id,
      String proposalNumber,
      String title,
      String status,
      String feeModel,
      BigDecimal feeAmount,
      String feeCurrency,
      Instant sentAt) {}

  public record PortalProposalDetail(
      UUID id,
      String proposalNumber,
      String title,
      String status,
      String feeModel,
      BigDecimal feeAmount,
      String feeCurrency,
      String contentHtml,
      String milestonesJson,
      Instant sentAt,
      Instant expiresAt,
      String orgName,
      String orgLogoUrl,
      String orgBrandColor) {}

  public record PortalAcceptResponse(
      UUID proposalId, String status, Instant acceptedAt, String projectName, String message) {}

  public record PortalDeclineResponse(UUID proposalId, String status, Instant declinedAt) {}

  private record PortalProposalRow(UUID id, String orgId, String status, UUID portalContactId) {}
}
