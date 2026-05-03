package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplierRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for AI specialist invocation lifecycle and review-queue actions.
 *
 * <p>Capability gating: every entry point requires {@code AI_ASSISTANT_USE}. Cross-actor approve /
 * reject / retry additionally require {@code TEAM_OVERSIGHT}, enforced at runtime against the
 * caller's member id.
 */
@Service
public class AiSpecialistInvocationService {

  /** Hard cap for {@link #bulkApprove}. */
  public static final int BULK_APPROVE_MAX = 25;

  private static final String CAP_AI = "AI_ASSISTANT_USE";
  private static final String CAP_OVERSIGHT = "TEAM_OVERSIGHT";

  private final AiSpecialistInvocationRepository repository;
  private final OutputApplierRegistry outputApplierRegistry;
  private final CapabilityAuthorizationService capabilityAuthorizationService;
  private final AuditService auditService;
  private final ApplicationEventPublisher applicationEventPublisher;

  public AiSpecialistInvocationService(
      AiSpecialistInvocationRepository repository,
      OutputApplierRegistry outputApplierRegistry,
      CapabilityAuthorizationService capabilityAuthorizationService,
      AuditService auditService,
      ApplicationEventPublisher applicationEventPublisher) {
    this.repository = repository;
    this.outputApplierRegistry = outputApplierRegistry;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
    this.auditService = auditService;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  // ------------------------- write entry points -------------------------

  @Transactional
  public AiSpecialistInvocation recordRunning(
      String specialistId,
      InvocationSource source,
      UUID actorId,
      UUID actionExecutionId,
      String contextEntityType,
      UUID contextEntityId,
      String promptVersion) {
    var inv =
        new AiSpecialistInvocation(
            specialistId,
            source,
            actorId,
            actionExecutionId,
            contextEntityType,
            contextEntityId,
            promptVersion);
    return repository.save(inv);
  }

  @Transactional
  public void recordProposal(UUID invocationId, OutputPayload proposed) {
    var inv = loadOrThrow(invocationId);
    inv.recordProposal(proposed);
    repository.save(inv);
  }

  @Transactional
  public void markPendingApproval(UUID invocationId) {
    var inv = loadOrThrow(invocationId);
    inv.markPendingApproval();
    repository.save(inv);
  }

  @Transactional
  public ApproveResult approve(UUID id, OutputPayload edited) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    var inv = loadOrThrow(id);
    inv.requireStatus(InvocationStatus.PENDING_APPROVAL);
    UUID callerId = RequestScopes.requireMemberId();
    if (!inv.getActorId().equals(callerId)) {
      capabilityAuthorizationService.requireCapability(CAP_OVERSIGHT);
    }
    OutputPayload toApply = edited != null ? edited : inv.getProposedOutput();
    if (toApply == null) {
      throw new InvalidStateException(
          "Missing payload", "Invocation has no proposed output and no edited payload supplied");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    var applier = outputApplierRegistry.forPayload(toApply);
    applier.apply(toApply, callerId);
    inv.markApproved(callerId, toApply);
    saveWithLockGuard(inv);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.specialist.approved")
            .entityType(inv.getContextEntityType())
            .entityId(inv.getContextEntityId())
            .details(
                Map.of(
                    "specialistId", inv.getSpecialistId(),
                    "invocationId", inv.getId().toString()))
            .build());
    applicationEventPublisher.publishEvent(new AiInvocationApprovedEvent(inv));
    return new ApproveResult(inv.getId(), inv.getStatus(), inv.getReviewedAt());
  }

