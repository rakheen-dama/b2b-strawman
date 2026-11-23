package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.Map;
import java.util.Set;

/**
 * One data row as read: {@code rowNumber} is the row the user sees in Excel (1-based, header and
 * title rows counted; for CSV the 1-based record index), {@code values} maps each header to the
 * trimmed cell text ({@code ""} when blank), in header order. {@code numericHeaders} names the
 * headers whose XLSX cell was a number (written as point-decimal text by {@link
 * TakeOnDataFormatter}); the chosen decimal separator applies only to the other, text cells.
 */
public record TakeOnRawRow(int rowNumber, Map<String, String> values, Set<String> numericHeaders) {

  public TakeOnRawRow {
    numericHeaders = numericHeaders == null ? Set.of() : Set.copyOf(numericHeaders);
  }

  public TakeOnRawRow(int rowNumber, Map<String, String> values) {
    this(rowNumber, values, Set.of());
  }
}
