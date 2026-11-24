package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnReadException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * The take-on refusals the frontend may branch on (Phase 93 653A): each is an RFC 9457 problem with
 * a machine-readable {@code code} property. 409 for state conflicts, 422 for files that cannot be
 * taken on, 404 for a purged file. Request-shape refusals stay {@code InvalidStateException} (400)
 * and missing rows {@code ResourceNotFoundException} (404).
 */
public final class TakeOnProblems {

  private TakeOnProblems() {}

  /** A take-on refusal: the HTTP status, title, X3 detail and {@code code} of one case. */
  public static final class TakeOnProblemException extends ErrorResponseException {

    private final String code;

    TakeOnProblemException(HttpStatus status, String code, String title, String detail) {
      super(status, ProblemDetailFactory.create(status, title, detail, Map.of("code", code)), null);
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  // --- 409 ---

  /** An entity transition refused (C-2): the entity's X3 message is the detail. */
  public static TakeOnProblemException wrongState(String message) {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_WRONG_STATE",
        "Take-on batch not in the right state",
        message);
  }

  /** Mapping a file kept with no rows (OBS F-1): the sheet and header row come first. */
  public static TakeOnProblemException noRows() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_NO_ROWS",
        "No rows to map",
        "This file has no rows to map below the chosen header row. Choose the sheet and the row"
            + " that holds the column names.");
  }

  public static TakeOnProblemException concurrentChange() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_CONCURRENT_CHANGE",
        "Take-on batch changed",
        "This take-on batch was changed at the same time. Try again.");
  }

  public static TakeOnProblemException cutoverDateRequired() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_CUTOVER_DATE_REQUIRED",
        "Cutover date required",
        "Enter the cutover date before validating.");
  }

  /** 657B: a trust take-on's dry run needs the reconciled position at cutover. */
  public static TakeOnProblemException positionRequired() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_POSITION_REQUIRED",
        "Reconciled position required",
        "Enter the reconciled position at cutover before validating.");
  }

  public static TakeOnProblemException positionNotApplicable() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_POSITION_NOT_APPLICABLE",
        "Cutover date not applicable",
        "A cutover date applies only to trust and debtor take-ons.");
  }

  public static TakeOnProblemException sectionExists(TakeOnSection section) {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_SECTION_EXISTS",
        "File already uploaded",
        "This take-on already has a file for %s. Remove it to upload another."
            .formatted(section.label().toLowerCase(Locale.ROOT)));
  }

  public static TakeOnProblemException singleSource() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_SINGLE_SOURCE",
        "One file per take-on",
        "This take-on has one file. Remove it to upload another.");
  }

  public static TakeOnProblemException notABankingAccount() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_NOT_A_BANKING_ACCOUNT",
        "Not a trust banking account",
        "Choose the trust banking account to take on. Investment accounts are taken on through"
            + " their investment rows.");
  }

  // --- 409: attestation and commit (655A, §17.4.4, §17.10 X3) ---

  /** A second commit of the same batch (a double-submit). */
  public static TakeOnProblemException alreadyCommitting() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "ALREADY_COMMITTING",
        "This take-on batch is already committing",
        "This take-on batch is already committing. Wait for it to finish.");
  }

  /** Another batch holds the tenant's one commit slot ({@code uq_take_on_batches_committing}). */
  public static TakeOnProblemException anotherBatchCommitting() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "ANOTHER_BATCH_COMMITTING",
        "Another take-on batch is committing",
        "Another take-on batch is committing. Wait for it to finish.");
  }

  /** The job queue is switched off on this server: refused before any claim (FD-35). */
  public static TakeOnProblemException queueDisabled() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_QUEUE_DISABLED",
        "Take-on commits are switched off on this server",
        "Take-on commits run in the background, and the background worker is switched off on"
            + " this server.");
  }

  /** The hash sent is not the batch's (or the live attestation's) current hash. */
  public static TakeOnProblemException hashMismatch() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "HASH_MISMATCH",
        "The batch changed since it was signed off",
        "Re-validate and sign off again before committing.");
  }

  /** A clients batch commits only under a live FICA attestation on its current hash (C-15). */
  public static TakeOnProblemException attestationRequired() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_ATTESTATION_REQUIRED",
        "FICA attestation required",
        "Attest the FICA records of this batch before committing it.");
  }

  /** One live attestation per batch ({@code uq_take_on_sign_offs_live}). */
  public static TakeOnProblemException alreadyAttested() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_ALREADY_ATTESTED",
        "Batch already attested",
        "This take-on batch has already been attested. Withdraw the attestation to attest again.");
  }

  /** An attestation needs at least one row that a commit would create. */
  public static TakeOnProblemException nothingToAttest() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_NOTHING_TO_ATTEST",
        "Nothing to attest",
        "This take-on batch has no valid rows to attest. Correct the file and validate it again.");
  }

  /**
   * A statutory hard block on a row stops the whole batch (657B semantics; 655A guards the claim
   * and the attestation so a blocked batch never reaches the drain). Additive code (CR-7).
   */
  public static TakeOnProblemException blocked() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_BLOCKED",
        "Take-on batch blocked",
        "Rows in this take-on batch block it. Correct them in the previous system, upload the"
            + " corrected file and validate it again.");
  }

  /**
   * A batch committed whole (the trust position, D-191) still holds an error — on a row or an
   * account check above the table. Additive code.
   */
  public static TakeOnProblemException errorsRemain() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_ERRORS_REMAIN",
        "Take-on batch has errors",
        "A trust take-on is committed whole. Correct every error, upload the corrected files and"
            + " validate it again.");
  }

  /** Commit runs for one dataset in 655A; the others arrive with their slices. */
  public static TakeOnProblemException commitNotAvailable() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_COMMIT_NOT_AVAILABLE",
        "Commit not available yet",
        "This take-on cannot be committed yet.");
  }

  /** Portal access is created for a completed clients batch only. */
  public static TakeOnProblemException notCompleted() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_NOT_COMPLETED",
        "Take-on batch not completed",
        "Portal access can be created once the take-on has been committed.");
  }

  /** The result report exists once a commit has finished. */
  public static TakeOnProblemException noResultYet() {
    return new TakeOnProblemException(
        HttpStatus.CONFLICT,
        "TAKE_ON_NO_RESULT",
        "No result yet",
        "The result report is available once the take-on has been committed.");
  }

  // --- 422 ---

  public static TakeOnProblemException fileUnsupported() {
    return new TakeOnProblemException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "TAKE_ON_FILE_UNSUPPORTED",
        "File not supported",
        "Save the file as .xlsx or .csv and upload it again.");
  }

  public static TakeOnProblemException fileTooLarge() {
    return new TakeOnProblemException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "TAKE_ON_FILE_TOO_LARGE",
        "File too large",
        "This file is larger than 10 MB. Split it into smaller files.");
  }

  public static TakeOnProblemException fileEmpty() {
    return new TakeOnProblemException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "TAKE_ON_FILE_EMPTY",
        "File is empty",
        "This file is empty. Choose the file again.");
  }

  /**
   * A reader refusal: {@code TAKE_ON_} + the reader's code, its X3 message as the detail. An
   * unsupported format is the same refusal as the upload sniffer's.
   */
  public static TakeOnProblemException unreadable(TakeOnReadException e) {
    return switch (e.getCode()) {
      case UNSUPPORTED_FORMAT -> fileUnsupported();
      case FILE_TOO_LARGE -> fileTooLarge();
      case TOO_MANY_ROWS ->
          new TakeOnProblemException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "TAKE_ON_TOO_MANY_ROWS",
              "Too many rows",
              e.getMessage());
      case TOO_MANY_COLUMNS ->
          new TakeOnProblemException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "TAKE_ON_TOO_MANY_COLUMNS",
              "Too many columns",
              e.getMessage());
      case SHEET_NOT_FOUND ->
          new TakeOnProblemException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "TAKE_ON_SHEET_NOT_FOUND",
              "Sheet not found",
              e.getMessage());
      case HEADER_ROW_BLANK ->
          new TakeOnProblemException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "TAKE_ON_HEADER_ROW_BLANK",
              "Header row is blank",
              e.getMessage());
      case UNREADABLE, ALL_ROWS_MALFORMED, NO_HEADER ->
          new TakeOnProblemException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "TAKE_ON_" + e.getCode().name(),
              "File could not be read",
              e.getMessage());
    };
  }

  // --- 404 ---

  public static TakeOnProblemException fileGone() {
    return new TakeOnProblemException(
        HttpStatus.NOT_FOUND,
        "TAKE_ON_FILE_PURGED",
        "File removed",
        "The file was removed when the take-on batch was cancelled.");
  }
}
