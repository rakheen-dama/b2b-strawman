package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

/** Thrown when a bank statement file cannot be parsed due to malformed content. */
public class BankStatementParseException extends RuntimeException {

  public BankStatementParseException(String message) {
    super(message);
  }

  public BankStatementParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
