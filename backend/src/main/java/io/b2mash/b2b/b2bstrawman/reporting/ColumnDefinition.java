package io.b2mash.b2b.b2bstrawman.reporting;

/** Parsed representation of a column from column_definitions JSON. */
public record ColumnDefinition(String key, String label, String type, String format) {
  public ColumnDefinition(String key, String label, String type) {
    this(key, label, type, null);
  }
}
