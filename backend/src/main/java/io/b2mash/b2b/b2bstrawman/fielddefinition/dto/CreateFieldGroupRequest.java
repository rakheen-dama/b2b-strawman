package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param fieldDefinitionIds the group's member field definitions in display order; absent = an
 *     empty group (G88-F3, §14.3.16). API-only — pack seeders write membership directly.
 */
public record CreateFieldGroupRequest(
    @NotNull EntityType entityType,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String slug,
    String description,
    int sortOrder,
    Boolean autoApply,
    @Size(max = 10) List<UUID> dependsOn,
    List<@NotNull UUID> fieldDefinitionIds) {

  /** Returns autoApply with null-safe default of false. */
  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }

  /** Membership in list order; absent = an empty group (G88-F3). */
  public List<UUID> fieldDefinitionIdsOrEmpty() {
    return fieldDefinitionIds == null ? List.of() : fieldDefinitionIds;
  }
}
