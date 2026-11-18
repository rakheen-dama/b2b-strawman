package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One trust creditor on a Rule 54.15 list (Phase 92 650A, architecture §16.2.10, ADR-382), as at
 * the list's date. Top-level (like {@link IntegrityFlag}) because {@link TrustMonthlyList} stores a
 * list of these, frozen, in its {@code lines} column — names included, so a locked list shows what
 * it showed on the day.
 *
 * <ul>
 *   <li>{@code CLIENT}: a client with a non-zero general balance; {@code matters} is the non-zero
 *       per-matter breakdown (a null {@code matterId} = rows not linked to a matter) and sums to
 *       {@code balance}.
 *   <li>{@code CLIENT_INVESTMENT}: a client with a non-zero invested balance (section 86(4)) on
 *       this account — the investment account holding the money.
 *   <li>{@code FUND}: the Legal Practitioners' Fidelity Fund interest-and-charges line, always
 *       present, even at zero or in debit.
 *   <li>{@code UNIDENTIFIED}: one open unidentified deposit, with its received date and bank
 *       description.
 *   <li>{@code INTER_ACCOUNT}: section 86(3) placements, only when non-zero (negative on the
 *       funding account, positive on the investment account).
 * </ul>
 *
 * <p>{@code balance} is signed. The lines of one account sum to its creditor total. {@code
 * receivedDate} and {@code lastTransactionDate} are ISO dates ({@code yyyy-MM-dd}): Hibernate's
 * JSON mapper for the tenant {@code jsonb} columns has no {@code java.time} support, so the frozen
 * line carries the dates as text.
 *
 * <p>{@code totalDeposits}, {@code totalPayments}, {@code totalFeeTransfers} and {@code
 * lastTransactionDate}: the client's posted {@code CLIENT}-ledger totals and last posted date on
 * the account as at the list's date — carried once per client, on its {@code CLIENT} line (or, with
 * none, its {@code CLIENT_INVESTMENT} line), null on every other line. Frozen with the list, so a
 * locked list's report rows show the figures of the day, not today's ledger.
 */
public record CreditorLine(
    Kind kind,
    UUID clientId,
    String name,
    List<MatterBalance> matters,
    BigDecimal balance,
    UUID unidentifiedDepositId,
    String receivedDate,
    String bankDescription,
    BigDecimal totalDeposits,
    BigDecimal totalPayments,
    BigDecimal totalFeeTransfers,
    String lastTransactionDate) {

  /** What a line is a balance of. */
  public enum Kind {
    CLIENT,
    CLIENT_INVESTMENT,
    FUND,
    UNIDENTIFIED,
    INTER_ACCOUNT
  }

  /** One matter's part of a client's general balance; {@code matterId} null = no matter. */
  public record MatterBalance(UUID matterId, String name, BigDecimal balance) {}
}
