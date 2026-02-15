package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.CustomerStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerLifecycleService {

  private final CustomerRepository customerRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberRepository memberRepository;
  private final OrgSettingsRepository orgSettingsRepository;

  public CustomerLifecycleService(
      CustomerRepository customerRepository,
      CustomerLifecycleGuard customerLifecycleGuard,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberRepository memberRepository,
      OrgSettingsRepository orgSettingsRepository) {
    this.customerRepository = customerRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberRepository = memberRepository;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional
  public Customer transitionCustomer(UUID customerId, String targetStatus, String notes) {
    var customer =
        customerRepository
            .findOneById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    String oldStatus = customer.getLifecycleStatus();

    customerLifecycleGuard.requireTransitionValid(customer, targetStatus, notes);

    UUID changedBy = RequestScopes.requireMemberId();
    Instant now = Instant.now();

    // Determine offboardedAt value
    Instant offboardedAt = customer.getOffboardedAt();
    if ("OFFBOARDED".equals(targetStatus)) {
      offboardedAt = now;
    } else if ("OFFBOARDED".equals(oldStatus) && "ACTIVE".equals(targetStatus)) {
      offboardedAt = null;
    }

    customer.transitionLifecycle(targetStatus, changedBy, now, offboardedAt);
    customer = customerRepository.save(customer);

    // Emit audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.status_changed")
            .entityType("customer")
            .entityId(customerId)
            .details(
                Map.of(
                    "old_status", oldStatus,
                    "new_status", targetStatus,
                    "notes", notes != null ? notes : ""))
            .build());

    // Emit domain event for notifications
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    String actorName = resolveActorName(changedBy);

    eventPublisher.publishEvent(
        new CustomerStatusChangedEvent(
            "customer.status_changed",
            "customer",
            customerId,
            null, // no project
            changedBy,
            actorName,
            tenantId,
            orgId,
            now,
            Map.of(
                "old_status", oldStatus,
                "new_status", targetStatus,
                "notes", notes != null ? notes : ""),
            customerId,
            customer.getName(),
            oldStatus,
            targetStatus));

    return customer;
  }

  @Transactional(readOnly = true)
  public List<Customer> checkDormancy() {
    int thresholdDays =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(s -> s.getDormancyThresholdDays() != null ? s.getDormancyThresholdDays() : 90)
            .orElse(90);

    Instant threshold = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);
    return customerRepository.findActiveCustomersWithoutActivitySince(threshold);
  }

  @Transactional(readOnly = true)
  public int getDormancyThresholdDays() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> s.getDormancyThresholdDays() != null ? s.getDormancyThresholdDays() : 90)
        .orElse(90);
  }

  private String resolveActorName(UUID memberId) {
    if (memberId == null) return "System";
    return memberRepository.findOneById(memberId).map(m -> m.getName()).orElse("Unknown");
  }
}
