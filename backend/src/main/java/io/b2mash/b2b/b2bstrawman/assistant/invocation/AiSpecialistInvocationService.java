package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplierRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final AiSpecialistInvocationService selfProxy;

  public AiSpecialistInvocationService(
      AiSpecialistInvocationRepository repository,
      OutputApplierRegistry outputApplierRegistry,
      CapabilityAuthorizationService capabilityAuthorizationService,
      AuditService auditService,
      ApplicationEventPublisher applicationEventPublisher,
      @Lazy @Autowired AiSpecialistInvocationService selfProxy) {
    this.repository = repository;
    this.outputApplierRegistry = outputApplierRegistry;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
    this.auditService = auditService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.selfProxy = selfProxy;
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
  public void markFailed(UUID invocationId, String errorMessage) {
    var inv = loadOrThrow(invocationId);
    inv.markFailed(errorMessage);
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
    applicationEventPublisher.publishEvent(AiInvocationApprovedEvent.of(inv));
    return new ApproveResult(inv.getId(), inv.getStatus(), inv.getReviewedAt());
  }

  /**
   * REQUIRES_NEW variant of {@link #approve} used by {@link #bulkApprove}. Each id runs in its own
   * physical transaction so a failure on one id does not poison the outer transaction (which would
   * otherwise be marked rollback-only and lose siblings whose outcomes say APPROVED).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ApproveResult approveInNewTransaction(UUID id, OutputPayload edited) {
    return approve(id, edited);
  }

  @Transactional
  public void reject(UUID id, String rejectReason) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    if (rejectReason == null || rejectReason.isBlank()) {
      throw new InvalidStateException(
          "Missing reject reason", "rejectReason is required and must not be blank");
    }
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
                    "reason", rejectReason))
            .build());
    applicationEventPublisher.publishEvent(AiInvocationRejectedEvent.of(inv, rejectReason));
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

  /**
   * Bulk-approve up to {@link #BULK_APPROVE_MAX} invocations of the same specialist. Each id runs
   * in its own physical transaction (via {@link #approveInNewTransaction}) so a failure on one id
   * does not roll back successful siblings — per the brief contract.
   *
   * <p>NOTE: deliberately not annotated {@code @Transactional} at the bulk level. If the outer call
   * carried a transaction, an inner approve() failure would mark it rollback-only and Spring would
   * throw {@code UnexpectedRollbackException} at commit, undoing the siblings whose outcomes
   * recorded APPROVED.
   */
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
        var result = selfProxy.approveInNewTransaction(inv.getId(), null);
        outcomes.add(new BulkApproveOutcome(inv.getId(), result.status(), null));
      } catch (RuntimeException ex) {
        outcomes.add(new BulkApproveOutcome(inv.getId(), inv.getStatus(), ex.getMessage()));
      }
    }
    return new BulkApproveResult(outcomes);
  }

  /**
   * Atomically records a RUNNING invocation, applies the payload via the registered applier, and
   * marks the invocation AUTO_APPLIED — all within a single transaction. Used by DIRECT-mode tool
   * execution to prevent partial state (e.g. comment posted but invocation stuck in RUNNING).
   */
  @Transactional
  public AiSpecialistInvocation recordAndAutoApply(
      String specialistId,
      InvocationSource source,
      UUID actorId,
      String contextEntityType,
      UUID contextEntityId,
      String promptVersion,
      OutputPayload payload) {
    var inv =
        new AiSpecialistInvocation(
            specialistId, source, actorId, null, contextEntityType, contextEntityId, promptVersion);
    inv = repository.save(inv);

    var applier = outputApplierRegistry.forPayload(payload);
    applier.apply(payload, actorId);

    inv.markAutoApplied(payload);
    return repository.save(inv);
  }

  /**
   * Applies an output payload to an existing RUNNING invocation and marks it AUTO_APPLIED. Used by
   * DIRECT-mode automation where the invocation is recorded before the specialist runs (so that the
   * runner has a real invocation ID for LLM call telemetry).
   */
  @Transactional
  public void autoApply(UUID invocationId, OutputPayload payload) {
    var inv = loadOrThrow(invocationId);
    var applier = outputApplierRegistry.forPayload(payload);
    applier.apply(payload, inv.getActorId());
    inv.markAutoApplied(payload);
    repository.save(inv);
  }

  // ------------------------- read entry points -------------------------

  @Transactional(readOnly = true)
  public AiSpecialistInvocation findById(UUID id) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    var inv = loadOrThrow(id);
    UUID callerId = RequestScopes.requireMemberId();
    if (!Objects.equals(inv.getActorId(), callerId)
        && !capabilityAuthorizationService.hasCapability(CAP_OVERSIGHT)) {
      // Brief §515A.6 specifies 403 for cross-actor access without TEAM_OVERSIGHT.
      throw new ForbiddenException(
          "Cross-actor access denied",
          "Viewing another member's invocation requires TEAM_OVERSIGHT");
    }
    return inv;
  }

  @Transactional(readOnly = true)
  public Page<AiSpecialistInvocation> findByFilter(InvocationFilter filter, Pageable pageable) {
    capabilityAuthorizationService.requireCapability(CAP_AI);
    UUID callerId = RequestScopes.requireMemberId();
    boolean hasOversight = capabilityAuthorizationService.hasCapability(CAP_OVERSIGHT);
    InvocationFilter scoped;
    if (hasOversight) {
      scoped = filter;
    } else {
      // Without TEAM_OVERSIGHT, a caller may only query their own invocations. Reject an
      // explicit filter for someone else rather than silently rewriting it (which would
      // otherwise hide the access denial behind an empty result page).
      if (filter.actorId() != null && !filter.actorId().equals(callerId)) {
        throw new ForbiddenException(
            "Cross-actor filter denied",
            "Filtering by another member's actorId requires TEAM_OVERSIGHT");
      }
      scoped =
          new InvocationFilter(
              filter.status(),
              filter.specialistId(),
              filter.from(),
              filter.to(),
              filter.contextEntityType(),
              filter.contextEntityId(),
              callerId);
    }
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
      // saveAndFlush forces immediate version check; otherwise Hibernate may defer the
      // optimistic-lock failure to commit time, where it escapes this try/catch and surfaces
      // as a 500 instead of the intended 409 ResourceConflictException.
      repository.saveAndFlush(inv);
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
