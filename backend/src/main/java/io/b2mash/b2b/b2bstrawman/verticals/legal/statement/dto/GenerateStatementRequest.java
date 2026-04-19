package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/matters/{projectId}/statements} (architecture §67.4.3). The
 * optional {@code templateId} lets a caller select a non-default Statement of Account template; if
 * null the system template (slug {@code statement-of-account}) is resolved by the service.
 */
public record GenerateStatementRequest(
    @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, UUID templateId) {}
