package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateFieldGroupRequest(
    @NotBlank @Size(max = 100) String name,
    String description,
    int sortOrder,
    Boolean autoApply,
    List<UUID> dependsOn) {

  /** Returns autoApply with null-safe default of false. */
  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }
}
