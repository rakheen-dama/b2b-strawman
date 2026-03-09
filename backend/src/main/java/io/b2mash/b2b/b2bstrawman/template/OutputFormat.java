package io.b2mash.b2b.b2bstrawman.template;

public enum OutputFormat {
  PDF,
  DOCX,

  /**
   * Request-only value — never persisted to the database. When BOTH is requested, the stored
   * outputFormat is DOCX (primary output) and the PDF is an additional artifact.
   */
  BOTH
}
