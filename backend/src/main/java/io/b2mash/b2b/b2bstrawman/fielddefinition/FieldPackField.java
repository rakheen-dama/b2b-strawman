package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.Map;

/** DTO record for individual field entries within a field pack JSON file. */
public record FieldPackField(
    String slug,
    String name,
    String fieldType,
    String description,
    boolean required,
    Map<String, Object> defaultValue,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    int sortOrder,
    List<String> requiredForContexts) {

  /** Compact canonical constructor that defaults requiredForContexts to empty list when null. */
  public FieldPackField {
    if (requiredForContexts == null) {
      requiredForContexts = List.of();
    }
  }
}
