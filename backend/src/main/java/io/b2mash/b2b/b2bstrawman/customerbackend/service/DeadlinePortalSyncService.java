package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.event.CourtDateCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.CourtDateScheduledEvent;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineClearedEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineSetEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Syncs firm-side deadline activity into the unified portal read-model ({@code
 * portal.portal_deadline_view}). Listens in {@link TransactionPhase#AFTER_COMMIT} so only committed
 * firm-side changes reach portal contacts; failures are swallowed and logged — a sync failure must
 * never roll back the originating firm transaction (mirrors {@code TrustLedgerPortalSyncService},
 * see ADR-253).
 *
 * <p>Per ADR-256, the portal surfaces four deadline sources behind a single polymorphic row, keyed
 * on {@code (source_entity, id)}:
 *
 * <ol>
 *   <li><b>FILING_SCHEDULE</b> — Phase 51 filing schedule lifecycle. <i>Dormant</i>: {@code
 *       FilingScheduleCreatedEvent} / {@code FilingStatusChangedEvent} do not yet exist in the
 *       codebase. When Phase 51 wires these events, add an {@code @TransactionalEventListener}
 *       method and call {@link #upsertFromDeadline(DeadlineSource)} with the filing-row fields.
 *   <li><b>COURT_DATE</b> — {@link CourtDateScheduledEvent} / {@link CourtDateCancelledEvent}
 *       (Phase 89 633B, ADR-363). <b>Wired.</b> Scheduled (create, portal-relevant update, the new
 *       row of a postpone, outcome → {@code COMPLETED}) upserts; cancel and the original row of a
 *       postpone DELETE the portal row (B4).
 *   <li><b>PRESCRIPTION_TRACKER</b> — {@link PrescriptionDeadlineSetEvent} / {@link
 *       PrescriptionDeadlineClearedEvent} (Phase 89 633B). <b>Wired.</b> Create / update upsert;
 *       interrupt DELETES the portal row (B4).
 *   <li><b>CUSTOM_FIELD_DATE</b> — Phase 48 {@link FieldDateApproachingEvent}. <b>Wired.</b> Gated
 *       on {@code FieldDefinition.portalVisibleDeadline == true} (ADR-257) — custom-field dates
 *       only leak to the portal when an admin has explicitly opted-in per field.
 * </ol>
 *
 * <p>Status derivation happens at sync time (ADR-253), not at read time. See {@link
 * #deriveStatus(LocalDate, LocalDate)}: past due_date → {@code OVERDUE}, within 7 days → {@code
 * DUE_SOON}, otherwise {@code UPCOMING}. {@code COMPLETED} and {@code CANCELLED} are driven by
 * firm-side state (filing.filed, court-date.cancelled, etc.) and are set by future wiring code.
 *
 * <p>Court-date and prescription rows go to the matter's <b>owning client only</b> — co-clients
 * linked through {@code customer_projects} see nothing (§14.12 #5, decided 2026-11-04). Their
 * client-facing copy is built in {@link #courtDateSource} / {@link #prescriptionSource} only, whose
 * parameters cannot carry a firm-only field (judge, outcome, description, notes, interruption
 * reason, cause-of-action date — the sanitised-copy rule by construction, B10). {@code
 * PortalDeadlineBackfillService} uses the same builders for pre-existing rows.
 */
@Service
public class DeadlinePortalSyncService {

  private static final Logger log = LoggerFactory.getLogger(DeadlinePortalSyncService.class);

  /** Fallback description label when {@link FieldDateApproachingEvent} details are blank. */
  private static final String CUSTOM_DATE_FALLBACK_LABEL = "CUSTOM_DATE";

  /** Threshold (inclusive) below which a row flips from UPCOMING to DUE_SOON. */
  private static final long DUE_SOON_DAYS = 7;

  static final String COURT_DATE_SOURCE = "COURT_DATE";
  static final String COURT_DATE_TYPE = "COURT_DATE";
  static final String PRESCRIPTION_SOURCE = "PRESCRIPTION_TRACKER";
  static final String PRESCRIPTION_TYPE = "PRESCRIPTION";
  static final String PRESCRIPTION_LABEL = "Prescription deadline";

  /** Portal label column is varchar(160) (global V1); court_name is varchar(200) — D2. */
  private static final int LABEL_MAX = 160;

  /** D4: the client-facing copy for each prescription type (PrescriptionTrackerService types). */
  private static final Map<String, String> PRESCRIPTION_TYPE_LABELS =
      Map.of(
          "GENERAL_3Y", "General 3 years",
          "DEBT_6Y", "Debt 6 years",
          "MORTGAGE_30Y", "Mortgage 30 years",
          "DELICT_3Y", "Delict 3 years",
          "CONTRACT_3Y", "Contract 3 years",
          "CUSTOM", "Custom");

  /**
   * D3: humanised sanitiser fallback for the court-date / prescription rows, so a blank description
   * never renders a raw enum ("COURT_DATE — …") on the portal. Other types keep their literal.
   */
  private static final Map<String, String> DEADLINE_TYPE_FALLBACK_LABELS =
      Map.of(COURT_DATE_TYPE, "Court date", PRESCRIPTION_TYPE, "Prescription");

  private final PortalDeadlineViewRepository deadlineRepo;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final PortalTrustDescriptionSanitiser sanitiser;
  private final TransactionTemplate portalTxTemplate;
  private final Clock clock;

  public DeadlinePortalSyncService(
      PortalDeadlineViewRepository deadlineRepo,
      FieldDefinitionRepository fieldDefinitionRepository,
      CustomerProjectRepository customerProjectRepository,
      PortalTrustDescriptionSanitiser sanitiser,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager,
      Clock clock) {
    this.deadlineRepo = deadlineRepo;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.sanitiser = sanitiser;
    this.portalTxTemplate = new TransactionTemplate(portalTxManager);
    this.clock = clock;
  }

  // ── Event listeners ─────────────────────────────────────────────────────

  /**
   * Handles Phase 48 {@link FieldDateApproachingEvent}. Gated on {@code
   * FieldDefinition.portalVisibleDeadline == true} — when the field is not portal-visible the
   * listener is a no-op (the event still fires for internal notification purposes). Projects the
   * event into {@code portal_deadline_view} as a {@code CUSTOM_DATE} row.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFieldDateApproaching(FieldDateApproachingEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncCustomFieldDate(event);
          } catch (Exception e) {
            log.error(
                "Portal deadline sync failed for event=field_date.approaching tenant={}"
                    + " entityType={} entityId={}",
                event.tenantId(),
                event.entityType(),
                event.entityId(),
                e);
          }
        });
  }

  // TODO (Phase 51): wire onFilingScheduleCreated / onFilingStatusChanged once
  //   FilingScheduleCreatedEvent and FilingStatusChangedEvent exist. Call:
  //   upsertFromDeadline(new DeadlineSource("FILING_SCHEDULE", "FILING", id, customerId,
  //       matterId, label, dueDate, firmStatus, descriptionRaw, sourceRef));

  /**
   * Court date scheduled / rescheduled / heard (Phase 89 633B, ADR-363). Upserts a COURT_DATE row
   * for the matter's OWNING client only — co-clients see nothing (§14.12 #5, decided 2026-11-04).
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCourtDateScheduled(CourtDateScheduledEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            UUID customerId = resolveOwningCustomer(event.customerId(), event.projectId());
            if (customerId == null) {
              log.debug(
                  "No owning client for court date {} — no portal deadline row", event.entityId());
              return;
            }
            upsertFromDeadline(
                courtDateSource(
                    event.entityId(),
                    customerId,
                    event.projectId(),
                    event.dateType(),
                    event.scheduledDate(),
                    event.courtName(),
                    event.courtReference(),
                    event.status()));
          } catch (Exception e) {
            log.error(
                "Portal deadline sync failed for event=court_date.scheduled tenant={}"
                    + " courtDateId={}",
                event.tenantId(),
                event.entityId(),
                e);
          }
        });
  }

  /** Court date cancelled, or the original row of a postpone — the portal row is DELETED (B4). */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCourtDateCancelled(CourtDateCancelledEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            portalTxTemplate.executeWithoutResult(
                status ->
                    deadlineRepo.deleteBySourceEntityAndId(COURT_DATE_SOURCE, event.entityId()));
          } catch (Exception e) {
            log.error(
                "Portal deadline sync failed for event=court_date.cancelled tenant={}"
                    + " courtDateId={}",
                event.tenantId(),
                event.entityId(),
                e);
          }
        });
  }

  /**
   * Prescription tracker created / updated (Phase 89 633B). Upserts a PRESCRIPTION row for the
   * matter's owning client only.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPrescriptionDeadlineSet(PrescriptionDeadlineSetEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            UUID customerId = resolveOwningCustomer(event.customerId(), event.projectId());
            if (customerId == null) {
              log.debug(
                  "No owning client for prescription tracker {} — no portal deadline row",
                  event.entityId());
              return;
            }
            upsertFromDeadline(
                prescriptionSource(
                    event.entityId(),
                    customerId,
                    event.projectId(),
                    event.prescriptionType(),
                    event.prescriptionDate()));
          } catch (Exception e) {
            log.error(
                "Portal deadline sync failed for event=prescription.deadline_set tenant={}"
                    + " trackerId={}",
                event.tenantId(),
                event.entityId(),
                e);
          }
        });
  }

  /** Prescription tracker interrupted — the portal row is DELETED (B4). */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPrescriptionDeadlineCleared(PrescriptionDeadlineClearedEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            portalTxTemplate.executeWithoutResult(
                status ->
                    deadlineRepo.deleteBySourceEntityAndId(PRESCRIPTION_SOURCE, event.entityId()));
          } catch (Exception e) {
            log.error(
                "Portal deadline sync failed for event=prescription.deadline_cleared tenant={}"
                    + " trackerId={}",
                event.tenantId(),
                event.entityId(),
                e);
          }
        });
  }

  // ── Sync helpers (must run inside a bound tenant scope) ─────────────────

  /**
   * Resolves the firm-side custom field-date details, checks the {@code portalVisibleDeadline}
   * opt-in, and upserts a {@code CUSTOM_DATE} row in the portal deadline view. Short-circuits when
   * the field is not opted-in or when essential details are missing.
   */
  private void syncCustomFieldDate(FieldDateApproachingEvent event) {
    String rawEntityType = event.entityType();
    if (rawEntityType == null) {
      log.warn("FieldDateApproachingEvent missing entityType — skipping portal deadline sync");
      return;
    }

    EntityType fieldEntityType;
    try {
      fieldEntityType = EntityType.valueOf(rawEntityType.toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn(
          "Unknown entityType={} on FieldDateApproachingEvent — skipping portal deadline sync",
          rawEntityType);
      return;
    }

    Map<String, Object> details = event.details();
    if (details == null) {
      log.warn("FieldDateApproachingEvent missing details — skipping portal deadline sync");
      return;
    }

    String fieldSlug = stringOrNull(details.get("field_name"));
    if (fieldSlug == null) {
      log.warn("FieldDateApproachingEvent missing field_name detail — skipping");
      return;
    }

    var fieldDefOpt = fieldDefinitionRepository.findByEntityTypeAndSlug(fieldEntityType, fieldSlug);
    if (fieldDefOpt.isEmpty()) {
      log.warn(
          "FieldDefinition not found during portal deadline sync — entityType={} slug={}",
          fieldEntityType,
          fieldSlug);
      return;
    }
    var fieldDef = fieldDefOpt.get();

    // The opt-in gate (ADR-257). Without it, every internal date reminder would leak to the portal.
    if (!fieldDef.isPortalVisibleDeadline()) {
      return;
    }

    LocalDate dueDate = parseIsoDate(details.get("field_value"));
    if (dueDate == null) {
      log.warn(
          "FieldDateApproachingEvent has non-parseable field_value={} — skipping",
          details.get("field_value"));
      return;
    }

    UUID customerId = resolveCustomerId(fieldEntityType, event.entityId(), event.projectId());
    if (customerId == null) {
      // No customer linkage means the deadline has no portal audience — nothing to show.
      return;
    }

    String fieldLabel = stringOrNull(details.get("field_label"));
    String entityName = stringOrNull(details.get("entity_name"));
    String label = buildCustomDateLabel(fieldLabel, fieldSlug, entityName);

    String sanitisedDescription = sanitiser.sanitise(entityName, CUSTOM_DATE_FALLBACK_LABEL, label);

    // Derive a stable per-field-instance id from (entityType, entityId, fieldSlug) so two
    // portal-visible date fields on the same entity don't collide on the (source_entity, id)
    // primary key. Using the event.entityId() alone would let the later sync overwrite the
    // earlier field's row for multi-date entities.
    UUID deadlineId =
        UUID.nameUUIDFromBytes(
            ("CUSTOM_FIELD_DATE:" + fieldEntityType + ":" + event.entityId() + ":" + fieldSlug)
                .getBytes(StandardCharsets.UTF_8));

    PortalDeadlineView view =
        new PortalDeadlineView(
            deadlineId,
            "CUSTOM_FIELD_DATE",
            customerId,
            event.projectId(),
            "CUSTOM_DATE",
            label,
            dueDate,
            deriveStatus(dueDate, LocalDate.now(clock)),
            sanitisedDescription,
            null);

    portalTxTemplate.executeWithoutResult(status -> deadlineRepo.upsert(view));
  }

  /**
   * Shared upsert path the deadline sources converge on: the court-date and prescription listeners
   * and {@code PortalDeadlineBackfillService} call it with a {@link #courtDateSource} / {@link
   * #prescriptionSource} mapping (a future filing listener would too). Kept package-private so
   * integration tests and the backfill can drive it directly; treat it as the canonical write seam.
   */
  void upsertFromDeadline(DeadlineSource src) {
    String sanitisedDescription =
        sanitiser.sanitise(
            src.descriptionRaw(),
            DEADLINE_TYPE_FALLBACK_LABELS.getOrDefault(src.deadlineType(), src.deadlineType()),
            src.sourceReference());
    PortalDeadlineView view =
        new PortalDeadlineView(
            src.id(),
            src.sourceEntity(),
            src.customerId(),
            src.matterId(),
            src.deadlineType(),
            src.label(),
            src.dueDate(),
            src.overrideStatus() != null
                ? src.overrideStatus()
                : deriveStatus(src.dueDate(), LocalDate.now(clock)),
            sanitisedDescription,
            null);
    portalTxTemplate.executeWithoutResult(status -> deadlineRepo.upsert(view));
  }

  /**
   * Value object passed to {@link #upsertFromDeadline(DeadlineSource)} by the court-date /
   * prescription listeners and the deadline backfill (and a future filing listener). Keeps the
   * shared write path free of event-type specifics.
   *
   * @param overrideStatus set to {@code COMPLETED} or {@code CANCELLED} when firm-side state forces
   *     a terminal status; otherwise {@code null} to let {@link #deriveStatus} compute from the due
   *     date.
   */
  public record DeadlineSource(
      String sourceEntity,
      String deadlineType,
      UUID id,
      UUID customerId,
      UUID matterId,
      String label,
      LocalDate dueDate,
      String overrideStatus,
      String descriptionRaw,
      String sourceReference) {}

  // ── Court-date / prescription copy rule (Phase 89 633B, ADR-363, §14.3.14) ──

  /**
   * The sanitised-copy rule for a court date: label = humanised type + " — " + court (type only
   * when the court is blank), truncated to the 160-char column (D2); description = court reference
   * only. Judge / magistrate, outcome and free-text description are not parameters (B10).
   */
  static DeadlineSource courtDateSource(
      UUID courtDateId,
      UUID customerId,
      UUID matterId,
      String dateType,
      LocalDate scheduledDate,
      String courtName,
      String courtReference,
      String status) {
    String type = humanise(dateType);
    String label =
        truncate(
            courtName == null || courtName.isBlank() ? type : type + " — " + courtName.strip(),
            LABEL_MAX);
    return new DeadlineSource(
        COURT_DATE_SOURCE,
        COURT_DATE_TYPE,
        courtDateId,
        customerId,
        matterId,
        label,
        scheduledDate,
        "HEARD".equals(status) ? "COMPLETED" : null,
        courtReference,
        label);
  }

  /**
   * Prescription copy: fixed label, humanised type (D4). No cause of action, notes or interruption
   * data are parameters (B10).
   */
  static DeadlineSource prescriptionSource(
      UUID trackerId,
      UUID customerId,
      UUID matterId,
      String prescriptionType,
      LocalDate prescriptionDate) {
    return new DeadlineSource(
        PRESCRIPTION_SOURCE,
        PRESCRIPTION_TYPE,
        trackerId,
        customerId,
        matterId,
        PRESCRIPTION_LABEL,
        prescriptionDate,
        null,
        humanisePrescriptionType(prescriptionType),
        PRESCRIPTION_LABEL);
  }

  /**
   * The matter's owning client: the entity's denormalised {@code customer_id} (NOT NULL, R7.7); the
   * {@code customer_projects} lookup is a defensive fallback only. Needs a bound tenant scope.
   */
  UUID resolveOwningCustomer(UUID customerId, UUID projectId) {
    if (customerId != null) {
      return customerId;
    }
    if (projectId == null) {
      return null;
    }
    return customerProjectRepository.findFirstCustomerByProjectId(projectId).orElse(null);
  }

  /** {@code PRE_TRIAL} → "Pre trial": lower-case, '_' → ' ', first letter upper. */
  static String humanise(String literal) {
    if (literal == null || literal.isBlank()) {
      return "";
    }
    String spaced = literal.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  static String humanisePrescriptionType(String prescriptionType) {
    return prescriptionType == null
        ? ""
        : PRESCRIPTION_TYPE_LABELS.getOrDefault(prescriptionType, humanise(prescriptionType));
  }

  // ── Pure helpers ────────────────────────────────────────────────────────

  /**
   * Derives the deadline status from the due date. Per ADR-253 this is computed at sync time, not
   * at read time, so a read hitting the portal during the nightly scanner's gap is never stale.
   */
  static String deriveStatus(LocalDate dueDate, LocalDate today) {
    if (dueDate.isBefore(today)) {
      return "OVERDUE";
    }
    long daysUntil = ChronoUnit.DAYS.between(today, dueDate);
    if (daysUntil <= DUE_SOON_DAYS) {
      return "DUE_SOON";
    }
    return "UPCOMING";
  }

  private UUID resolveCustomerId(EntityType entityType, UUID entityId, UUID projectId) {
    return switch (entityType) {
      case CUSTOMER -> entityId;
      case PROJECT -> {
        if (entityId == null) {
          yield null;
        }
        yield customerProjectRepository.findFirstCustomerByProjectId(entityId).orElse(null);
      }
      case TASK, INVOICE -> {
        // Phase 48 scanner only scans CUSTOMER/PROJECT, so these branches are not reachable from
        // the current event path. Log at debug so drift is visible if the scanner scope widens
        // later without updating this resolver.
        log.debug(
            "Custom-field deadline resolution skipped for unsupported entityType={}", entityType);
        yield null;
      }
    };
  }

  private static String buildCustomDateLabel(
      String fieldLabel, String fieldSlug, String entityName) {
    String leading = fieldLabel != null && !fieldLabel.isBlank() ? fieldLabel : fieldSlug;
    if (entityName != null && !entityName.isBlank()) {
      return truncate(leading + " — " + entityName, 160);
    }
    return truncate(leading, 160);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    if (value.length() <= max) {
      return value;
    }
    return value.substring(0, max - 1) + "\u2026";
  }

  private static String stringOrNull(Object o) {
    if (o == null) {
      return null;
    }
    String s = o.toString();
    return s.isBlank() ? null : s;
  }

  private static LocalDate parseIsoDate(Object o) {
    if (o == null) {
      return null;
    }
    try {
      return LocalDate.parse(o.toString());
    } catch (DateTimeParseException ex) {
      return null;
    }
  }
}
