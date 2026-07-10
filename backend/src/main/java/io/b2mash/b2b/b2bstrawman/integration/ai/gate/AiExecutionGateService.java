package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionService;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiExecutionGateService {

  private static final Logger log = LoggerFactory.getLogger(AiExecutionGateService.class);

  private final AiExecutionGateRepository gateRepository;
  private final GateActionExecutor gateActionExecutor;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final TenantScopedRunner tenantScopedRunner;
  private final TransactionTemplate transactionTemplate;
  private final JobEnqueuer jobEnqueuer;
  private final JobQueueProperties jobQueueProperties;
  private final AiExecutionService aiExecutionService;

  public AiExecutionGateService(
      AiExecutionGateRepository gateRepository,
      GateActionExecutor gateActionExecutor,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      TenantScopedRunner tenantScopedRunner,
      TransactionTemplate transactionTemplate,
      JobEnqueuer jobEnqueuer,
      JobQueueProperties jobQueueProperties,
      AiExecutionService aiExecutionService) {
    this.gateRepository = gateRepository;
    this.gateActionExecutor = gateActionExecutor;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.tenantScopedRunner = tenantScopedRunner;
    this.transactionTemplate = transactionTemplate;
    this.jobEnqueuer = jobEnqueuer;
    this.jobQueueProperties = jobQueueProperties;
    this.aiExecutionService = aiExecutionService;
  }

  /**
   * Creates a new PENDING approval gate (Epic 585, ADR-322). This is the first path that creates an
   * {@link AiExecutionGate} from outside the in-product AI skill flows — the {@code propose_task}
   * MCP write tool calls it after recording a synthetic, zero-cost {@link AiExecution} (BYOC). The
   * gate is created PENDING and stays so until an authorised member approves it in-product
   * (AI_REVIEW), at which point {@link #approve} runs the {@link GateActionExecutor} arm. Mirrors
   * the {@link #approve}/{@link #reject} persist-then-audit shape.
   *
   * <p>No {@code AiGatePending}/{@code AiGateCreated} event is published — no such event exists and
   * no v1 listener needs one. The {@code ai.gate.created} audit row is the mandatory record.
   */
  @Transactional
  public AiExecutionGate createGate(
      AiExecution execution,
      String gateType,
      Map<String, Object> proposedAction,
      String aiReasoning,
      Instant expiresAt) {
    var gate = new AiExecutionGate(execution, gateType, proposedAction, aiReasoning, expiresAt);
    var saved = gateRepository.save(gate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.gate.created")
            .entityType("ai_execution_gate")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "gateType", saved.getGateType(),
                    "executionId", saved.getExecution().getId().toString()))
            .build());

    return saved;
  }

  /**
   * Atomically records a synthetic, zero-cost {@link AiExecution} (BYOC) and creates the PENDING
   * gate it backs, in a <b>single</b> transaction (Epic 585, ADR-322). This is the entry point used
   * by the {@code propose_task} MCP write tool: it keeps the JPA {@link AiExecution}/{@link
   * AiExecutionGate} entities out of the tool layer and guarantees the synthetic execution can
   * never be left orphaned if gate creation fails (both are committed or neither is). {@code
   * recordSyntheticMcpExecution} is itself {@code @Transactional} with the default REQUIRED
   * propagation, so it joins this method's transaction rather than committing independently.
   *
   * @param memberId the authenticated MCP member who invoked the proposal
   * @param correspondenceId the filed email the task is proposed from (the execution's entity and
   *     the gate's correspondence link)
   * @param gateType the gate discriminator, e.g. {@code CREATE_TASK_FROM_CORRESPONDENCE}
   * @param proposedAction the JSONB payload describing the task to create on approval
   * @param aiReasoning a human-readable rationale (TEXT NOT NULL on the gate)
   * @param expiresAt when the gate auto-expires (the existing scheduler reaps it)
   * @return the id of the created PENDING gate
   */
  @Transactional
  public UUID createGateForMcpTaskProposal(
      UUID memberId,
      UUID correspondenceId,
      String gateType,
      Map<String, Object> proposedAction,
      String aiReasoning,
      Instant expiresAt) {
    AiExecution synthetic =
        aiExecutionService.recordSyntheticMcpExecution(memberId, correspondenceId);
    AiExecutionGate gate = createGate(synthetic, gateType, proposedAction, aiReasoning, expiresAt);
    return gate.getId();
  }

  /**
   * Returns the id of an open (PENDING) gate of {@code gateType} already proposed for the given
   * correspondence, if any. Backs the Epic 585 v1 open-gate dedupe guard so a second {@code
   * propose_task} for the same email returns the existing gate instead of creating a duplicate.
   */
  @Transactional(readOnly = true)
  public Optional<UUID> findPendingGateForCorrespondence(UUID correspondenceId, String gateType) {
    return gateRepository.findPendingGateIdForCorrespondence(correspondenceId.toString(), gateType);
  }

  @Transactional
  public AiExecutionGate approve(UUID gateId, UUID reviewerId, String notes) {
    var gate =
        gateRepository
            .findById(gateId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution gate", gateId));
    gate.approve(reviewerId, notes);
    gate = gateRepository.save(gate);

    gateActionExecutor.execute(gate);

    eventPublisher.publishEvent(
        new AiGateApprovedEvent(gate.getId(), gate.getGateType(), reviewerId));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.gate.approved")
            .entityType("ai_execution_gate")
            .entityId(gate.getId())
            .details(
                Map.of(
                    "gateType", gate.getGateType(),
                    "executionId", gate.getExecution().getId().toString()))
            .build());

    return gate;
  }

  @Transactional
  public AiExecutionGate reject(UUID gateId, UUID reviewerId, String notes) {
    var gate =
        gateRepository
            .findById(gateId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution gate", gateId));
    gate.reject(reviewerId, notes);
    gate = gateRepository.save(gate);

    eventPublisher.publishEvent(
        new AiGateRejectedEvent(gate.getId(), gate.getGateType(), reviewerId));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.gate.rejected")
            .entityType("ai_execution_gate")
            .entityId(gate.getId())
            .details(
                Map.of(
                    "gateType", gate.getGateType(),
                    "executionId", gate.getExecution().getId().toString(),
                    "notes", notes != null ? notes : ""))
            .build());

    return gate;
  }

  /** Scheduled job: expire stale gates. Runs every hour. */
  @SchedulerLock(name = "ai_gate_expire_stale_gates", lockAtLeastFor = "30m")
  @Scheduled(fixedRate = 3600000)
  public void expireStaleGates() {
    log.info("AiExecutionGateService: starting hourly gate expiry sweep");

    if (jobQueueProperties.isDualMode("ai_gate_expiry")) {
      int[] totalExpired = {0};

      tenantScopedRunner.forEachTenant(
          (tenantId, orgId) -> {
            totalExpired[0] += expireStaleGatesForTenant();
          });

      log.info("AiExecutionGateService: expired {} stale gates", totalExpired[0]);
    }

    jobEnqueuer.fanOutToAllTenants("ai_gate_expiry", null);
  }

  /**
   * Expires stale gates for the current tenant. Called by both the inline scheduler path
   * (dual-mode) and {@link AiGateExpiryHandler}.
   */
  int expireStaleGatesForTenant() {
    Integer expired =
        transactionTemplate.execute(
            tx -> {
              var staleGates = gateRepository.findPendingExpiredBefore(Instant.now());
              int count = 0;
              for (var gate : staleGates) {
                try {
                  gate.expire();
                  gateRepository.save(gate);
                  count++;

                  auditService.log(
                      AuditEventBuilder.builder()
                          .eventType("ai.gate.expired")
                          .entityType("ai_execution_gate")
                          .entityId(gate.getId())
                          .actorType("SYSTEM")
                          .source("SCHEDULER")
                          .details(
                              Map.of(
                                  "gateType", gate.getGateType(),
                                  "executionId", gate.getExecution().getId().toString()))
                          .build());

                  eventPublisher.publishEvent(
                      new AiGateExpiredEvent(gate.getId(), gate.getGateType()));
                } catch (Exception e) {
                  log.warn("Failed to expire gate {}: {}", gate.getId(), e.getMessage());
                }
              }
              return count;
            });
    return expired != null ? expired : 0;
  }

  /**
   * Expires ONE pending gate because its underlying action became moot (Phase 83, ADR-325: an
   * invoice being paid/voided cancels a pending collection reminder). No-ops (returns {@code
   * false}) when the gate is missing or not PENDING — the approve/reject path may have won the
   * race, which is fine (§5.2).
   *
   * <p>Deliberately does NOT publish {@link AiGateExpiredEvent}: the caller ({@code
   * CollectionsPaymentListener}) performs the activity transition itself, so a re-entrant expiry
   * event would double-process it (flipping the just-cancelled activity back to {@code
   * SKIPPED(gate_expired)}). The {@code ai.gate.expired} audit row is still written.
   */
  @Transactional
  public boolean expirePendingGate(UUID gateId, String reviewNote) {
    var gate = gateRepository.findById(gateId).orElse(null);
    if (gate == null || !"PENDING".equals(gate.getStatus())) {
      return false;
    }
    gate.expire(reviewNote);
    gateRepository.save(gate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.gate.expired")
            .entityType("ai_execution_gate")
            .entityId(gate.getId())
            .actorType("SYSTEM")
            .source("SYSTEM")
            .details(
                Map.of(
                    "gateType", gate.getGateType(),
                    "executionId", gate.getExecution().getId().toString(),
                    "reason", reviewNote != null ? reviewNote : ""))
            .build());
    return true;
  }

  /** List gates with filtering — used by controller. */
  public Page<AiExecutionGate> listGates(String status, String gateType, Pageable pageable) {
    if (gateType != null && status != null) {
      return gateRepository.findByStatusAndGateTypeOrderByCreatedAtDesc(status, gateType, pageable);
    }
    if (status != null) {
      return gateRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }
    if (gateType != null) {
      return gateRepository.findByGateTypeOrderByCreatedAtDesc(gateType, pageable);
    }
    return gateRepository.findAll(pageable);
  }

  /** Get single gate by ID. */
  public AiExecutionGate getGate(UUID gateId) {
    return gateRepository
        .findById(gateId)
        .orElseThrow(() -> new ResourceNotFoundException("Execution gate", gateId));
  }
}
