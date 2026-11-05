package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param fieldDefinitionIds {@code null} = leave membership unchanged (callers that never knew the
 *     field keep working); a list = the complete membership in display order (G88-F3, §14.3.16)
 */
public record UpdateFieldGroupRequest(
    @NotBlank @Size(max = 100) String name,
    String description,
    int sortOrder,
    Boolean autoApply,
    @Size(max = 10) List<UUID> dependsOn,
    List<@NotNull UUID> fieldDefinitionIds) {

  /** Returns autoApply with null-safe default of false. */
  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }
}
