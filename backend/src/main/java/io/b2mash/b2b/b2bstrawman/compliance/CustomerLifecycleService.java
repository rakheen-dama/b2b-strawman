package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerController.DormancyCandidate;
import io.b2mash.b2b.b2bstrawman.customer.CustomerController.DormancyCheckResult;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerLifecycleService {

  private static final Logger log = LoggerFactory.getLogger(CustomerLifecycleService.class);

  private static final String DORMANCY_SQL =
      """
      SELECT c.id, c.name,
        GREATEST(
          MAX(te.created_at),
          MAX(t.updated_at),
          MAX(d.created_at),
          MAX(i.created_at),
          MAX(cm.created_at)
        ) AS last_activity
      FROM customers c
      LEFT JOIN customer_projects cp ON cp.customer_id = c.id
      LEFT JOIN tasks t ON t.project_id = cp.project_id
      LEFT JOIN time_entries te ON te.task_id = t.id
      LEFT JOIN documents d ON d.customer_id = c.id
      LEFT JOIN invoices i ON i.customer_id = c.id
      LEFT JOIN comments cm ON cm.entity_id = d.id AND cm.entity_type = 'DOCUMENT'
      WHERE c.lifecycle_status = 'ACTIVE'
        AND c.status = 'ACTIVE'
      GROUP BY c.id, c.name
      HAVING GREATEST(
          MAX(te.created_at), MAX(t.updated_at), MAX(d.created_at),
          MAX(i.created_at), MAX(cm.created_at)
        ) < CAST(:cutoffDate AS TIMESTAMPTZ)
         OR GREATEST(
          MAX(te.created_at), MAX(t.updated_at), MAX(d.created_at),
          MAX(i.created_at), MAX(cm.created_at)
        ) IS NULL
      ORDER BY last_activity ASC NULLS FIRST
      """;

  private final CustomerRepository customerRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final OrgSettingsRepository orgSettingsRepository;
  private final EntityManager entityManager;

  public CustomerLifecycleService(
      CustomerRepository customerRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      OrgSettingsRepository orgSettingsRepository,
      EntityManager entityManager) {
    this.customerRepository = customerRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.orgSettingsRepository = orgSettingsRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public Customer transition(UUID customerId, String targetStatus, String notes, UUID actorId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    LifecycleStatus target;
    try {
      target = LifecycleStatus.from(targetStatus);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid lifecycle status", e.getMessage());
    }

    var oldStatus = customer.getLifecycleStatus();

    // Stub: check onboarding guard when transitioning ONBOARDING -> ACTIVE
    if (oldStatus == LifecycleStatus.ONBOARDING && target == LifecycleStatus.ACTIVE) {
      checkOnboardingGuard(customer);
    }

    // Entity method validates the transition and throws InvalidStateException if invalid
    customer.transitionLifecycleStatus(target, actorId);

    // Clear offboardedAt when reactivating from OFFBOARDED
    if (oldStatus == LifecycleStatus.OFFBOARDED) {
      customer.setOffboardedAt(null);
    }

    customer = customerRepository.save(customer);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.lifecycle.transitioned")
            .entityType("customer")
            .entityId(customerId)
            .details(
                Map.of(
                    "oldStatus",
                    oldStatus.name(),
                    "newStatus",
                    target.name(),
                    "notes",
                    notes != null ? notes : ""))
            .build());

    eventPublisher.publishEvent(
        new CustomerStatusChangedEvent(this, customerId, oldStatus.name(), target.name()));

    log.info(
        "Customer {} lifecycle transitioned from {} to {} by actor {}",
        customerId,
        oldStatus,
        target,
        actorId);

    return customer;
  }

  @Transactional(readOnly = true)
  public DormancyCheckResult runDormancyCheck() {
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    int thresholdDays =
        (settings != null && settings.getDormancyThresholdDays() != null)
            ? settings.getDormancyThresholdDays()
            : 90;

    Instant cutoffDate = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);

    @SuppressWarnings("unchecked")
    List<Tuple> rows =
        entityManager
            .createNativeQuery(DORMANCY_SQL, Tuple.class)
            .setParameter("cutoffDate", cutoffDate)
            .getResultList();

    var candidates =
        rows.stream()
            .map(
                row -> {
                  var lastActivity = row.get("last_activity");
                  Instant lastActivityInstant =
                      lastActivity != null ? ((Timestamp) lastActivity).toInstant() : null;
                  long daysSince =
                      lastActivityInstant != null
                          ? ChronoUnit.DAYS.between(lastActivityInstant, Instant.now())
                          : thresholdDays;
                  return new DormancyCandidate(
                      (UUID) row.get("id"),
                      (String) row.get("name"),
                      lastActivityInstant,
                      daysSince);
                })
            .toList();

    log.info(
        "Dormancy check completed: threshold={} days, candidates={}",
        thresholdDays,
        candidates.size());

    return new DormancyCheckResult(thresholdDays, candidates);
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> getLifecycleHistory(UUID customerId) {
    var filter =
        new AuditEventFilter("customer", customerId, null, "customer.lifecycle.", null, null);
    var page = auditService.findEvents(filter, PageRequest.of(0, 100));
    return page.getContent();
  }

  /**
   * Stub: checks that all onboarding checklist instances for this customer are completed. TODO Epic
   * 103B: check instanceRepository.existsByCustomerIdAndStatusNot(customerId, 'COMPLETED')
   */
  void checkOnboardingGuard(Customer customer) {
    // No-op stub — checklist infrastructure does not exist yet
  }
}
