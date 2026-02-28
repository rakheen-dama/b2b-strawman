package io.b2mash.b2b.b2bstrawman.clause;

import java.util.List;

/** DTO record for deserializing clause pack JSON files from the classpath. */
public record ClausePackDefinition(
    String packId,
    int version,
    String name,
    String description,
    List<ClauseDefinition> clauses,
    List<TemplateAssociation> templateAssociations) {

  /** A single clause definition within a clause pack. */
  public record ClauseDefinition(
      String title, String slug, String category, String description, Object body, int sortOrder) {}

  /** Associates clauses from this pack with a template from another pack. */
  public record TemplateAssociation(
      String templatePackId,
      String templateKey,
      List<String> clauseSlugs,
      List<String> requiredSlugs) {}
}
