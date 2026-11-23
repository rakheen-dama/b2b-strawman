package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a source's text is read and typed (§17.3.1); stored as {@code take_on_sources.parse_options}
 * ({@code {dateFormat, decimalSeparator, delimiter, encoding}}). {@code delimiter} null means
 * "detect"; it only applies to CSV.
 */
public record TakeOnParseOptions(
    String dateFormat, DecimalSeparator decimalSeparator, Character delimiter, String encoding) {

  public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy";
  public static final String UTF_8 = "UTF-8";

  public enum DecimalSeparator {
    POINT,
    COMMA
  }

  public TakeOnParseOptions {
    if (dateFormat == null || dateFormat.isBlank()) {
      dateFormat = DEFAULT_DATE_FORMAT;
    }
    if (decimalSeparator == null) {
      decimalSeparator = DecimalSeparator.POINT;
    }
    if (encoding == null || encoding.isBlank()) {
      encoding = UTF_8;
    }
  }

  public static TakeOnParseOptions defaults() {
    return new TakeOnParseOptions(DEFAULT_DATE_FORMAT, DecimalSeparator.POINT, null, UTF_8);
  }

  public TakeOnParseOptions withDelimiter(Character newDelimiter) {
    return new TakeOnParseOptions(dateFormat, decimalSeparator, newDelimiter, encoding);
  }

  public TakeOnParseOptions withEncoding(String newEncoding) {
    return new TakeOnParseOptions(dateFormat, decimalSeparator, delimiter, newEncoding);
  }

  public TakeOnParseOptions withDecimalSeparator(DecimalSeparator newSeparator) {
    return new TakeOnParseOptions(dateFormat, newSeparator, delimiter, encoding);
  }

  /** The jsonb shape of {@code take_on_sources.parse_options} (delimiter as a 1-char string). */
  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("dateFormat", dateFormat);
    map.put("decimalSeparator", decimalSeparator.name());
    map.put("delimiter", delimiter == null ? null : String.valueOf(delimiter));
    map.put("encoding", encoding);
    return map;
  }

  /** Reads {@link #toMap()}'s shape; missing or blank entries fall back to the defaults. */
  public static TakeOnParseOptions fromMap(Map<String, Object> map) {
    if (map == null) {
      return defaults();
    }
    Object separator = map.get("decimalSeparator");
    Object delimiter = map.get("delimiter");
    return new TakeOnParseOptions(
        map.get("dateFormat") instanceof String s ? s : null,
        separator instanceof String s && !s.isBlank()
            ? DecimalSeparator.valueOf(s.trim().toUpperCase(Locale.ROOT))
            : null,
        delimiter instanceof String s && s.length() == 1 ? s.charAt(0) : null,
        map.get("encoding") instanceof String s ? s : null);
  }
}
