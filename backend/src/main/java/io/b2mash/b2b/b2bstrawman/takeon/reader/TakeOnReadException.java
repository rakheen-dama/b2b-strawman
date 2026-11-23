package io.b2mash.b2b.b2bstrawman.takeon.reader;

/**
 * A take-on file that cannot be read. The message is user-facing (X3 wording); the service layer
 * maps it to a 422 ProblemDetail carrying {@link #getCode()}.
 */
public class TakeOnReadException extends RuntimeException {

  public enum Code {
    FILE_TOO_LARGE,
    UNSUPPORTED_FORMAT,
    UNREADABLE,
    TOO_MANY_ROWS,
    TOO_MANY_COLUMNS,
    ALL_ROWS_MALFORMED,
    NO_HEADER,
    SHEET_NOT_FOUND
  }

  private final Code code;

  public TakeOnReadException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public TakeOnReadException(Code code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public Code getCode() {
    return code;
  }
}
