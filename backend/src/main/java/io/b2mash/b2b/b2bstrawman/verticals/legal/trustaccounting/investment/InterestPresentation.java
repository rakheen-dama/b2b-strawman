package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

/**
 * How the bank presented a section 86(4) interest credit (Phase 92 648B, ADR-379, assumption A2 —
 * both are supported, so if A2 is wrong in either direction nothing changes).
 *
 * <ul>
 *   <li>{@link #GROSS} — the bank credited the gross to the investment account and debits the Legal
 *       Practitioners' Fidelity Fund's 5% in a separate line: the entry posts {@code INV_INTEREST}
 *       (the client's 95%) and {@code FUND_INV_SHARE} (the 5%, on the account's Fund ledger); the
 *       later debit is recorded from its line as {@code FUND_SWEEP}.
 *   <li>{@link #NET} — the bank credited only the client's 95%; the 5% went to the Fund at source
 *       and never reached the account. The entry posts {@code INV_INTEREST} only and alone holds
 *       the gross and the 5%.
 * </ul>
 */
public enum InterestPresentation {
  GROSS,
  NET
}
