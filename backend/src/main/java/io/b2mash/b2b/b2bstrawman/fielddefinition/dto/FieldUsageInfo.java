package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response indicating which templates and clauses reference a specific custom field.
 *
 * @param templates the list of templates that reference the field
 * @param clauses the list of clauses that reference the field
 */
public record FieldUsageInfo(List<TemplateReference> templates, List<ClauseReference> clauses) {

  /**
   * A reference to a document template that uses a custom field.
   *
   * @param id the template ID
   * @param name the template name
   * @param category the template category
   */
  public record TemplateReference(UUID id, String name, String category) {}

  /**
   * A reference to a clause that uses a custom field.
   *
   * @param id the clause ID
   * @param title the clause title
   */
  public record ClauseReference(UUID id, String title) {}
}
