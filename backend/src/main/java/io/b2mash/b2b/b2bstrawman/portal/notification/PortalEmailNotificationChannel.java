package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalEmailService;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerPeriodRolloverEvent;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
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

  /**
   * Date pattern for the trust-activity nudge email body. Matches the QA-fixture format used by
   * OBS-1101 (e.g. {@code "30 Apr 2026"}) and mirrors the {@code formatLocalDate} helper used by
   * the portal frontend.
   */
  private static final DateTimeFormatter OCCURRED_AT_FMT =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final PortalContactRepository portalContactRepository;
  private final PortalNotificationPreferenceService preferenceService;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;
  private final String portalBaseUrl;
  private final String productName;

  public PortalEmailNotificationChannel(
      PortalContactRepository portalContactRepository,
      PortalNotificationPreferenceService preferenceService,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      OrgSettingsRepository orgSettingsRepository,
      PlatformTransactionManager transactionManager,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.portalContactRepository = portalContactRepository;
    this.preferenceService = preferenceService;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.orgSettingsRepository = orgSettingsRepository;
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
    RequestScopes.runForTenant(
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
    RequestScopes.runForTenant(
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
    RequestScopes.runForTenant(
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
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
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
    String currencyCode = resolveCurrencyCode();
    context.put("amount", amount != null ? amount.toPlainString() : null);
    context.put("amountFormatted", formatAmount(amount, currencyCode));
    context.put("currency", currencyCode);
    context.put("occurredAt", event.occurredAt());
    context.put("occurredAtFormatted", formatOccurredAt(event.occurredAt()));
    // Deep link keys the portal trust page by matter (project) id when present.
    // Falls back to the /trust index page when the transaction has no matter
    // (e.g. cross-customer transfers, or matter id genuinely unset).
    UUID matterId = event.projectId();
    context.put(
        "trustUrl",
        matterId != null ? portalBaseUrl + "/trust/" + matterId : portalBaseUrl + "/trust");
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
    String currencyCode = resolveCurrencyCode();
    context.put("amount", amount != null ? amount.toPlainString() : null);
    context.put("amountFormatted", formatAmount(amount, currencyCode));
    context.put("currency", currencyCode);
    context.put("occurredAt", event.occurredAt());
    context.put("occurredAtFormatted", formatOccurredAt(event.occurredAt()));
    // Deep link keys the portal trust page by matter (project) id when present.
    // Falls back to the /trust index page when the transaction has no matter
    // (e.g. cross-customer transfers, or matter id genuinely unset).
    UUID matterId = event.projectId();
    context.put(
        "trustUrl",
        matterId != null ? portalBaseUrl + "/trust/" + matterId : portalBaseUrl + "/trust");
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

  /**
   * Resolves the tenant's default currency code from {@code OrgSettings}, falling back to {@code
   * "ZAR"} when settings or currency code are not set. Mirrors the fallback used by {@link
   * io.b2mash.b2b.b2bstrawman.expense.ExpenseService} and {@link
   * io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodService}.
   */
  private String resolveCurrencyCode() {
    try {
      return orgSettingsRepository
          .findForCurrentTenant()
          .map(s -> s.getDefaultCurrency())
          .filter(c -> c != null && !c.isBlank())
          .orElse("ZAR");
    } catch (RuntimeException e) {
      log.debug("Falling back to ZAR currency for trust-activity email: {}", e.getMessage());
      return "ZAR";
    }
  }

  /**
   * Formats a {@link BigDecimal} amount as a currency string (e.g. {@code "R 50 000,00"} for ZAR).
   * Returns {@code null} when the amount is null so the template can render an "N/A" fallback.
   *
   * <p>The JDK's locale-data provider does not reliably resolve {@code en-ZA} on every distribution
   * (it falls back to the default currency, producing {@code "$50,000.00"}). To get a stable
   * client-facing render we hard-code the ZA conventions — {@code "R"} symbol, space grouping
   * separator, comma decimal separator — rather than trusting {@code
   * NumberFormat.getCurrencyInstance(locale)} or {@code DecimalFormatSymbols.getInstance(locale)}.
   */
  private static String formatAmount(BigDecimal amount, String currencyCode) {
    if (amount == null) {
      return null;
    }
    String code = (currencyCode == null || currencyCode.isBlank()) ? "ZAR" : currencyCode;

    String symbol;
    char grouping;
    char decimal;
    if ("ZAR".equals(code)) {
      symbol = "R";
      grouping = ' ';
      decimal = ',';
    } else if ("USD".equals(code)) {
      symbol = "$";
      grouping = ',';
      decimal = '.';
    } else if ("GBP".equals(code)) {
      symbol = "£";
      grouping = ',';
      decimal = '.';
    } else if ("EUR".equals(code)) {
      symbol = "€";
      grouping = '.';
      decimal = ',';
    } else {
      // Unknown ISO code — fall through to the JDK's view, with ZA-style grouping defaults.
      try {
        symbol = java.util.Currency.getInstance(code).getSymbol();
      } catch (IllegalArgumentException ignored) {
        symbol = code;
      }
      grouping = ' ';
      decimal = ',';
    }
    java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(Locale.ENGLISH);
    symbols.setGroupingSeparator(grouping);
    symbols.setDecimalSeparator(decimal);
    java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00", symbols);
    return symbol + " " + df.format(amount);
  }

  /**
   * Formats an {@link Instant} timestamp as a calendar-day string (e.g. {@code "30 Apr 2026"}) at
   * UTC. Returns {@code null} for null input so the template can render an "N/A" fallback.
   */
  private static String formatOccurredAt(Instant occurredAt) {
    if (occurredAt == null) {
      return null;
    }
    return occurredAt.atZone(ZoneOffset.UTC).format(OCCURRED_AT_FMT);
  }
}
