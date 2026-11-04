package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.Map;

/**
 * DTO record for individual template entries within a template pack JSON file. {@code
 * requiredContextFields} (Phase 89 C1) is the optional list of {@code {entity, field}} entries the
 * template declares — {@code field} may be a dotted path ({@code customFields.seller_name}); empty
 * when the pack declares none.
 */
public record TemplatePackTemplate(
    String templateKey,
    String name,
    String category,
    String primaryEntityType,
    String contentFile,
    String cssFile,
    String description,
    int sortOrder,
    Boolean acceptanceEligible,
    List<Map<String, String>> requiredContextFields) {

  public TemplatePackTemplate {
    requiredContextFields =
        requiredContextFields == null ? List.of() : List.copyOf(requiredContextFields);
  }
}
