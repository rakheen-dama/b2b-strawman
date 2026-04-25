package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;

/** DTO record for the group section within a field pack JSON file. */
public record FieldPackGroup(
    String slug,
    String name,
    String description,
    Boolean autoApply,
    List<String> applicableWorkTypes) {

  /** Returns autoApply with null-safe default of false. */
  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }

  /**
   * Returns applicableWorkTypes with null-safe default of empty list. An empty list (or null in
   * JSON) means the group applies regardless of project.work_type — preserving the pre-GAP-L-37
   * behaviour for the 8 packs that don't opt in to work-type scoping.
   */
  public List<String> applicableWorkTypesOrEmpty() {
    return applicableWorkTypes != null ? applicableWorkTypes : List.of();
  }
}
