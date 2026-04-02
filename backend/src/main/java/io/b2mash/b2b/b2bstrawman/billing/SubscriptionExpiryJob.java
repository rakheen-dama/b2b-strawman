package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that processes subscription expiry transitions daily. Runs three methods at
 * staggered times to handle trial expiry (3:00 AM), grace period expiry (3:05 AM), and pending
 * cancellation end (3:10 AM).
 *
 * <p>Not {@code @Transactional} — each subscription is saved individually so that audit events
 * (which write to tenant schemas via ScopedValue) are isolated from the public-schema subscription
 * save.
 */
@Component
public class SubscriptionExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

  private final SubscriptionRepository subscriptionRepository;
  private final BillingProperties billingProperties;
  private final SubscriptionStatusCache statusCache;
  private final AuditService auditService;
  private final OrganizationRepository organizationRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;

  public SubscriptionExpiryJob(
      SubscriptionRepository subscriptionRepository,
      BillingProperties billingProperties,
      SubscriptionStatusCache statusCache,
      AuditService auditService,
      OrganizationRepository organizationRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository) {
    this.subscriptionRepository = subscriptionRepository;
    this.billingProperties = billingProperties;
    this.statusCache = statusCache;
    this.auditService = auditService;
    this.organizationRepository = organizationRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
  }

  /** Transitions TRIALING subscriptions past their trial end date to EXPIRED with grace period. */
  @Scheduled(cron = "0 0 3 * * *")
  public void processTrialExpiry() {
    log.info("Trial expiry job started");
    var now = Instant.now();
    var expired =
        subscriptionRepository.findBySubscriptionStatusAndTrialEndsAtBefore(
            Subscription.SubscriptionStatus.TRIALING, now);

    int count = 0;
    for (var sub : expired) {
      try {
        sub.transitionTo(Subscription.SubscriptionStatus.EXPIRED);
        sub.setGraceEndsAt(now.plus(Duration.ofDays(billingProperties.gracePeriodDays())));
        subscriptionRepository.save(sub);
        statusCache.evict(sub.getOrganizationId());
        logAuditEvent(
            sub,
            "subscription.trial_expired",
            Map.of(
                "organization_id", sub.getOrganizationId().toString(),
                "previous_status", "TRIALING",
                "new_status", "EXPIRED"));
        count++;
      } catch (Exception e) {
        log.error(
            "Failed to process trial expiry for subscription {}: {}",
            sub.getId(),
            e.getMessage(),
            e);
      }
    }

    log.info("Trial expiry job completed: {} subscriptions transitioned", count);
  }

  /**
   * Transitions GRACE_PERIOD, EXPIRED, and SUSPENDED subscriptions past their grace end date to
   * LOCKED.
   */
  @Scheduled(cron = "0 5 3 * * *")
  public void processGraceExpiry() {
    log.info("Grace period expiry job started");
    var now = Instant.now();
    var expired =
        subscriptionRepository.findBySubscriptionStatusInAndGraceEndsAtBefore(
            List.of(
                Subscription.SubscriptionStatus.GRACE_PERIOD,
                Subscription.SubscriptionStatus.EXPIRED,
                Subscription.SubscriptionStatus.SUSPENDED),
            now);

    int count = 0;
    for (var sub : expired) {
      try {
        var previousStatus = sub.getSubscriptionStatus().name();
        sub.transitionTo(Subscription.SubscriptionStatus.LOCKED);
        subscriptionRepository.save(sub);
        statusCache.evict(sub.getOrganizationId());
        logAuditEvent(
            sub,
            "subscription.locked",
            Map.of(
                "organization_id",
                sub.getOrganizationId().toString(),
                "previous_status",
                previousStatus,
                "new_status",
                "LOCKED"));
        count++;
      } catch (Exception e) {
        log.error(
            "Failed to process grace expiry for subscription {}: {}",
            sub.getId(),
            e.getMessage(),
            e);
      }
    }

    log.info("Grace period expiry job completed: {} subscriptions transitioned", count);
  }

  /**
   * Transitions PENDING_CANCELLATION subscriptions past their current period end to GRACE_PERIOD
   * with grace period set.
   */
  @Scheduled(cron = "0 10 3 * * *")
  public void processPendingCancellationEnd() {
    log.info("Pending cancellation end job started");
    var now = Instant.now();
    var ended =
        subscriptionRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(
            Subscription.SubscriptionStatus.PENDING_CANCELLATION, now);

    int count = 0;
    for (var sub : ended) {
      try {
        if (sub.getCurrentPeriodEnd() == null) {
          log.warn("Subscription {} has null currentPeriodEnd, skipping", sub.getId());
          continue;
        }
        sub.transitionTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
        sub.setGraceEndsAt(
            sub.getCurrentPeriodEnd().plus(Duration.ofDays(billingProperties.gracePeriodDays())));
        subscriptionRepository.save(sub);
        statusCache.evict(sub.getOrganizationId());
        logAuditEvent(
            sub,
            "subscription.cancellation_effective",
            Map.of(
                "organization_id", sub.getOrganizationId().toString(),
                "previous_status", "PENDING_CANCELLATION",
                "new_status", "GRACE_PERIOD"));
        count++;
      } catch (Exception e) {
        log.error(
            "Failed to process pending cancellation end for subscription {}: {}",
            sub.getId(),
            e.getMessage(),
            e);
      }
    }

    log.info("Pending cancellation end job completed: {} subscriptions transitioned", count);
  }

  private void logAuditEvent(Subscription sub, String eventType, Map<String, Object> details) {
    var orgOpt = organizationRepository.findById(sub.getOrganizationId());
    if (orgOpt.isEmpty()) {
      log.debug(
          "Organization {} not found, skipping audit for subscription {}",
          sub.getOrganizationId(),
          sub.getId());
      return;
    }

    var mappingOpt =
        orgSchemaMappingRepository.findByExternalOrgId(orgOpt.get().getExternalOrgId());
    if (mappingOpt.isEmpty()) {
      log.debug(
          "Schema mapping not found for org {}, skipping audit for subscription {}",
          orgOpt.get().getExternalOrgId(),
          sub.getId());
      return;
    }

    var mapping = mappingOpt.get();
    try {
      ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
          .where(RequestScopes.ORG_ID, mapping.getExternalOrgId())
          .run(
              () -> {
                auditService.log(
                    AuditEventBuilder.builder()
                        .eventType(eventType)
                        .entityType("subscription")
                        .entityId(sub.getId())
                        .actorType("SYSTEM")
                        .source("SCHEDULED")
                        .details(details)
                        .build());
              });
    } catch (Exception e) {
      log.warn("Failed to create audit event for subscription {}: {}", sub.getId(), e.getMessage());
    }
  }
}
