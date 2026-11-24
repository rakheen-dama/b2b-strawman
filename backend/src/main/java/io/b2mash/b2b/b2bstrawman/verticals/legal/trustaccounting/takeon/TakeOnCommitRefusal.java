package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import java.util.Objects;

/**
 * A known reason the trust take-on commit refuses to post (Phase 93 657C, R-3, R-6): thrown inside
 * the commit transaction so everything rolls back; {@link TrustTakeOnRunner} then returns the batch
 * to {@code VALIDATED} with {@link #userMessage()} as {@code last_error} (X3-register product copy,
 * RG-10) and audits {@code takeon.batch.commit_failed} with {@link #reasonCode()} (a SAFE_TOKEN).
 * The exception message is the code only — never a value from the file or the books.
 */
public final class TakeOnCommitRefusal extends RuntimeException {

  public static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
  public static final String ROW_REDACTED = "ROW_REDACTED";
  public static final String SIGN_OFF_MISSING = "SIGN_OFF_MISSING";
  public static final String REFERENCE_UNRESOLVED = "REFERENCE_UNRESOLVED";
  public static final String SELF_CHECK_FAILED = "SELF_CHECK_FAILED";
  public static final String UNEXPECTED = "UNEXPECTED";

  static final String SIGN_OFF_MISSING_MESSAGE =
      "The sign-off for this trust take-on was withdrawn before the commit ran. Sign it off again,"
          + " then commit.";

  static final String ROW_REDACTED_MESSAGE =
      "A client named in this trust take-on was anonymised after it was validated. Validate it"
          + " again, then sign it off.";

  static final String SELF_CHECK_FAILED_MESSAGE =
      "The trust position did not agree with the books when it was posted, so nothing was posted."
          + " Validate the take-on again. If it happens again, contact support.";

  static final String UNEXPECTED_MESSAGE =
      "The trust take-on could not be committed, so nothing was posted. Try again, or contact"
          + " support if it fails again.";

  private final String reasonCode;
  private final String userMessage;

  public TakeOnCommitRefusal(String reasonCode, String userMessage) {
    super(Objects.requireNonNull(reasonCode, "reasonCode"));
    this.reasonCode = reasonCode;
    this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
  }

  public static TakeOnCommitRefusal signOffMissing() {
    return new TakeOnCommitRefusal(SIGN_OFF_MISSING, SIGN_OFF_MISSING_MESSAGE);
  }

  public static TakeOnCommitRefusal rowRedacted() {
    return new TakeOnCommitRefusal(ROW_REDACTED, ROW_REDACTED_MESSAGE);
  }

  public static TakeOnCommitRefusal preconditionFailed(String message) {
    return new TakeOnCommitRefusal(PRECONDITION_FAILED, message);
  }

  public static TakeOnCommitRefusal referenceUnresolved(String message) {
    return new TakeOnCommitRefusal(REFERENCE_UNRESOLVED, message);
  }

  public static TakeOnCommitRefusal selfCheckFailed() {
    return new TakeOnCommitRefusal(SELF_CHECK_FAILED, SELF_CHECK_FAILED_MESSAGE);
  }

  public String reasonCode() {
    return reasonCode;
  }

  public String userMessage() {
    return userMessage;
  }
}
