package io.b2mash.b2b.b2bstrawman.takeon;

/**
 * Which part of a batch a source file holds (§17.2.2). {@code MAIN} for the single-file datasets;
 * the trust position uses one source per creditor-group section. Stored in {@code
 * take_on_sources.section} and {@code take_on_rows.section}.
 */
public enum TakeOnSection {
  MAIN("File"),
  CLIENT_LEDGER("Client and matter balances"),
  UNIDENTIFIED_DEPOSITS("Unidentified deposits"),
  INVESTMENTS("Investments"),
  OUTSTANDING_ITEMS("Items outstanding at cutover");

  private final String label;

  TakeOnSection(String label) {
    this.label = label;
  }

  /** The X3 label — never show the raw enum name. */
  public String label() {
    return label;
  }
}
