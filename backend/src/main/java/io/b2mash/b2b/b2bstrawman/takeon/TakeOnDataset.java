package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;

/**
 * What a take-on batch brings across from the firm's previous system (Phase 93, §17.2.1). Stored in
 * {@code take_on_batches.dataset} (CHECK {@code chk_take_on_batch_dataset}).
 */
public enum TakeOnDataset {
  CLIENTS("Clients"),
  MATTERS("Matters"),
  TRUST_POSITION("Trust opening position"),
  DEBTOR_BALANCES("Debtor opening balances");

  private final String label;

  TakeOnDataset(String label) {
    this.label = label;
  }

  /** The X3 card text — never show the raw enum name. */
  public String label() {
    return label;
  }

  /** The file sections a batch of this dataset may hold (one source per section). */
  public List<TakeOnSection> sections() {
    return this == TRUST_POSITION
        ? List.of(
            TakeOnSection.CLIENT_LEDGER,
            TakeOnSection.UNIDENTIFIED_DEPOSITS,
            TakeOnSection.INVESTMENTS,
            TakeOnSection.OUTSTANDING_ITEMS)
        : List.of(TakeOnSection.MAIN);
  }

  /** A cutover ("as at") date is required before validation (CHECK chk_take_on_batch_cutover). */
  public boolean requiresCutoverDate() {
    return this == TRUST_POSITION || this == DEBTOR_BALANCES;
  }

  /** A trust banking account is required (CHECK chk_take_on_batch_trust_account). */
  public boolean requiresTrustAccount() {
    return this == TRUST_POSITION;
  }

  /** Every dataset but the trust position takes exactly one file (section MAIN). */
  public boolean singleSource() {
    return this != TRUST_POSITION;
  }
}
