package io.b2mash.b2b.b2bstrawman.takeon;

/**
 * Status of one take-on row (§17.2.3): {@code PENDING} (read) → {@code VALIDATED} | {@code ERROR}
 * (dry run) → {@code CREATED} | {@code SKIPPED_DUPLICATE} | {@code SKIPPED_ZERO} | {@code FAILED}
 * (commit). Stored in {@code take_on_rows.status} (CHECK {@code chk_take_on_row_status}).
 */
public enum TakeOnRowStatus {
  PENDING,
  VALIDATED,
  ERROR,
  CREATED,
  SKIPPED_DUPLICATE,
  SKIPPED_ZERO,
  FAILED;

  /** Set by a commit — never reset by re-validation. */
  public boolean isTerminal() {
    return this == CREATED || this == SKIPPED_DUPLICATE || this == SKIPPED_ZERO || this == FAILED;
  }
}
