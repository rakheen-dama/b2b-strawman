package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The owner of the rows that resolve an unidentified deposit — {@code UNIDENT_ALLOC_OUT} and {@code
 * UNIDENT_FUND_PAY} (Phase 92 647D, ADR-376, §16.3.6). Implemented by {@code
 * creditor.UnidentifiedDepositService}; {@link TrustTransactionService} calls it inside the
 * approving, rejecting or reversing transaction (each implementation method is {@code
 * Propagation.MANDATORY}). It lives here so {@code transaction} never imports the creditor service,
 * and the implementation never depends on {@link TrustTransactionService} (no bean cycle).
 *
 * <p>Lock order: the resolution row (locked by the caller) → the item → the client's lock anchor.
 */
public interface UnidentifiedResolutionHooks {

  /**
   * The resolution row has just posted (final approval): for an allocation the {@code
   * UNIDENT_ALLOC_IN} credit leg posts and the item is {@code ALLOCATED}; for a payment to the Fund
   * the item is {@code PAID_TO_FUND}.
   */
  void onResolutionApproved(TrustTransaction resolution);

  /** The resolution row has just been {@code REJECTED}: the item stays HELD, resolution cleared. */
  void onResolutionRejected(TrustTransaction resolution);

  /**
   * Before the resolution's contra(s) post (same transaction): locks the item and returns it to
   * HELD. Returns the posted {@code UNIDENT_ALLOC_IN} to reverse with an allocation, empty for a
   * payment to the Fund.
   */
  Optional<UUID> onResolutionReversed(TrustTransaction resolution, LocalDate reversalDate);
}
