package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TemplateTaskRequest(
    @NotBlank @Size(max = 300) String name,
    String description,
    BigDecimal estimatedHours,
    int sortOrder,
    boolean billable,
    String assigneeRole,
    @Valid List<TemplateTaskItemRequest> items) {}
