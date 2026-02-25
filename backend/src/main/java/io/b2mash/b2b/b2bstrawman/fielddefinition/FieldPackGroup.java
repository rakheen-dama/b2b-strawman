package io.b2mash.b2b.b2bstrawman.fielddefinition;

/** DTO record for the group section within a field pack JSON file. */
public record FieldPackGroup(String slug, String name, String description, Boolean autoApply) {

  /** Returns autoApply with null-safe default of false. */
  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }
}
