package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InstantiateTemplateRequest(
    @Size(max = 255) String name,
    UUID customerId,
    UUID projectLeadMemberId,
    @Size(max = 255) String description,
    @Size(max = 100) String referenceNumber,
    ProjectPriority priority,
    @Size(max = 50) String workType) {}
