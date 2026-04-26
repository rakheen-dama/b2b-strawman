package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalEmailService;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerPeriodRolloverEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-event portal email channel (Epic 498B, Phase 68, ADR-258). Subscribes to the three portal
 * events that did NOT already have an email listener in Phase 24/28/32/34:
 *
 * <ol>
 *   <li>{@link TrustTransactionApprovalEvent} filtered to the {@code trust_transaction.approved}
 *       variant — renders the {@code portal-trust-activity} template; gated on the portal contact's
 *       {@code trustActivityEnabled} preference.
 *   <li>{@link TrustTransactionRecordedEvent} filtered to {@code transactionType=DEPOSIT} — renders
 *       the same {@code portal-trust-activity} template so a client is notified when the firm
 *       records that funds were received into trust (GAP-Trust-Nudge-Email-Missing). Other
 *       recorded-event types (TRANSFER_OUT/TRANSFER_IN) are skipped here — internal moves do not
 *       warrant a client-facing email; their portal surfacing happens via the read-model sync.
 *   <li>{@link RetainerPeriodRolloverEvent} — renders {@code portal-retainer-period-closed}; gated
 *       on {@code retainerUpdatesEnabled}.
 *   <li>{@link FieldDateApproachingEvent} — renders {@code portal-deadline-approaching}; gated on
 *       {@code deadlineRemindersEnabled}. Customer id is derived from the event's {@code details}
 *       via {@code field_value} / matter lookup is not required here because the portal-facing view
 *       uses the customer/contact relationship.
 * </ol>
 *
 * <p>Per ADR-258 (no-double-send), this channel never subscribes to {@code InvoiceSentEvent},
 * {@code AcceptanceRequestSentEvent}, {@code ProposalSentEvent}, or {@code
 * InformationRequestSentEvent} — those retain their existing Phase-24/28/32/34 listeners.
 *
 * <p>Fire-and-forget: each listener catches exceptions and swallows them so a failed email never
 * rolls back the originating firm-side transaction.
 */
