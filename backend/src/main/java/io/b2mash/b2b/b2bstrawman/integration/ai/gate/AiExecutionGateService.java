package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Instant;
import java.util.Map;
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

  public AiExecutionGateService(
      AiExecutionGateRepository gateRepository,
      GateActionExecutor gateActionExecutor,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      TenantScopedRunner tenantScopedRunner,
      TransactionTemplate transactionTemplate) {
    this.gateRepository = gateRepository;
    this.gateActionExecutor = gateActionExecutor;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.tenantScopedRunner = tenantScopedRunner;
    this.transactionTemplate = transactionTemplate;
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
    int[] totalExpired = {0};

    tenantScopedRunner.forEachTenant(
        (tenantId, orgId) -> {
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
          if (expired != null) {
            totalExpired[0] += expired;
          }
        });

    log.info("AiExecutionGateService: expired {} stale gates", totalExpired[0]);
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
