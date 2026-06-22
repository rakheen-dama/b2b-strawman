package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.crm.event.DealLostEvent;
import io.b2mash.b2b.b2bstrawman.crm.event.DealStageChangedEvent;
import io.b2mash.b2b.b2bstrawman.crm.event.DealWonEvent;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarded lifecycle path for {@link Deal} (Phase 80, slice 575A). This is the <strong>only</strong>
 * code that writes {@code status}/{@code wonAt}/{@code lostAt}/{@code lostReason} via the entity's
 * rich-domain methods ({@code markWon}/{@code markLost}/{@code reopen}/{@code moveToOpenStage}).
 *
 * <p>The transition is driven by the target stage's {@link StageType}:
 *
 * <ul>
 *   <li>OPEN target → re-open a terminal deal, else move between OPEN stages.
 *   <li>WON target → mark won, nudge a PROSPECT customer to ONBOARDING, publish {@link
 *       DealWonEvent} (the DEAL_WON notification is dispatched AFTER_COMMIT by the event handler).
 *   <li>LOST target → require a {@code lostReason}, then mark lost.
 * </ul>
 *
 * <p>Each transition emits a domain event and an audit row ({@code entityType="DEAL"}).
 */
@Service
public class DealTransitionService {

  private static final Logger log = LoggerFactory.getLogger(DealTransitionService.class);

  private final DealRepository dealRepository;
  private final PipelineStageRepository pipelineStageRepository;
  private final CustomerService customerService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public DealTransitionService(
      DealRepository dealRepository,
      PipelineStageRepository pipelineStageRepository,
      CustomerService customerService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.dealRepository = dealRepository;
    this.pipelineStageRepository = pipelineStageRepository;
    this.customerService = customerService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Transitions a deal into the target stage. Dispatches on the target's {@link StageType} and on
   * the deal's current status (re-open vs move). Returns the deal's updated read-model view.
   */
  @Transactional
  public DealResponse transition(UUID dealId, TransitionRequest req) {
    Deal deal = dealRepository.findOneById(dealId);
    PipelineStage target = pipelineStageRepository.findOneById(req.targetStageId());
    Instant now = Instant.now();

    switch (target.getStageType()) {
      case OPEN -> {
        if (DealStatus.TERMINAL_STATUSES.contains(deal.getStatus())) {
          // WON/LOST → OPEN: re-open (clears wonAt/lostAt/lostReason)
          deal.reopen(target.getId());
          publish(new DealStageChangedEvent(dealId, target.getId(), tenant(), org()));
          audit("deal.reopened", deal, Map.of("stage_id", target.getId().toString()));
        } else {
          // OPEN → OPEN: plain move
          deal.moveToOpenStage(target.getId(), req.probabilityOverride());
          publish(new DealStageChangedEvent(dealId, target.getId(), tenant(), org()));
          audit("deal.stage_changed", deal, Map.of("stage_id", target.getId().toString()));
        }
      }
      case WON -> {
        deal.markWon(target.getId(), now);
        customerNudge(deal.getCustomerId());
        publish(
            new DealWonEvent(
                dealId, deal.getCustomerId(), deal.getOwnerId(), tenant(), org(), shard()));
        audit("deal.won", deal, Map.of("value", deal.getValueAmount().toString()));
      }
      case LOST -> {
        if (isBlank(req.lostReason())) {
          throw new InvalidStateException(
              "Invalid deal state", "lostReason required to lose a deal");
        }
        deal.markLost(target.getId(), req.lostReason(), now);
        publish(new DealLostEvent(dealId, req.lostReason(), tenant(), org()));
        audit("deal.lost", deal, Map.of("lost_reason", req.lostReason()));
      }
    }

    int effectiveProb = deal.effectiveProbabilityPct(target.getDefaultProbabilityPct());
    log.info("Transitioned deal {} into stage {} ({})", dealId, target.getId(), deal.getStatus());
    return DealResponse.from(deal, effectiveProb, target.getName());
  }

  /**
   * Only-if-PROSPECT, never downgrade. The fetched customer is managed within this transaction, so
   * mutation flushes at commit. An ACTIVE/DORMANT/etc customer is a clean no-op — a closed deal
   * must never downgrade an existing customer.
   */
  private void customerNudge(UUID customerId) {
    Customer c = customerService.getCustomer(customerId);
    if (c.getLifecycleStatus() == LifecycleStatus.PROSPECT) {
      c.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, RequestScopes.requireMemberId());
    }
  }

  private void audit(String eventType, Deal deal, Map<String, Object> details) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("DEAL")
            .entityId(deal.getId())
            .details(details)
            .build());
  }

  private void publish(Object event) {
    eventPublisher.publishEvent(event);
  }

  private static String tenant() {
    return RequestScopes.getTenantIdOrNull();
  }

  private static String org() {
    return RequestScopes.getOrgIdOrNull();
  }

  private static String shard() {
    return RequestScopes.getShardIdOrDefault();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
