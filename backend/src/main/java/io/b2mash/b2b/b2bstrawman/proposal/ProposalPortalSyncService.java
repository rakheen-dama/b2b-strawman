package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Syncs proposal data to the portal read-model ({@code portal.portal_proposals}) when a proposal is
 * sent or its status changes. Uses the portal {@link JdbcClient} (separate data source) rather than
 * JPA.
 */
@Service
public class ProposalPortalSyncService {

  private final JdbcClient jdbc;

  public ProposalPortalSyncService(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant != null ? Timestamp.from(instant) : null;
  }

  /**
   * Inserts a portal_proposals row when a proposal is sent. The {@code contentHtml} parameter is
   * the pre-rendered HTML (Tiptap JSON already rendered with variables resolved).
   *
   * @param proposal the proposal entity (must have portalContactId set)
   * @param contentHtml pre-rendered HTML content
   * @param orgId Clerk organization ID
   * @param orgName organization display name
   * @param orgSettings org settings for branding data
   */
  public void syncProposalToPortal(
      Proposal proposal,
      String contentHtml,
      String orgId,
      String orgName,
      OrgSettings orgSettings) {

    BigDecimal feeAmount = resolveFeeAmount(proposal);
    String feeCurrency = resolveFeeCurrency(proposal);

    jdbc.sql(
            """
            INSERT INTO portal.portal_proposals
                (id, org_id, customer_id, portal_contact_id, proposal_number, title, status,
                 fee_model, fee_amount, fee_currency, content_html, milestones_json,
                 sent_at, expires_at, org_name, org_logo_url, org_brand_color, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now())
            """)
        .params(
            proposal.getId(),
            orgId,
            proposal.getCustomerId(),
            proposal.getPortalContactId(),
            proposal.getProposalNumber(),
            proposal.getTitle(),
            proposal.getStatus().name(),
            proposal.getFeeModel().name(),
            feeAmount,
            feeCurrency,
            contentHtml,
            "[]",
            toTimestamp(proposal.getSentAt()),
            toTimestamp(proposal.getExpiresAt()),
            orgName,
            orgSettings != null ? orgSettings.getLogoS3Key() : null,
            orgSettings != null ? orgSettings.getBrandColor() : null)
        .update();
  }

  /**
   * Updates the status and synced_at timestamp for a portal proposal.
   *
   * @param proposalId the proposal UUID
   * @param newStatus the new status string (e.g., "ACCEPTED", "DECLINED")
   */
  public void updatePortalProposalStatus(UUID proposalId, String newStatus) {
    jdbc.sql(
            """
            UPDATE portal.portal_proposals
            SET status = ?, synced_at = now()
            WHERE id = ?
            """)
        .params(newStatus, proposalId)
        .update();
  }

  private BigDecimal resolveFeeAmount(Proposal proposal) {
    return switch (proposal.getFeeModel()) {
      case FIXED -> proposal.getFixedFeeAmount();
      case RETAINER -> proposal.getRetainerAmount();
      case HOURLY -> null;
    };
  }

  private String resolveFeeCurrency(Proposal proposal) {
    return switch (proposal.getFeeModel()) {
      case FIXED -> proposal.getFixedFeeCurrency();
      case RETAINER -> proposal.getRetainerCurrency();
      case HOURLY -> null;
    };
  }
}
