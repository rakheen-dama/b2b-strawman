package io.b2mash.b2b.b2bstrawman.testutil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every term of the trust equation (architecture §16.1.2) for one trust account as at one date, as
 * computed by {@link TrustBooksHarness} with its own SQL. Tests read figures through this record
 * (and {@link TrustBooksHarness#clientLedger}) only — never from {@code client_ledger_cards} or a
 * production service — so that 646A can swap the harness internals to the effect columns without
 * changing any expectation (task 646.9).
 */
public record BooksSnapshot(
    UUID trustAccountId,
    LocalDate asAt,
    BigDecimal cashbook,
    BigDecimal clientLedgers,
    BigDecimal investmentCreditors,
    BigDecimal fundLedger,
    BigDecimal unidentified,
    BigDecimal interAccount) {

  /**
   * {@code creditors(A,D)} — the six-term creditor side of EQ-2: client ledgers + investment
   * creditors + fund ledger + unidentified + other non-client (always zero in Phase 92) +
   * inter-account.
   */
  public BigDecimal creditors() {
    return clientLedgers
        .add(investmentCreditors)
        .add(fundLedger)
        .add(unidentified)
        .add(interAccount);
  }

  /** {@code cashbook − creditors}: zero when EQ-2 holds. */
  public BigDecimal difference() {
    return cashbook.subtract(creditors());
  }

  /** Per-term movement from {@code before} to this snapshot ({@code this − before}). */
  public TrustBooksHarness.Expected deltaSince(BooksSnapshot before) {
    return new TrustBooksHarness.Expected(
        cashbook.subtract(before.cashbook),
        clientLedgers.subtract(before.clientLedgers),
        investmentCreditors.subtract(before.investmentCreditors),
        fundLedger.subtract(before.fundLedger),
        unidentified.subtract(before.unidentified),
        interAccount.subtract(before.interAccount));
  }
}