@Component
public class PortalEmailNotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(PortalEmailNotificationChannel.class);

  /** Same ADR-256 window used by {@code DeadlinePortalSyncService}. */
  private static final long DEADLINE_DUE_SOON_DAYS = 7;

  private final PortalContactRepository portalContactRepository;
  private final PortalNotificationPreferenceService preferenceService;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final TransactionTemplate transactionTemplate;
  private final String portalBaseUrl;
  private final String productName;

  public PortalEmailNotificationChannel(
      PortalContactRepository portalContactRepository,
      PortalNotificationPreferenceService preferenceService,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      PlatformTransactionManager transactionManager,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.portalContactRepository = portalContactRepository;
    this.preferenceService = preferenceService;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    // Build a REQUIRES_NEW template so dispatch writes from an AFTER_COMMIT listener always
    // run inside a fresh tenant transaction (the originating tx has already committed and
    // unbound its EM/session by the time the listener fires).
    TransactionTemplate tpl = new TransactionTemplate(transactionManager);
    tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transactionTemplate = tpl;
    this.portalBaseUrl = portalBaseUrl;
    this.productName = productName;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Trust activity — approved variant only
  // ──────────────────────────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTrustTransactionApproval(TrustTransactionApprovalEvent event) {
    if (!"trust_transaction.approved".equals(event.eventType())) {
      return;
    }
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            dispatchToContacts(
                event.customerId(),
                PortalNotificationPreference::isTrustActivityEnabled,
                contact -> buildTrustActivityContext(contact, event),
                portalEmailService::sendTrustActivityEmail,
                "trust-activity");
          } catch (Exception e) {
            log.error(
                "Portal trust-activity email failed tenant={} customerId={}",
                event.tenantId(),
                event.customerId(),
                e);
          }
        });
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Trust deposit recorded (GAP-Trust-Nudge-Email-Missing)
  //
  // recordDeposit() bypasses the awaiting-approval flow and goes straight to RECORDED, so the
  // approval-path listener above never fires for deposits. Without this listener, clients only
  // surface deposits by logging into the portal — never via email. Filter is restricted to
  // DEPOSIT so we do not double-send when the closure-pack notifications (E5.1) ship a
  // listener for WITHDRAWAL/FEE_TRANSFER/REFUND. TRANSFER_OUT / TRANSFER_IN are internal moves
  // that the read-model sync surfaces via the portal trust ledger; no client-facing email.
  // ──────────────────────────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTrustTransactionRecorded(TrustTransactionRecordedEvent event) {
    if (!"DEPOSIT".equals(event.transactionType())) {
      return;
    }
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            dispatchToContacts(
                event.customerId(),
                PortalNotificationPreference::isTrustActivityEnabled,
                contact -> buildTrustActivityContext(contact, event),
                portalEmailService::sendTrustActivityEmail,
                "trust-deposit-recorded");
          } catch (Exception e) {
            log.error(
                "Portal trust-deposit email failed tenant={} customerId={}",
                event.tenantId(),
                event.customerId(),
                e);
          }
        });
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Retainer period closed
  // ──────────────────────────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRetainerPeriodRollover(RetainerPeriodRolloverEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            dispatchToContacts(
                event.customerId(),
                PortalNotificationPreference::isRetainerUpdatesEnabled,
                contact -> buildRetainerClosedContext(contact, event),
                portalEmailService::sendRetainerClosedEmail,
                "retainer-closed");
          } catch (Exception e) {
            log.error(
                "Portal retainer-closed email failed tenant={} customerId={}",
                event.tenantId(),
                event.customerId(),
                e);
          }
        });
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Field date approaching (custom-field deadline)
  // ──────────────────────────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFieldDateApproaching(FieldDateApproachingEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            UUID customerId = resolveCustomerIdFromEvent(event);
            if (customerId == null) {
              log.warn(
                  "Skipping portal deadline email -- no customer linkage for entityType={} "
                      + "entityId={} (TODO: project/task/invoice resolution is a follow-up)",
                  event.entityType(),
                  event.entityId());
              return;
            }
            dispatchToContacts(
                customerId,
                PortalNotificationPreference::isDeadlineRemindersEnabled,
                contact -> buildDeadlineContext(contact, event),
                portalEmailService::sendDeadlineReminderEmail,
                "deadline-approaching");
          } catch (Exception e) {
            log.error(
                "Portal deadline-approaching email failed tenant={} entityId={}",
                event.tenantId(),
                event.entityId(),
                e);
          }
        });
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Shared dispatch path
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Resolves the portal contacts for {@code customerId}, filters to ACTIVE + non-blank email +
   * per-contact preference, then renders and sends via {@code sender}. The {@code contextBuilder}
   * receives the contact and returns the fully-populated template context.
   */
  private void dispatchToContacts(
      UUID customerId,
      Function<PortalNotificationPreference, Boolean> preferenceCheck,
      Function<PortalContact, Map<String, Object>> contextBuilder,
      java.util.function.BiFunction<PortalContact, Map<String, Object>, Boolean> sender,
      String label) {
    if (customerId == null) {
      return;
    }
    List<PortalContact> contacts =
        transactionTemplate.execute(
            tx ->
                portalContactRepository.findByCustomerId(customerId).stream()
                    .filter(c -> c.getStatus() == PortalContact.ContactStatus.ACTIVE)
                    .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
                    .toList());
    if (contacts == null || contacts.isEmpty()) {
      return;
    }
    for (PortalContact contact : contacts) {
      try {
        var pref =
            transactionTemplate.execute(tx -> preferenceService.getOrCreate(contact.getId()));
        if (pref == null || !preferenceCheck.apply(pref)) {
          continue;
        }
        Map<String, Object> context = contextBuilder.apply(contact);
        // Wrap the send in a tenant tx so the delivery-log write inside PortalEmailService
        // joins a real transaction (we're running AFTER_COMMIT — the originating tx is gone).
        transactionTemplate.execute(
            tx -> {
              sender.apply(contact, context);
              return null;
            });
      } catch (Exception e) {
        log.warn(
            "Portal {} email dispatch failed for contact {} in tenant {}: {}",
            label,
            contact.getId(),
            RequestScopes.getTenantIdOrNull(),
            e.getMessage());
      }
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Context builders (one per event type)
  // ──────────────────────────────────────────────────────────────────────────

  private Map<String, Object> buildTrustActivityContext(
      PortalContact contact, TrustTransactionApprovalEvent event) {
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put("subject", orgName + ": Trust account activity");
    context.put("contactName", contact.getDisplayName());
    context.put("transactionType", event.transactionType());
    BigDecimal amount = event.amount();
    context.put("amount", amount != null ? amount.toPlainString() : null);
    context.put("currency", context.getOrDefault("currency", ""));
    context.put("occurredAt", event.occurredAt());
    // Deep link keys the portal trust page by matter id when present.
    UUID matterKey = event.trustAccountId();
    context.put(
        "trustUrl",
        matterKey != null ? portalBaseUrl + "/trust/" + matterKey : portalBaseUrl + "/trust");
    context.put("portalBaseUrl", portalBaseUrl);
    return context;
  }

  /**
   * Variant for the RECORDED-path event (currently DEPOSIT only). Mirrors the approval-path subject
   * line so portal contacts see a single, consistent "Trust account activity" framing whether the
   * underlying transaction went through the dual-approval flow or straight to RECORDED.
   */
  private Map<String, Object> buildTrustActivityContext(
      PortalContact contact, TrustTransactionRecordedEvent event) {
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put("subject", orgName + ": Trust account activity");
    context.put("contactName", contact.getDisplayName());
    context.put("transactionType", event.transactionType());
    BigDecimal amount = event.amount();
    context.put("amount", amount != null ? amount.toPlainString() : null);
    context.put("currency", context.getOrDefault("currency", ""));
    context.put("occurredAt", event.occurredAt());
    UUID matterKey = event.trustAccountId();
    context.put(
        "trustUrl",
        matterKey != null ? portalBaseUrl + "/trust/" + matterKey : portalBaseUrl + "/trust");
    context.put("portalBaseUrl", portalBaseUrl);
    return context;
  }

  private Map<String, Object> buildRetainerClosedContext(
      PortalContact contact, RetainerPeriodRolloverEvent event) {
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put("subject", orgName + ": Your retainer period has closed");
    context.put("contactName", contact.getDisplayName());
    context.put("closedPeriodId", event.closedPeriodId());
    context.put("newPeriodStart", event.newPeriodStart());
    context.put("newPeriodEnd", event.newPeriodEnd());
    context.put("nextRenewalDate", event.nextRenewalDate());
    BigDecimal allocated = event.allocatedHours();
    BigDecimal rollover = event.rolloverHoursOut();
    context.put("allocatedHours", allocated != null ? allocated.toPlainString() : null);
    context.put("rolloverHours", rollover != null ? rollover.toPlainString() : null);
    context.put(
        "retainerUrl",
        event.agreementId() != null
            ? portalBaseUrl + "/retainer/" + event.agreementId()
            : portalBaseUrl + "/retainer");
    context.put("portalBaseUrl", portalBaseUrl);
    return context;
  }

  private Map<String, Object> buildDeadlineContext(
      PortalContact contact, FieldDateApproachingEvent event) {
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    Map<String, Object> details = event.details() != null ? event.details() : Map.of();

    String label =
        firstNonBlank(
            stringOrNull(details.get("field_label")),
            stringOrNull(details.get("field_name")),
            "Upcoming deadline");
    String fieldValue = stringOrNull(details.get("field_value"));

    LocalDate dueDate = null;
    if (fieldValue != null) {
      try {
        dueDate = LocalDate.parse(fieldValue);
      } catch (java.time.format.DateTimeParseException ignored) {
        // Non-ISO date — fall through; deep link still works.
      }
    }

    long daysUntil = -1;
    if (details.get("days_until") instanceof Number n) {
      daysUntil = n.longValue();
    } else if (dueDate != null) {
      daysUntil = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), dueDate);
    }

    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put(
        "subject", "Upcoming deadline: " + label + (dueDate != null ? " on " + dueDate : ""));
    context.put("orgName", orgName);
    context.put("contactName", contact.getDisplayName());
    context.put("label", label);
    context.put("dueDate", dueDate);
    context.put("daysUntil", daysUntil);
    context.put("entityName", stringOrNull(details.get("entity_name")));
    context.put("deadlineUrl", portalBaseUrl + "/deadlines");
    context.put("portalBaseUrl", portalBaseUrl);
    context.put("dueSoonThresholdDays", DEADLINE_DUE_SOON_DAYS);
    return context;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Resolves the customer id for a {@link FieldDateApproachingEvent}. For {@code CUSTOMER} entity
   * types the entity id IS the customer id; for other entity types we return null (no portal
   * audience).
   *
   * <p>TODO(portal-notifications): resolve PROJECT / TASK / INVOICE entity types via {@code
   * CustomerProjectRepository}, mirroring {@code DeadlinePortalSyncService}. Until that lands,
   * non-CUSTOMER deadline events silently drop their portal audience — a follow-up ticket is
   * required before broadening the FieldDateApproachingEvent publisher to non-CUSTOMER entities.
   */
  private UUID resolveCustomerIdFromEvent(FieldDateApproachingEvent event) {
    String rawEntityType = event.entityType();
    if (rawEntityType == null) {
      return null;
    }
    // Custom-field dates on CUSTOMER entity — direct customer id.
    if ("CUSTOMER".equalsIgnoreCase(rawEntityType)) {
      return event.entityId();
    }
    // For other entity types (PROJECT, TASK, INVOICE) we have no direct customer linkage here;
    // DeadlinePortalSyncService performs that resolution via CustomerProjectRepository.
    // Phase 68 scope covers CUSTOMER-level dates; project-level resolution is a follow-up.
    return null;
  }

  private static String firstNonBlank(String... candidates) {
    for (String c : candidates) {
      if (c != null && !c.isBlank()) {
        return c;
      }
    }
    return null;
  }

  private static String stringOrNull(Object o) {
    if (o == null) {
      return null;
    }
    String s = o.toString();
    return s.isBlank() ? null : s;
  }

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      log.warn("Portal email channel event received without tenantId — skipping");
      return;
    }
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }
}
