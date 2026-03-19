package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
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
  private final CommentRepository commentRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final AuditService auditService;
  private final StorageService storageService;
  private final NotificationService notificationService;

  public RetentionService(
      RetentionPolicyRepository policyRepository,
      CustomerRepository customerRepository,
      AuditEventRepository auditEventRepository,
      DocumentRepository documentRepository,
      CommentRepository commentRepository,
      TimeEntryRepository timeEntryRepository,
      AuditService auditService,
      StorageService storageService,
      NotificationService notificationService) {
    this.policyRepository = policyRepository;
    this.customerRepository = customerRepository;
    this.auditEventRepository = auditEventRepository;
    this.documentRepository = documentRepository;
    this.commentRepository = commentRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.auditService = auditService;
    this.storageService = storageService;
    this.notificationService = notificationService;
  }

  @Transactional
  public RetentionCheckResult runCheck() {
    List<RetentionPolicy> policies = policyRepository.findByActive(true);
    RetentionCheckResult result = new RetentionCheckResult();
    Instant now = Instant.now();

    for (RetentionPolicy policy : policies) {
      Instant cutoff = now.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
      List<UUID> flaggedIds =
          switch (policy.getRecordType()) {
            case "CUSTOMER" -> findExpiredCustomers(policy.getTriggerEvent(), cutoff);
            case "AUDIT_EVENT" -> findExpiredAuditEvents(policy.getTriggerEvent(), cutoff);
            case "DOCUMENT" -> findExpiredDocuments(policy.getTriggerEvent(), cutoff);
            case "TIME_ENTRY" ->
                findExpiredTimeEntries(
                    policy.getTriggerEvent(), LocalDate.now().minusDays(policy.getRetentionDays()));
            default -> List.of();
          };
      if (!flaggedIds.isEmpty()) {
        result.addFlagged(
            policy.getRecordType(), policy.getTriggerEvent(), policy.getAction(), flaggedIds);
      }

      // 30-day warning notifications for approaching records
      evaluateWarnings(policy, now);

      // Update lastEvaluatedAt after evaluating each policy
      policy.setLastEvaluatedAt(Instant.now());
      policyRepository.save(policy);
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

  /**
   * Evaluates records approaching their retention deadline within 30 days and sends warning
   * notifications to org admins and owners.
   */
  private void evaluateWarnings(RetentionPolicy policy, Instant now) {
    if (policy.getRetentionDays() <= 30) {
      return; // No meaningful warning window for policies <= 30 days
    }

    List<UUID> approachingIds;

    if ("TIME_ENTRY".equals(policy.getRecordType())) {
      LocalDate warnCutoff = LocalDate.now().minusDays(policy.getRetentionDays() - 30);
      LocalDate expiredCutoff = LocalDate.now().minusDays(policy.getRetentionDays());
      approachingIds =
          findApproachingTimeEntries(policy.getTriggerEvent(), warnCutoff, expiredCutoff);
    } else {
      Instant warnCutoff = now.minus(policy.getRetentionDays() - 30, ChronoUnit.DAYS);
      Instant expiredCutoff = now.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
      approachingIds =
          switch (policy.getRecordType()) {
            case "CUSTOMER" ->
                findApproachingCustomers(policy.getTriggerEvent(), warnCutoff, expiredCutoff);
            case "AUDIT_EVENT" ->
                findApproachingAuditEvents(policy.getTriggerEvent(), warnCutoff, expiredCutoff);
            case "DOCUMENT" ->
                findApproachingDocuments(policy.getTriggerEvent(), warnCutoff, expiredCutoff);
            default -> List.of();
          };
    }

    if (!approachingIds.isEmpty()) {
      LocalDate purgeDate = LocalDate.now().plusDays(30);
      notificationService.notifyAdminsAndOwners(
          "RETENTION_PURGE_WARNING",
          "Retention warning: "
              + approachingIds.size()
              + " "
              + policy.getRecordType()
              + "(s) will be purged by retention policy on "
              + purgeDate,
          null,
          "retention_policy",
          policy.getId());
      log.info(
          "Retention warning sent: {} {} record(s) approaching deadline",
          approachingIds.size(),
          policy.getRecordType());
    }
  }

  private List<UUID> findApproachingCustomers(
      String triggerEvent, Instant warnCutoff, Instant expiredCutoff) {
    if ("CUSTOMER_OFFBOARDED".equals(triggerEvent)) {
      List<UUID> allBeforeWarn =
          customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
              LifecycleStatus.OFFBOARDED, warnCutoff);
      List<UUID> alreadyExpired =
          customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
              LifecycleStatus.OFFBOARDED, expiredCutoff);
      return allBeforeWarn.stream().filter(id -> !alreadyExpired.contains(id)).toList();
    }
    return List.of();
  }

  private List<UUID> findApproachingAuditEvents(
      String triggerEvent, Instant warnCutoff, Instant expiredCutoff) {
    if ("RECORD_CREATED".equals(triggerEvent)) {
      List<UUID> allBeforeWarn = auditEventRepository.findIdsByOccurredAtBefore(warnCutoff);
      List<UUID> alreadyExpired = auditEventRepository.findIdsByOccurredAtBefore(expiredCutoff);
      return allBeforeWarn.stream().filter(id -> !alreadyExpired.contains(id)).toList();
    }
    return List.of();
  }

  private List<UUID> findApproachingDocuments(
      String triggerEvent, Instant warnCutoff, Instant expiredCutoff) {
    if ("RECORD_CREATED".equals(triggerEvent)) {
      List<UUID> allBeforeWarn = documentRepository.findIdsByCreatedAtBefore(warnCutoff);
      List<UUID> alreadyExpired = documentRepository.findIdsByCreatedAtBefore(expiredCutoff);
      return allBeforeWarn.stream().filter(id -> !alreadyExpired.contains(id)).toList();
    }
    return List.of();
  }

  private List<UUID> findApproachingTimeEntries(
      String triggerEvent, LocalDate warnCutoff, LocalDate expiredCutoff) {
    if ("RECORD_CREATED".equals(triggerEvent)) {
      List<UUID> allBeforeWarn = timeEntryRepository.findIdsByDateBefore(warnCutoff);
      List<UUID> alreadyExpired = timeEntryRepository.findIdsByDateBefore(expiredCutoff);
      return allBeforeWarn.stream().filter(id -> !alreadyExpired.contains(id)).toList();
    }
    return List.of();
  }

  /** Dry-run evaluation that returns flagged records without executing any purge actions. */
  @Transactional(readOnly = true)
  public RetentionCheckResult previewPurge() {
    List<RetentionPolicy> policies = policyRepository.findByActive(true);
    RetentionCheckResult result = new RetentionCheckResult();
    Instant now = Instant.now();

    for (RetentionPolicy policy : policies) {
      Instant cutoff = now.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
      List<UUID> flaggedIds =
          switch (policy.getRecordType()) {
            case "CUSTOMER" -> findExpiredCustomers(policy.getTriggerEvent(), cutoff);
            case "AUDIT_EVENT" -> findExpiredAuditEvents(policy.getTriggerEvent(), cutoff);
            case "DOCUMENT" -> findExpiredDocuments(policy.getTriggerEvent(), cutoff);
            case "TIME_ENTRY" ->
                findExpiredTimeEntries(
                    policy.getTriggerEvent(), LocalDate.now().minusDays(policy.getRetentionDays()));
            default -> List.of();
          };
      if (!flaggedIds.isEmpty()) {
        result.addFlagged(
            policy.getRecordType(), policy.getTriggerEvent(), policy.getAction(), flaggedIds);
      }
    }
    return result;
  }

  /**
   * Seeds default retention policies for the given jurisdiction. Idempotent — skips record types
   * that already have policies.
   */
  @Transactional
  public void seedJurisdictionDefaults(String jurisdiction) {
    if (!"ZA".equals(jurisdiction)) {
      return; // Only ZA defaults implemented in this epic
    }
    seedIfAbsent(
        "CUSTOMER",
        1800,
        "RECORD_CREATED",
        "anonymize",
        "Client personal information (POPIA: 5 years)");
    seedIfAbsent(
        "TIME_ENTRY", 1800, "RECORD_CREATED", "delete", "Time records (Income Tax Act: 5 years)");
    seedIfAbsent(
        "DOCUMENT", 1800, "RECORD_CREATED", "delete", "Client documents (VAT Act: 5 years)");
    seedIfAbsent("COMMENT", 1080, "RECORD_CREATED", "delete", "Communication records (3 years)");
    seedIfAbsent(
        "AUDIT_EVENT", 2520, "RECORD_CREATED", "delete", "Audit trail (best practice: 7 years)");
  }

  private void seedIfAbsent(
      String recordType,
      int retentionDays,
      String triggerEvent,
      String action,
      String description) {
    if (!policyRepository.existsByRecordTypeAndTriggerEvent(recordType, triggerEvent)) {
      var policy = new RetentionPolicy(recordType, retentionDays, triggerEvent, action);
      policy.setDescription(description);
      policyRepository.save(policy);
    }
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

  @Transactional
  public PurgeResult executePurge(String recordType, List<UUID> recordIds) {
    if (recordIds == null || recordIds.isEmpty()) {
      return new PurgeResult(recordType, 0, 0);
    }

    int purged = 0;
    int failed = 0;

    switch (recordType) {
      case "CUSTOMER" -> {
        for (UUID customerId : recordIds) {
          try {
            var customerOpt = customerRepository.findById(customerId);
            if (customerOpt.isPresent()) {
              var customer = customerOpt.get();
              String hash = customerId.toString().substring(0, 6);
              customer.anonymize("Anonymized Customer " + hash);
              customerRepository.save(customer);
              purged++;
            }
          } catch (Exception e) {
            log.warn(
                "Failed to anonymize customer {} during retention purge: {}",
                customerId,
                e.getMessage());
            failed++;
          }
        }
      }
      case "AUDIT_EVENT" -> {
        try {
          auditEventRepository.deleteAllById(recordIds);
          purged = recordIds.size();
        } catch (Exception e) {
          log.warn("Failed to delete audit events during retention purge: {}", e.getMessage());
          failed = recordIds.size();
        }
      }
      case "COMMENT" -> {
        try {
          commentRepository.deleteAllById(recordIds);
          purged = recordIds.size();
        } catch (Exception e) {
          log.warn("Failed to delete comments during retention purge: {}", e.getMessage());
          failed = recordIds.size();
        }
      }
      case "DOCUMENT" -> {
        for (UUID docId : recordIds) {
          try {
            var docOpt = documentRepository.findById(docId);
            if (docOpt.isPresent()) {
              var doc = docOpt.get();
              if (doc.getS3Key() != null && !"pending".equals(doc.getS3Key())) {
                storageService.delete(doc.getS3Key());
              }
              documentRepository.delete(doc);
              purged++;
            }
          } catch (Exception e) {
            log.warn(
                "Failed to delete document {} during retention purge: {}", docId, e.getMessage());
            failed++;
          }
        }
      }
      case "TIME_ENTRY" -> {
        try {
          timeEntryRepository.deleteAllById(recordIds);
          purged = recordIds.size();
        } catch (Exception e) {
          log.warn("Failed to delete time entries during retention purge: {}", e.getMessage());
          failed = recordIds.size();
        }
      }
      default -> log.warn("Unknown recordType '{}' for retention purge — skipping", recordType);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retention.purge.executed")
            .entityType("retention_policy")
            .entityId(UUID.randomUUID())
            .details(Map.of("recordType", recordType, "count", purged, "failed", failed))
            .build());

    log.info(
        "Retention purge complete — recordType={}, purged={}, failed={}",
        recordType,
        purged,
        failed);
    return new PurgeResult(recordType, purged, failed);
  }

  public record PurgeResult(String recordType, int purged, int failed) {}

  private List<UUID> findExpiredTimeEntries(String triggerEvent, LocalDate cutoffDate) {
    if ("RECORD_CREATED".equals(triggerEvent)) {
      return timeEntryRepository.findIdsByDateBefore(cutoffDate);
    }
    return List.of();
  }

  private List<UUID> findExpiredDocuments(String triggerEvent, Instant cutoff) {
    if ("CUSTOMER_OFFBOARDED".equals(triggerEvent)) {
      List<UUID> offboardedCustomerIds =
          customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
              LifecycleStatus.OFFBOARDED, cutoff);
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
