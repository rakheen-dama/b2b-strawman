package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.io.IOException;
import java.io.InputStream;

/** Strategy interface for parsing bank statement files into a structured format. */
public interface BankStatementParser {

  /**
   * Checks whether this parser can handle the given file based on its name and header line.
   *
   * @param fileName the uploaded file name
   * @param headerLine the first line of the file content
   * @return true if this parser recognizes the format
   */
  boolean canParse(String fileName, String headerLine);

  /**
   * Parses the bank statement from the given input stream.
   *
   * @param inputStream the file content
   * @return the parsed statement with header data and line items
   * @throws IOException if reading fails
   * @throws BankStatementParseException if the content is malformed
   */
  ParsedStatement parse(InputStream inputStream) throws IOException;
}
