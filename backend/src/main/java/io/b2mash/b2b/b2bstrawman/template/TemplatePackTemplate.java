package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.Map;

/**
 * DTO record for individual template entries within a template pack JSON file. {@code
 * requiredContextFields} (Phase 89 C1) is the optional list of {@code {entity, field}} entries the
 * template declares — {@code field} may be a dotted path ({@code customFields.seller_name}); empty
 * when the pack declares none.
 *
 * <p>{@code genericMenuExcludedProfiles} (G89 632A(c)) lists the vertical profiles on which the
 * template is not offered in the generic Generate menus (matter, client, fee note) — e.g. the
 * universal common pack's matter templates on legal-za, which ships its own. It governs the menus
 * only: the template still installs, stays in Settings › Documents and can still be generated.
 * Empty when the pack declares none. Read at list time from the classpath pack, so changing it
 * needs no pack version bump (no tenant row changes).
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
    List<Map<String, String>> requiredContextFields,
    List<String> genericMenuExcludedProfiles) {

  public TemplatePackTemplate {
    requiredContextFields =
        requiredContextFields == null ? List.of() : List.copyOf(requiredContextFields);
    genericMenuExcludedProfiles =
        genericMenuExcludedProfiles == null ? List.of() : List.copyOf(genericMenuExcludedProfiles);
  }
}
