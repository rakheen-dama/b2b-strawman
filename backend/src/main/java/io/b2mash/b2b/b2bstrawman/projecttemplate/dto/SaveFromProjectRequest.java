package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveFromProjectRequest(
    @NotBlank @Size(max = 300) String name,
    @NotBlank @Size(max = 300) String namePattern,
    String description,
    List<UUID> taskIds,
    List<UUID> tagIds,
    Map<UUID, String> taskRoles) {}
