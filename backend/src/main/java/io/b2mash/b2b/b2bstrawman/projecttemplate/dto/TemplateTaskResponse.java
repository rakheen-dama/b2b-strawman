package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TemplateTaskResponse(
    UUID id,
    String name,
    String description,
    BigDecimal estimatedHours,
    int sortOrder,
    boolean billable,
    String assigneeRole) {}
