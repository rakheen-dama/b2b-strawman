package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

/**
 * The kind of a non-client trust creditor account (Phase 92, ADR-376, architecture §16.2.4). Every
 * trust account carries one of each shipped type.
 *
 * <p>Validated in Java only — {@code trust_creditor_accounts.account_type} has no DDL CHECK so a
 * further type needs no schema change (ADR-376). A4: if interest and charges must be split, add a
 * type here and re-point {@code BANK_CHARGE}/{@code CHARGE_REFUND}'s {@code creditorAccountType}.
 * The default names are byte-identical to the V18 migration's literals.
 */
public enum TrustCreditorAccountType {
  /** The Legal Practitioners' Fidelity Fund's interest-and-charges ledger (may be in debit). */
  FUND_INTEREST_AND_CHARGES("Legal Practitioners' Fidelity Fund — interest and charges"),
  /** Bank credits whose owner is not yet known (never in debit by construction). */
  UNIDENTIFIED_DEPOSITS("Unidentified deposits");

  private final String defaultName;

  TrustCreditorAccountType(String defaultName) {
    this.defaultName = defaultName;
  }

  /** The display name a new account of this type is opened with. */
  public String defaultName() {
    return defaultName;
  }
}
