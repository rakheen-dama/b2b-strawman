package io.b2mash.b2b.b2bstrawman.takeon;

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

  private TakeOnStatements() {}

  /** The {@code FICA-ATTEST-v1} text with the firm's name filled in. */
  public static String ficaAttestation(String firmName) {
    return FICA_ATTEST_V1_TEXT.formatted(Objects.requireNonNull(firmName, "firmName"));
  }
}
