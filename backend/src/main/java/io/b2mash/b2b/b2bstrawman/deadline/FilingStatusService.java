package io.b2mash.b2b.b2bstrawman.deadline;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilingStatusService {

  private final FilingStatusRepository repository;
  private final AuditService auditService;

  public FilingStatusService(FilingStatusRepository repository, AuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  public record CreateFilingStatusRequest(
      @NotNull UUID customerId,
      @NotBlank String deadlineTypeSlug,
      @NotBlank String periodKey,
      @NotBlank String status,
      String notes,
      UUID linkedProjectId) {}

  public record BatchUpdateRequest(@NotEmpty List<@Valid CreateFilingStatusRequest> items) {}

  public record FilingStatusResponse(
      UUID id,
      UUID customerId,
      String deadlineTypeSlug,
      String periodKey,
      String status,
      Instant filedAt,
      String notes,
      UUID linkedProjectId,
      Instant createdAt) {}

  @Transactional
  public FilingStatusResponse upsert(CreateFilingStatusRequest request, UUID memberId) {
    var existing =
        repository.findByCustomerIdAndDeadlineTypeSlugAndPeriodKey(
            request.customerId(), request.deadlineTypeSlug(), request.periodKey());

    FilingStatus entity;
    if (existing.isPresent()) {
      entity = existing.get();
      entity.setStatus(request.status());
      entity.setNotes(request.notes());
      entity.setLinkedProjectId(request.linkedProjectId());
      entity.setFiledBy(memberId);
      if ("filed".equals(request.status())) {
        entity.setFiledAt(Instant.now());
      } else {
        entity.setFiledAt(null);
      }
      entity.setUpdatedAt(Instant.now());
    } else {
      entity =
          new FilingStatus(
              request.customerId(),
              request.deadlineTypeSlug(),
              request.periodKey(),
              request.status());
      entity.setNotes(request.notes());
      entity.setLinkedProjectId(request.linkedProjectId());
      entity.setFiledBy(memberId);
      if ("filed".equals(request.status())) {
        entity.setFiledAt(Instant.now());
      }
    }

    var saved = repository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("filing_status.updated")
            .entityType("filing_status")
            .entityId(saved.getId())
            .actorId(memberId)
            .actorType("USER")
            .source("INTERNAL")
            .details(
                Map.of(
                    "customerId", request.customerId().toString(),
                    "deadlineTypeSlug", request.deadlineTypeSlug(),
                    "periodKey", request.periodKey(),
                    "status", request.status(),
                    "notes", request.notes() != null ? request.notes() : ""))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public List<FilingStatusResponse> batchUpsert(BatchUpdateRequest request, UUID memberId) {
    return request.items().stream().map(item -> upsert(item, memberId)).toList();
  }

  @Transactional(readOnly = true)
  public List<FilingStatusResponse> list(UUID customerId, String deadlineTypeSlug, String status) {
    List<FilingStatus> results;
    if (deadlineTypeSlug != null && status != null) {
      results =
          repository.findByCustomerIdAndDeadlineTypeSlugAndStatus(
              customerId, deadlineTypeSlug, status);
    } else if (deadlineTypeSlug != null) {
      results = repository.findByCustomerIdAndDeadlineTypeSlug(customerId, deadlineTypeSlug);
    } else if (status != null) {
      results = repository.findByCustomerIdAndStatus(customerId, status);
    } else {
      results = repository.findByCustomerId(customerId);
    }
    return results.stream().map(this::toResponse).toList();
  }

  private FilingStatusResponse toResponse(FilingStatus entity) {
    return new FilingStatusResponse(
        entity.getId(),
        entity.getCustomerId(),
        entity.getDeadlineTypeSlug(),
        entity.getPeriodKey(),
        entity.getStatus(),
        entity.getFiledAt(),
        entity.getNotes(),
        entity.getLinkedProjectId(),
        entity.getCreatedAt());
  }
}
