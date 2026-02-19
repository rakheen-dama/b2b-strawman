package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectTemplateResponse(
    UUID id,
    String name,
    String namePattern,
    String description,
    boolean billableDefault,
    String source,
    UUID sourceProjectId,
    boolean active,
    int taskCount,
    int tagCount,
    List<TemplateTaskResponse> tasks,
    List<TagResponse> tags,
    Instant createdAt,
    Instant updatedAt) {}
