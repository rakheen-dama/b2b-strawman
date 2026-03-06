package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RequestReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(RequestReminderScheduler.class);
  private static final long CHECK_INTERVAL_MS = 21_600_000; // 6 hours
  private static final int DEFAULT_REMINDER_INTERVAL_DAYS = 5;

  private final OrgSchemaMappingRepository mappingRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final InformationRequestRepository informationRequestRepository;
  private final RequestItemRepository requestItemRepository;
  private final PortalContactRepository portalContactRepository;
  private final InformationRequestEmailService emailService;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;

  public RequestReminderScheduler(
      OrgSchemaMappingRepository mappingRepository,
      OrgSettingsRepository orgSettingsRepository,
      InformationRequestRepository informationRequestRepository,
      RequestItemRepository requestItemRepository,
      PortalContactRepository portalContactRepository,
      InformationRequestEmailService emailService,
      AuditService auditService,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.informationRequestRepository = informationRequestRepository;
    this.requestItemRepository = requestItemRepository;
    this.portalContactRepository = portalContactRepository;
    this.emailService = emailService;
    this.auditService = auditService;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedRate = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)
  public void checkRequestReminders() {
    log.debug("Request reminder scheduler started");
    var mappings = mappingRepository.findAll();
    int remindersSent = 0;

    for (var mapping : mappings) {
      try {
        int sent =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getExternalOrgId())
                .call(() -> processTenant());
        remindersSent += sent;
      } catch (Exception e) {
        log.error("Failed to process request reminders for schema {}", mapping.getSchemaName(), e);
      }
    }

    log.info("Request reminder scheduler completed: {} reminders sent", remindersSent);
  }

  private int processTenant() {
    var orgSettingsOpt =
        transactionTemplate.execute(tx -> orgSettingsRepository.findForCurrentTenant());

    Integer orgDefaultDays = DEFAULT_REMINDER_INTERVAL_DAYS;
    if (orgSettingsOpt != null && orgSettingsOpt.isPresent()) {
      var orgSettings = orgSettingsOpt.get();
      if (orgSettings.getDefaultRequestReminderDays() != null) {
        orgDefaultDays = orgSettings.getDefaultRequestReminderDays();
      }
    }

    var requests =
        transactionTemplate.execute(
            tx ->
                informationRequestRepository.findByStatusIn(
                    List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS)));

    if (requests == null || requests.isEmpty()) {
      return 0;
    }

    int sent = 0;
    for (var request : requests) {
      int intervalDays =
          request.getReminderIntervalDays() != null
              ? request.getReminderIntervalDays()
              : orgDefaultDays;

      Instant referenceTime =
          request.getLastReminderSentAt() != null
              ? request.getLastReminderSentAt()
              : request.getSentAt();

      if (referenceTime == null) {
        continue;
      }

      long daysSince = Duration.between(referenceTime, Instant.now()).toDays();
      if (daysSince >= intervalDays) {
        if (sendReminder(request.getId(), intervalDays)) {
          sent++;
        }
      }
    }

    return sent;
  }

  /** DTO to carry reminder data between transaction and email send. */
  private record ReminderData(
      String recipientEmail,
      String contactName,
      String requestNumber,
      List<RequestItem> pendingItems,
      UUID requestId) {}

  private boolean sendReminder(UUID requestId, int intervalDays) {
    try {
      // Phase 1: DB operations in a transaction (re-fetch to avoid stale merge)
      var reminderData =
          transactionTemplate.execute(
              tx -> {
                // Re-fetch to get a managed entity and re-check status
                var requestOpt = informationRequestRepository.findById(requestId);
                if (requestOpt.isEmpty()) {
                  return null;
                }
                var request = requestOpt.get();

                // Re-check status — request may have been completed/cancelled concurrently
                if (request.getStatus() != RequestStatus.SENT
                    && request.getStatus() != RequestStatus.IN_PROGRESS) {
                  log.debug(
                      "Request {} is no longer in a remindable state ({}), skipping",
                      request.getRequestNumber(),
                      request.getStatus());
                  return null;
                }

                var items = requestItemRepository.findByRequestIdOrderBySortOrder(request.getId());
                var pendingItems =
                    items.stream()
                        .filter(
                            item ->
                                item.getStatus() == ItemStatus.PENDING
                                    || item.getStatus() == ItemStatus.REJECTED)
                        .toList();

                if (pendingItems.isEmpty()) {
                  return null;
                }

                var contactOpt = portalContactRepository.findById(request.getPortalContactId());
                if (contactOpt.isEmpty()) {
                  log.warn(
                      "Portal contact not found for request {}, skipping reminder",
                      request.getRequestNumber());
                  return null;
                }

                var contact = contactOpt.get();

                // Update lastReminderSentAt on the managed entity
                request.setLastReminderSentAt(Instant.now());
                informationRequestRepository.save(request);

                auditService.log(
                    AuditEventBuilder.builder()
                        .eventType("information_request.reminder_sent")
                        .entityType("information_request")
                        .entityId(request.getId())
                        .actorType("SYSTEM")
                        .source("SCHEDULED")
                        .details(
                            Map.of(
                                "request_number",
                                request.getRequestNumber(),
                                "reminder_interval_days",
                                intervalDays,
                                "outstanding_items",
                                pendingItems.size()))
                        .build());

                return new ReminderData(
                    contact.getEmail(),
                    contact.getDisplayName(),
                    request.getRequestNumber(),
                    pendingItems,
                    request.getId());
              });

      if (reminderData == null) {
        return false;
      }

      // Phase 2: Send email outside the transaction boundary
      emailService.sendReminderEmail(
          reminderData.recipientEmail(),
          reminderData.contactName(),
          reminderData.requestNumber(),
          reminderData.pendingItems(),
          reminderData.requestId());

      return true;
    } catch (Exception e) {
      log.error("Failed to send reminder for request {}", requestId, e);
      return false;
    }
  }
}
