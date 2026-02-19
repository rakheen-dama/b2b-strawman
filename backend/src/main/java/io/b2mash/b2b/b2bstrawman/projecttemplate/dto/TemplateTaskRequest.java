package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TemplateTaskRequest(
    @NotBlank @Size(max = 300) String name,
    String description,
    BigDecimal estimatedHours,
    int sortOrder,
    boolean billable,
    String assigneeRole) {}
