package io.b2mash.b2b.b2bstrawman.crm.dto;

import io.b2mash.b2b.b2bstrawman.proposal.Proposal;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-model for a proposal linked to a CRM deal (Phase 80, 576A). Surfaces just enough for the
 * deal-detail "linked proposals" list: identity, proposal number, status chip, and a headline
 * amount.
 *
 * <p>{@code amount} is derived per fee model — FIXED uses {@code fixedFeeAmount}, RETAINER uses
 * {@code retainerAmount}; HOURLY and CONTINGENCY have no single headline amount, so {@code amount}
 * is {@code null} for those.
 *
 * @param id the proposal id
 * @param proposalNumber the human-facing proposal number (e.g. {@code P-2026-0007})
 * @param status the proposal status (DRAFT/SENT/ACCEPTED/DECLINED/EXPIRED)
 * @param amount the headline fee amount, or {@code null} for HOURLY/CONTINGENCY
 */
public record LinkedProposalDto(
    UUID id, String proposalNumber, ProposalStatus status, BigDecimal amount) {

  /** Maps a {@link Proposal} to its linked-proposal read-model view. */
  public static LinkedProposalDto from(Proposal proposal) {
    return new LinkedProposalDto(
        proposal.getId(),
        proposal.getProposalNumber(),
        proposal.getStatus(),
        resolveAmount(proposal));
  }

  private static BigDecimal resolveAmount(Proposal proposal) {
    return switch (proposal.getFeeModel()) {
      case FIXED -> proposal.getFixedFeeAmount();
      case RETAINER -> proposal.getRetainerAmount();
      case HOURLY, CONTINGENCY -> null;
    };
  }
}
