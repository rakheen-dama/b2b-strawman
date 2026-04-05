package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Shared parameter parsing utilities for trust report queries. */
final class ReportParamUtils {

  private ReportParamUtils() {}

  static LocalDate parseDate(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) return null;
    if (value instanceof LocalDate ld) return ld;
    var str = value.toString().strip();
    if (str.isEmpty()) return null;
    return LocalDate.parse(str);
  }

  static UUID parseUuid(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) return null;
    if (value instanceof UUID uuid) return uuid;
    var str = value.toString().strip();
    if (str.isEmpty()) return null;
    try {
      return UUID.fromString(str);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid parameter", "Parameter '%s' is not a valid UUID: %s".formatted(key, value));
    }
  }

  static String parseString(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) return null;
    var str = value.toString().strip();
    return str.isEmpty() ? null : str;
  }
}
