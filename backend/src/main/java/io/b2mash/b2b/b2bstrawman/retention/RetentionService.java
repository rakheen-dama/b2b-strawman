package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class RetentionService {

  private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

  private final RetentionPolicyRepository policyRepository;
  private final CustomerRepository customerRepository;
  private final AuditEventRepository auditEventRepository;
  private final DocumentRepository documentRepository;
  private final CommentRepository commentRepository;
  private final AuditService auditService;
  private final S3Client s3Client;
  private final S3Properties s3Properties;

  public RetentionService(
      RetentionPolicyRepository policyRepository,
      CustomerRepository customerRepository,
      AuditEventRepository auditEventRepository,
      DocumentRepository documentRepository,
      CommentRepository commentRepository,
      AuditService auditService,
      S3Client s3Client,
      S3Properties s3Properties) {
    this.policyRepository = policyRepository;
    this.customerRepository = customerRepository;
    this.auditEventRepository = auditEventRepository;
    this.documentRepository = documentRepository;
    this.commentRepository = commentRepository;
    this.auditService = auditService;
    this.s3Client = s3Client;
    this.s3Properties = s3Properties;
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
            default -> List.of();
          };
      if (!flaggedIds.isEmpty()) {
        result.addFlagged(
            policy.getRecordType(), policy.getTriggerEvent(), policy.getAction(), flaggedIds);
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
                try {
                  s3Client.deleteObject(
                      DeleteObjectRequest.builder()
                          .bucket(s3Properties.bucketName())
                          .key(doc.getS3Key())
                          .build());
                } catch (Exception e) {
                  log.warn(
                      "Best-effort S3 deletion failed for key={} — object may remain as orphan:"
                          + " {}",
                      doc.getS3Key(),
                      e.getMessage());
                }
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
