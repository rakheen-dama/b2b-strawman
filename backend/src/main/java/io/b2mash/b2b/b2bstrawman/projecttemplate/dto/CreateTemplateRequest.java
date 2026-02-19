package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateTemplateRequest(
    @NotBlank @Size(max = 300) String name,
    @NotBlank @Size(max = 300) String namePattern,
    String description,
    boolean billableDefault,
    @Valid List<TemplateTaskRequest> tasks,
    List<UUID> tagIds) {}
