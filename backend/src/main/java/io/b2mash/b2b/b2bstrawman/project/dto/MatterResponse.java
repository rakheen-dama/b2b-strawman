package io.b2mash.b2b.b2bstrawman.project.dto;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire DTO for a matter (Phase 87, ADR-337). Entity in, matter out: {@code Project} is the JPA
 * entity and keeps its name; only this record and its JSON components are legal-native. No
 * {@code @JsonProperty} — the component name IS the JSON name.
 *
 * <p>{@code retentionEndsOn} is the persisted {@code projects.retention_ends_on} (Phase 88 U17,
 * ADR-354) on every factory: stamped at close, cleared on reopen, so it is null unless the matter
 * is CLOSED. There is no read-time computation or fallback.
 */
public record MatterResponse(
    UUID id,
    String name,
    String description,
    String status,
    UUID clientId,
    LocalDate dueDate,
    UUID createdBy,
    String createdByName,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    UUID completedBy,
    String completedByName,
    Instant archivedAt,
    Instant retentionClockStartedAt,
    LocalDate retentionEndsOn,
    String matterRole,
    Map<String, Object> customFields,
    List<UUID> appliedFieldGroups,
    List<TagResponse> tags,
    String referenceNumber,
    ProjectPriority priority,
    String workType) {

  public static MatterResponse from(Project project) {
    return from(project, Map.of());
  }

  public static MatterResponse from(Project project, Map<UUID, String> memberNames) {
    return new MatterResponse(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getStatus().name(),
        project.getCustomerId(),
        project.getDueDate(),
        project.getCreatedBy(),
        project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
        project.getCreatedAt(),
        project.getUpdatedAt(),
        project.getCompletedAt(),
        project.getCompletedBy(),
        project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
        project.getArchivedAt(),
        project.getRetentionClockStartedAt(),
        project.getRetentionEndsOn(),
        null,
        project.getCustomFields(),
        project.getAppliedFieldGroups(),
        List.of(),
        project.getReferenceNumber(),
        project.getPriority(),
        project.getWorkType());
  }

  public static MatterResponse from(
      Project project, String matterRole, Map<UUID, String> memberNames) {
    return new MatterResponse(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getStatus().name(),
        project.getCustomerId(),
        project.getDueDate(),
        project.getCreatedBy(),
        project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
        project.getCreatedAt(),
        project.getUpdatedAt(),
        project.getCompletedAt(),
        project.getCompletedBy(),
        project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
        project.getArchivedAt(),
        project.getRetentionClockStartedAt(),
        project.getRetentionEndsOn(),
        matterRole,
        project.getCustomFields(),
        project.getAppliedFieldGroups(),
        List.of(),
        project.getReferenceNumber(),
        project.getPriority(),
        project.getWorkType());
  }

  public static MatterResponse from(
      Project project, String matterRole, List<TagResponse> tags, Map<UUID, String> memberNames) {
    return new MatterResponse(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getStatus().name(),
        project.getCustomerId(),
        project.getDueDate(),
        project.getCreatedBy(),
        project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
        project.getCreatedAt(),
        project.getUpdatedAt(),
        project.getCompletedAt(),
        project.getCompletedBy(),
        project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
        project.getArchivedAt(),
        project.getRetentionClockStartedAt(),
        project.getRetentionEndsOn(),
        matterRole,
        project.getCustomFields(),
        project.getAppliedFieldGroups(),
        tags,
        project.getReferenceNumber(),
        project.getPriority(),
        project.getWorkType());
  }
}
