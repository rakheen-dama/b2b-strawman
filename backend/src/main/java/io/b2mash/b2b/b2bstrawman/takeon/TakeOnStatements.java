package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The versioned statements a take-on sign-off stores (§17.10 X3). Legal copy pending sign-off
 * (FD-5, release-gated RG-2): a wording change is a new version, not a data-model change.
 */
public final class TakeOnStatements {

  /** The FICA attestation of a clients batch (A1). */
  public static final String FICA_ATTEST_V1 = "FICA-ATTEST-v1";

  private static final String FICA_ATTEST_V1_TEXT =
      "I confirm that customer due diligence for the clients in this batch was performed by %s in"
          + " its previous system, as recorded in each row, and that the records and the copies"
          + " of, or references to, the verification documents remain available at the location"
          + " recorded for each client for at least as long as FICA requires (sections 22 and"
          + " 23). These clients will be shown as verified in the prior system, not as verified in"
          + " Kazi.";

  /** The partner/director sign-off of a trust position take-on (Rule 54.19, FD-4, 657C). */
  public static final String TAKEON_SIGNOFF_V1 = "TAKEON-SIGNOFF-v1";

  private static final String TAKEON_SIGNOFF_V1_TEXT =
      "I am a partner or director of %s responsible for its trust accounting records (Rule"
          + " 54.19). I have reviewed this take-on of %s at %s. The trust creditors listed agree,"
          + " to the cent, with the reconciled trust position in the previous system at that date,"
          + " and no trust creditor is in debit.";

  private TakeOnStatements() {}

  /** The {@code FICA-ATTEST-v1} text with the firm's name filled in. */
  public static String ficaAttestation(String firmName) {
    return FICA_ATTEST_V1_TEXT.formatted(Objects.requireNonNull(firmName, "firmName"));
  }

  /**
   * The {@code TAKEON-SIGNOFF-v1} declaration (§17.10 X3, verbatim; legal copy release-gated RG-1)
   * with the firm, the trust account and the cutover date ({@code d MMMM yyyy}) filled in.
   */
  public static String trustSignOff(String firmName, String accountName, LocalDate cutoverDate) {
    return TAKEON_SIGNOFF_V1_TEXT.formatted(
        Objects.requireNonNull(firmName, "firmName"),
        Objects.requireNonNull(accountName, "accountName"),
        IntegrityMessages.date(Objects.requireNonNull(cutoverDate, "cutoverDate")));
  }
}
