package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
 * never roll back the originating firm transaction (mirrors {@code RetainerPortalSyncService}, see
 * ADR-253).
 *
 * <p>Per ADR-256, the portal surfaces four deadline sources behind a single polymorphic row, keyed
 * on {@code (source_entity, id)}:
 *
 * <ol>
 *   <li><b>FILING_SCHEDULE</b> — Phase 51 filing schedule lifecycle. <i>Dormant</i>: {@code
 *       FilingScheduleCreatedEvent} / {@code FilingStatusChangedEvent} do not yet exist in the
 *       codebase. When Phase 51 wires these events, add an {@code @TransactionalEventListener}
 *       method and call {@link #upsertFromDeadline(DeadlineSource)} with the filing-row fields.
 *   <li><b>COURT_DATE</b> — Phase 55 court-calendar events. <i>Dormant</i>: {@code
 *       CourtDateScheduledEvent} / {@code CourtDateCancelledEvent} do not yet exist. Same wiring
 *       pattern as above; the {@code CANCELLED} case can either upsert with {@code
 *       status=CANCELLED} or call {@link PortalDeadlineViewRepository#deleteBySourceEntityAndId}.
 *   <li><b>PRESCRIPTION_TRACKER</b> — Phase 55 prescription events. <i>Dormant</i>: {@code
 *       PrescriptionDeadlineSetEvent} does not yet exist.
 *   <li><b>CUSTOM_FIELD_DATE</b> — Phase 48 {@link FieldDateApproachingEvent}. <b>Wired.</b> Gated
 *       on {@code FieldDefinition.portalVisibleDeadline == true} (ADR-257) — custom-field dates
 *       only leak to the portal when an admin has explicitly opted-in per field.
 * </ol>
 *
 * <p>Status derivation happens at sync time (ADR-253), not at read time. See {@link
 * #deriveStatus(LocalDate, LocalDate)}: past due_date → {@code OVERDUE}, within 7 days → {@code
 * DUE_SOON}, otherwise {@code UPCOMING}. {@code COMPLETED} and {@code CANCELLED} are driven by
 * firm-side state (filing.filed, court-date.cancelled, etc.) and are set by future wiring code.
 */
@Service
public class DeadlinePortalSyncService {

  private static final Logger log = LoggerFactory.getLogger(DeadlinePortalSyncService.class);

  /** Fallback description label when {@link FieldDateApproachingEvent} details are blank. */
  private static final String CUSTOM_DATE_FALLBACK_LABEL = "CUSTOM_DATE";

  /** Threshold (inclusive) below which a row flips from UPCOMING to DUE_SOON. */
  private static final long DUE_SOON_DAYS = 7;

  private final PortalDeadlineViewRepository deadlineRepo;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final PortalTrustDescriptionSanitiser sanitiser;
  private final TransactionTemplate portalTxTemplate;

  public DeadlinePortalSyncService(
      PortalDeadlineViewRepository deadlineRepo,
      FieldDefinitionRepository fieldDefinitionRepository,
      CustomerProjectRepository customerProjectRepository,
      PortalTrustDescriptionSanitiser sanitiser,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.deadlineRepo = deadlineRepo;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.sanitiser = sanitiser;
    this.portalTxTemplate = new TransactionTemplate(portalTxManager);
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
    handleInTenantScope(
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
  //
  // TODO (Phase 55): wire onCourtDateScheduled / onCourtDateCancelled and onPrescriptionDeadlineSet
  //   once their respective domain events are added to the sealed DomainEvent interface.

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
            deriveStatus(dueDate, LocalDate.now()),
            sanitisedDescription,
            null);

    portalTxTemplate.executeWithoutResult(status -> deadlineRepo.upsert(view));
  }

  /**
   * Shared upsert path that all four deadline sources converge on. Future listeners for filing,
   * court-date, and prescription events call this with their source-specific mapping already
   * resolved. Kept package-private-visible via the sync-service class so integration tests can
   * drive it directly; treat it as the canonical write seam.
   */
  void upsertFromDeadline(DeadlineSource src) {
    String sanitisedDescription =
        sanitiser.sanitise(src.descriptionRaw(), src.deadlineType(), src.sourceReference());
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
                : deriveStatus(src.dueDate(), LocalDate.now()),
            sanitisedDescription,
            null);
    portalTxTemplate.executeWithoutResult(status -> deadlineRepo.upsert(view));
  }

  /**
   * Value object used by future filing / court-date / prescription listeners when calling {@link
   * #upsertFromDeadline(DeadlineSource)}. Keeps the shared write path free of event-type specifics.
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

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      log.warn("Deadline portal sync event received without tenantId — skipping");
      return;
    }
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }
}