  @Transactional
  public void reject(UUID id, String rejectReason) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    var inv = loadOrThrow(id);
    inv.requireStatus(InvocationStatus.PENDING_APPROVAL);
    UUID callerId = RequestScopes.requireMemberId();
    if (!inv.getActorId().equals(callerId)) {
      capabilityAuthorizationService.requireCapability(CAP_OVERSIGHT);
    }
    inv.markRejected(callerId, rejectReason);
    saveWithLockGuard(inv);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.specialist.rejected")
            .entityType(inv.getContextEntityType())
            .entityId(inv.getContextEntityId())
            .details(
                Map.of(
                    "specialistId", inv.getSpecialistId(),
                    "invocationId", inv.getId().toString(),
                    "reason", rejectReason == null ? "" : rejectReason))
            .build());
    applicationEventPublisher.publishEvent(new AiInvocationRejectedEvent(inv, rejectReason));
  }

  @Transactional
  public void retry(UUID id) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    var inv = loadOrThrow(id);
    UUID callerId = RequestScopes.requireMemberId();
    if (!inv.getActorId().equals(callerId)) {
      capabilityAuthorizationService.requireCapability(CAP_OVERSIGHT);
    }
    inv.resetToRunning();
    saveWithLockGuard(inv);
  }

  @Transactional
  public BulkApproveResult bulkApprove(List<UUID> ids) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    if (ids == null || ids.isEmpty()) {
      throw new InvalidStateException(
          "Empty bulk-approve", "At least one invocation id is required");
    }
    if (ids.size() > BULK_APPROVE_MAX) {
      throw new InvalidStateException(
          "Bulk-approve cap exceeded",
          "At most " + BULK_APPROVE_MAX + " invocations may be approved per request");
    }
    var loaded = repository.findAllById(ids);
    if (loaded.size() != ids.size()) {
      throw new ResourceNotFoundException("AiSpecialistInvocation", ids);
    }
    Set<String> specialistIds = new LinkedHashSet<>();
    for (var inv : loaded) {
      specialistIds.add(inv.getSpecialistId());
    }
    if (specialistIds.size() > 1) {
      throw new InvalidStateException(
          "Mixed specialist", "Bulk-approve requires all invocations share the same specialistId");
    }
    var outcomes = new ArrayList<BulkApproveOutcome>(loaded.size());
    for (var inv : loaded) {
      try {
        var result = approve(inv.getId(), null);
        outcomes.add(new BulkApproveOutcome(inv.getId(), result.status(), null));
      } catch (RuntimeException ex) {
        outcomes.add(new BulkApproveOutcome(inv.getId(), inv.getStatus(), ex.getMessage()));
      }
    }
    return new BulkApproveResult(outcomes);
  }

  // ------------------------- read entry points -------------------------

  @Transactional(readOnly = true)
  public AiSpecialistInvocation findById(UUID id) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    var inv = loadOrThrow(id);
    UUID callerId = RequestScopes.requireMemberId();
    if (!Objects.equals(inv.getActorId(), callerId)
        && !capabilityAuthorizationService.hasCapability(CAP_OVERSIGHT)) {
      // Cross-actor visibility requires TEAM_OVERSIGHT; treat as not-found to avoid leaking
      // the existence of other members' invocations.
      throw new ResourceNotFoundException("AiSpecialistInvocation", id);
    }
    return inv;
  }

  @Transactional(readOnly = true)
  public Page<AiSpecialistInvocation> findByFilter(InvocationFilter filter, Pageable pageable) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    UUID callerId = RequestScopes.requireMemberId();
    boolean hasOversight = capabilityAuthorizationService.hasCapability(CAP_OVERSIGHT);
    InvocationFilter scoped =
        hasOversight
            ? filter
            : new InvocationFilter(
                filter.status(),
                filter.specialistId(),
                filter.from(),
                filter.to(),
                filter.contextEntityType(),
                filter.contextEntityId(),
                callerId);
    return repository.findAll(buildSpec(scoped), pageable);
  }

  // ------------------------- internals -------------------------

  private AiSpecialistInvocation loadOrThrow(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("AiSpecialistInvocation", id));
  }

  private void saveWithLockGuard(AiSpecialistInvocation inv) {
    try {
      repository.save(inv);
    } catch (ObjectOptimisticLockingFailureException ole) {
      throw new ResourceConflictException(
          "Invocation already updated",
          "Another reviewer modified this invocation; refresh and retry");
    }
  }

  private static Specification<AiSpecialistInvocation> buildSpec(InvocationFilter f) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (f.status() != null) {
        predicates.add(cb.equal(root.get("status"), f.status()));
      }
      if (f.specialistId() != null) {
        predicates.add(cb.equal(root.get("specialistId"), f.specialistId()));
      }
      if (f.contextEntityType() != null) {
        predicates.add(cb.equal(root.get("contextEntityType"), f.contextEntityType()));
      }
      if (f.contextEntityId() != null) {
        predicates.add(cb.equal(root.get("contextEntityId"), f.contextEntityId()));
      }
      if (f.actorId() != null) {
        predicates.add(cb.equal(root.get("actorId"), f.actorId()));
      }
      if (f.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
      }
      if (f.to() != null) {
        predicates.add(cb.lessThan(root.get("createdAt"), f.to()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  // ------------------------- result types -------------------------

  public record ApproveResult(UUID id, InvocationStatus status, Instant appliedAt) {}

  public record BulkApproveOutcome(UUID id, InvocationStatus status, String error) {}

  public record BulkApproveResult(List<BulkApproveOutcome> outcomes) {}

  public record InvocationFilter(
      InvocationStatus status,
      String specialistId,
      Instant from,
      Instant to,
      String contextEntityType,
      UUID contextEntityId,
      UUID actorId) {}
}
