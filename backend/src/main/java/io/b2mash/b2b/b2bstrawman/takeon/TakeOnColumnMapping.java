package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A source's column mapping (§17.3.2) as stored verbatim in {@code take_on_sources.column_mapping}
 * — the preset seed (RD-4): {@code targets} field key → header, {@code customFields} custom-field
 * slug → header, {@code parked} headers kept as a note on the record, {@code defaults} field key →
 * value used when the cell is blank.
 */
public record TakeOnColumnMapping(
    Map<String, String> targets,
    Map<String, String> customFields,
    List<String> parked,
    Map<String, String> defaults) {

  public TakeOnColumnMapping {
    targets = targets == null ? Map.of() : copy(targets);
    customFields = customFields == null ? Map.of() : copy(customFields);
    parked = parked == null ? List.of() : List.copyOf(parked);
    defaults = defaults == null ? Map.of() : copy(defaults);
  }

  /** The jsonb shape (insertion order kept). */
  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("targets", new LinkedHashMap<>(targets));
    map.put("customFields", new LinkedHashMap<>(customFields));
    map.put("parked", new ArrayList<>(parked));
    map.put("defaults", new LinkedHashMap<>(defaults));
    return map;
  }

  /** Reads {@link #toMap()}'s shape; null when the source is not mapped. */
  public static TakeOnColumnMapping fromMap(Map<String, Object> map) {
    if (map == null) {
      return null;
    }
    return new TakeOnColumnMapping(
        stringMap(map.get("targets")),
        stringMap(map.get("customFields")),
        stringList(map.get("parked")),
        stringMap(map.get("defaults")));
  }

  private static Map<String, String> copy(Map<String, String> source) {
    var copy = new LinkedHashMap<String, String>();
    source.forEach(
        (k, v) -> {
          if (k != null && v != null) {
            copy.put(k, v);
          }
        });
    return java.util.Collections.unmodifiableMap(copy);
  }

  private static Map<String, String> stringMap(Object value) {
    var result = new LinkedHashMap<String, String>();
    if (value instanceof Map<?, ?> m) {
      m.forEach(
          (k, v) -> {
            if (k != null && v != null) {
              result.put(String.valueOf(k), String.valueOf(v));
            }
          });
    }
    return result;
  }

  private static List<String> stringList(Object value) {
    var result = new ArrayList<String>();
    if (value instanceof List<?> l) {
      l.forEach(
          v -> {
            if (v != null) {
              result.add(String.valueOf(v));
            }
          });
    }
    return result;
  }
}
