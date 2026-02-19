package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

  private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

  private final RetentionPolicyRepository policyRepository;
  private final CustomerRepository customerRepository;
  private final AuditEventRepository auditEventRepository;
  private final DocumentRepository documentRepository;
  private final AuditService auditService;

  public RetentionService(
      RetentionPolicyRepository policyRepository,
      CustomerRepository customerRepository,
      AuditEventRepository auditEventRepository,
      DocumentRepository documentRepository,
      AuditService auditService) {
    this.policyRepository = policyRepository;
    this.customerRepository = customerRepository;
    this.auditEventRepository = auditEventRepository;
    this.documentRepository = documentRepository;
    this.auditService = auditService;
  }

  @Transactional
  public RetentionCheckResult runCheck() {
    List<RetentionPolicy> policies = policyRepository.findByActive(true);
    RetentionCheckResult result = new RetentionCheckResult();

    for (RetentionPolicy policy : policies) {
      Instant cutoff = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
      List<UUID> flaggedIds =
          switch (policy.getRecordType()) {
            case "CUSTOMER" -> findExpiredCustomers(policy.getTriggerEvent(), cutoff);
            case "AUDIT_EVENT" -> findExpiredAuditEvents(policy.getTriggerEvent(), cutoff);
            case "DOCUMENT" -> findExpiredDocuments(policy.getTriggerEvent(), cutoff);
            default -> List.of();
          };
      if (!flaggedIds.isEmpty()) {
        result.addFlagged(policy.getRecordType(), policy.getAction(), flaggedIds);
      }
    }

    UUID checkId = UUID.randomUUID();
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retention.check.executed")
            .entityType("retention_policy")
            .entityId(checkId)
            .details(Map.of("totalFlagged", result.getTotalFlagged()))
            .build());

    log.info("Retention check complete — {} total records flagged", result.getTotalFlagged());
    return result;
  }

  private List<UUID> findExpiredCustomers(String triggerEvent, Instant cutoff) {
    if ("CUSTOMER_OFFBOARDED".equals(triggerEvent)) {
      return customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
          LifecycleStatus.OFFBOARDED, cutoff);
    }
    return List.of();
  }

  private List<UUID> findExpiredAuditEvents(String triggerEvent, Instant cutoff) {
    if ("RECORD_CREATED".equals(triggerEvent)) {
      return auditEventRepository.findIdsByOccurredAtBefore(cutoff);
    }
    return List.of();
  }

  private List<UUID> findExpiredDocuments(String triggerEvent, Instant cutoff) {
    if ("CUSTOMER_OFFBOARDED".equals(triggerEvent)) {
      List<UUID> offboardedCustomerIds =
          customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
              LifecycleStatus.OFFBOARDED, Instant.now());
      if (offboardedCustomerIds.isEmpty()) {
        return List.of();
      }
      return documentRepository.findIdsByCustomerIdInAndCreatedAtBefore(
          offboardedCustomerIds, cutoff);
    }
    if ("RECORD_CREATED".equals(triggerEvent)) {
      return documentRepository.findIdsByCreatedAtBefore(cutoff);
    }
    return List.of();
  }
}
