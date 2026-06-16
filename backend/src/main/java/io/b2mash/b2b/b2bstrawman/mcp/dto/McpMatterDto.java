package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.project.Project;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detailed matter projection for {@code get_matter} and the {@code kazi://matter/{id}} resource
 * (§11.4). Flat named fields, short enum names, no ORM-entity serialisation.
 *
 * <p>DRIFT note (vs §11.4): no {@code customerName}/{@code startDate} on {@code Project} — we carry
 * {@code customerId} and use {@code createdAt} for the start-ish timestamp (see {@link
 * McpMatterListItem}). {@code projectRole} is the caller's role on this matter (LEAD/MEMBER/…),
 * supplied by {@code ProjectWithRole}.
 */
public record McpMatterDto(
    UUID id,
    String name,
    String description,
    String status,
    String priority,
    String workType,
    String referenceNumber,
    UUID customerId,
    LocalDate dueDate,
    Instant createdAt,
    Instant updatedAt,
    String projectRole) {

  /** Projects a firm-side {@link Project} entity plus the caller's role into the MCP DTO. */
  public static McpMatterDto from(Project project, String projectRole) {
    return new McpMatterDto(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getStatus() != null ? project.getStatus().name() : null,
        project.getPriority() != null ? project.getPriority().name() : null,
        project.getWorkType(),
        project.getReferenceNumber(),
        project.getCustomerId(),
        project.getDueDate(),
        project.getCreatedAt(),
        project.getUpdatedAt(),
        projectRole);
  }
}
