package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

/**
 * The owner of an investment's placement row — {@code INV_PLACE} (section 86(4)) and {@code
 * FIRM_INV_PLACE} (section 86(3)) (Phase 92 648A, ADR-379, §16.3.7). Implemented by {@code
 * investment.TrustInvestmentService}; {@link TrustTransactionService} calls it inside the approving
 * or rejecting transaction (each implementation method is {@code Propagation.MANDATORY}). It lives
 * here so {@code transaction} never imports the investment service, and the implementation never
 * depends on {@link TrustTransactionService} (no bean cycle) — the pattern of {@link
 * UnidentifiedResolutionHooks}.
 *
 * <p>Lock order: the placement row (locked by the caller) → the investment row.
 */
public interface InvestmentPlacementHooks {

  /**
   * The placement row has just posted (final approval) on the funding account: the receive leg
   * ({@code INV_RECEIVE} / {@code FIRM_INV_RECEIVE}) posts on the investment account, same amount,
   * date and reference, with its pair's approvers.
   */
  void onPlacementApproved(TrustTransaction placement);

  /**
   * The placement row has just been {@code REJECTED}: nothing was invested, so the investment is
   * {@code CANCELLED} (G89-F87).
   */
  void onPlacementRejected(TrustTransaction placement, String reason);
}
