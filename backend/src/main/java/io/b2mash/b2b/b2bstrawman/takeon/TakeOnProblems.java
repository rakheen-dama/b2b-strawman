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
