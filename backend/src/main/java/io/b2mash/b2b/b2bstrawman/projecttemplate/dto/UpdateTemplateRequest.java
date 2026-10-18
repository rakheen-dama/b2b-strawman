package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateTemplateRequest(
    @NotBlank @Size(max = 300) String name,
    @NotBlank @Size(max = 300) String namePattern,
    String description,
    boolean billableDefault,
    @Valid List<TemplateTaskRequest> tasks,
    List<UUID> tagIds,
    UUID requestTemplateId,
    @Size(max = 50, message = "workType must be at most 50 characters") String workType) {}
