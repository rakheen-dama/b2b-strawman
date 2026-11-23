package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.Map;

/**
 * One data row as read: {@code rowNumber} is the row the user sees in Excel (1-based, header and
 * title rows counted; for CSV the 1-based record index), {@code values} maps each header to the
 * trimmed cell text ({@code ""} when blank), in header order.
 */
public record TakeOnRawRow(int rowNumber, Map<String, String> values) {}
