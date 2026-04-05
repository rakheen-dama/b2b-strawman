package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    try {
      return LocalDate.parse(str);
    } catch (DateTimeParseException e) {
      throw new InvalidStateException(
          "Invalid parameter", "Parameter '%s' is not a valid date: %s".formatted(key, value));
    }
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

  /** Parses a required UUID parameter, throwing if absent or blank. */
  static UUID requireUuid(Map<String, Object> params, String key) {
    var result = parseUuid(params, key);
    if (result == null) {
      throw new InvalidStateException(
          "Missing required parameter", "Parameter '%s' is required".formatted(key));
    }
    return result;
  }

  /** Parses a required date parameter, throwing if absent or blank. */
  static LocalDate requireDate(Map<String, Object> params, String key) {
    var result = parseDate(params, key);
    if (result == null) {
      throw new InvalidStateException(
          "Missing required parameter", "Parameter '%s' is required".formatted(key));
    }
    return result;
  }

  static String parseString(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) return null;
    var str = value.toString().strip();
    return str.isEmpty() ? null : str;
  }
}
