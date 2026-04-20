package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerConsumptionEntryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerConsumptionEntryRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerSummaryRepository;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retainer.PeriodStatus;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriod;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerStatus;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerAgreementCreatedEvent;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerAgreementUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerPeriodRolloverEvent;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.settings.PortalRetainerMemberDisplay;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
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
 * Syncs firm-side retainer activity into the portal read-model ({@code
 * portal.portal_retainer_summary}, {@code portal.portal_retainer_consumption_entry}). Listens in
 * {@link TransactionPhase#AFTER_COMMIT} so only committed firm-side changes ever reach portal
 * contacts; failures are swallowed and logged — a sync failure must never roll back the originating
 * firm transaction (mirrors {@code TrustLedgerPortalSyncService}; see ADR-253).
 *
 * <p>Four firm-side events drive the sync (Epic 496A):
 *
 * <ul>
 *   <li>{@link RetainerAgreementCreatedEvent} — seeds a {@code portal_retainer_summary} row.
 *   <li>{@link RetainerAgreementUpdatedEvent} — re-projects summary fields (name, allotment,
 *       status).
 *   <li>{@link TimeEntryChangedEvent} — when the time entry belongs to a retainer-backed customer,
 *       upserts (or deletes) a consumption-entry row and re-snapshots the summary counters from the
 *       currently-open period.
 *   <li>{@link RetainerPeriodRolloverEvent} — rolls the summary forward to the new period bounds.
 * </ul>
 *
 * <p>The firm-side {@code RetainerConsumptionListener} keeps tenant-side bookkeeping (period's
 * consumed / remaining hours) correct synchronously with the time-entry transaction. This service
 * reads that updated state from the portal sync's {@code AFTER_COMMIT} listener — it never
 * duplicates the firm-side calculation.
 */
@Service
public class RetainerPortalSyncService {

  private static final Logger log = LoggerFactory.getLogger(RetainerPortalSyncService.class);

  /**
   * Sentinel actor id used when a system operation (the tenant-wide backfill) rebinds {@code
   * MEMBER_ID} — downstream listeners that read the scoped member still see a non-null UUID, but it
   * never matches a real member row.
   */
  private static final UUID SYSTEM_ACTOR_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  /** Fallback for the portal description when the firm-side text is blank or [internal]-tagged. */
  private static final String DESCRIPTION_FALLBACK_LABEL = "TIME";

  private final PortalRetainerSummaryRepository summaryRepo;
  private final PortalRetainerConsumptionEntryRepository entryRepo;
  private final RetainerAgreementRepository agreementRepository;
  private final RetainerPeriodRepository periodRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final MemberRepository memberRepository;
  private final OrgSettingsService orgSettingsService;
  private final PortalRetainerMemberDisplayResolver memberDisplayResolver;
  private final PortalTrustDescriptionSanitiser sanitiser;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate portalTxTemplate;

  public RetainerPortalSyncService(
      PortalRetainerSummaryRepository summaryRepo,
      PortalRetainerConsumptionEntryRepository entryRepo,
      RetainerAgreementRepository agreementRepository,
      RetainerPeriodRepository periodRepository,
      CustomerProjectRepository customerProjectRepository,
      TimeEntryRepository timeEntryRepository,
      MemberRepository memberRepository,
      OrgSettingsService orgSettingsService,
      PortalRetainerMemberDisplayResolver memberDisplayResolver,
      PortalTrustDescriptionSanitiser sanitiser,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.summaryRepo = summaryRepo;
    this.entryRepo = entryRepo;
    this.agreementRepository = agreementRepository;
    this.periodRepository = periodRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.memberRepository = memberRepository;
    this.orgSettingsService = orgSettingsService;
    this.memberDisplayResolver = memberDisplayResolver;
    this.sanitiser = sanitiser;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.portalTxTemplate = new TransactionTemplate(portalTxManager);
  }

  // ── Event listeners ─────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRetainerAgreementCreated(RetainerAgreementCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncSummary(event.agreementId());
          } catch (Exception e) {
            log.error(
                "Portal retainer sync failed for event=retainer_agreement.created tenant={}"
                    + " agreement={}",
                event.tenantId(),
                event.agreementId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRetainerAgreementUpdated(RetainerAgreementUpdatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncSummary(event.agreementId());
          } catch (Exception e) {
            log.error(
                "Portal retainer sync failed for event=retainer_agreement.updated tenant={}"
                    + " agreement={}",
                event.tenantId(),
                event.agreementId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTimeEntryChanged(TimeEntryChangedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncTimeEntry(event);
          } catch (Exception e) {
            log.error(
                "Portal retainer sync failed for event=time_entry.changed tenant={} timeEntry={}"
                    + " action={}",
                event.tenantId(),
                event.entityId(),
                event.action(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRetainerPeriodRollover(RetainerPeriodRolloverEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            // Use the agreement id as the portal summary id (1:1 with firm-side).
            summaryRepo.updatePeriodRollover(
                event.customerId(),
                event.agreementId(),
                event.newPeriodStart(),
                event.newPeriodEnd(),
                event.nextRenewalDate(),
                event.rolloverHoursOut(),
                event.allocatedHours());
          } catch (Exception e) {
            log.error(
                "Portal retainer sync failed for event=retainer_period.rollover tenant={}"
                    + " agreement={} newPeriod={}",
                event.tenantId(),
                event.agreementId(),
                event.newPeriodId(),
                e);
          }
        });
  }

  // ── Sync helpers (must run inside a bound tenant scope) ─────────────────

  /**
   * Upserts the portal summary row for the given agreement. Reads fresh firm-side state for the
   * agreement + its open period so repeated calls always converge. No-op (and log) when the
   * agreement or its open period can't be found — covers a transient race between the event and the
   * read.
   */
  private void syncSummary(UUID agreementId) {
    var agreementOpt = agreementRepository.findById(agreementId);
    if (agreementOpt.isEmpty()) {
      log.warn("Retainer agreement not found during portal sync — agreement={}", agreementId);
      return;
    }
    RetainerAgreement agreement = agreementOpt.get();

    Optional<RetainerPeriod> periodOpt =
        periodRepository.findByAgreementIdAndStatus(agreementId, PeriodStatus.OPEN);
    if (periodOpt.isEmpty()) {
      // No OPEN period — typically the agreement has been terminated. Project a terminal snapshot
      // so the portal row reflects the new status (otherwise the previous ACTIVE/PAUSED snapshot
      // sticks). We zero out remaining hours and stamp a fresh last_synced_at via the existing
      // upsert path.
      PortalRetainerSummaryView terminalView = toTerminalSummaryView(agreement);
      portalTxTemplate.executeWithoutResult(status -> summaryRepo.upsert(terminalView));
      log.debug(
          "No OPEN retainer period found for agreement={} — wrote terminal portal snapshot"
              + " (status={})",
          agreementId,
          terminalView.status());
      return;
    }
    PortalRetainerSummaryView view = toSummaryView(agreement, periodOpt.get());
    portalTxTemplate.executeWithoutResult(status -> summaryRepo.upsert(view));
  }

  /**
   * Projects a firm-side time-entry change into the portal read-model. Detects whether the
   * time-entry's project is backed by a retainer; if so, upserts (or deletes, for DELETED) the
   * consumption entry and re-snapshots the summary counters from the current open period.
   */
  private void syncTimeEntry(TimeEntryChangedEvent event) {
    if (event.projectId() == null || event.entityId() == null) {
      return;
    }

    // Is the project retainer-backed?
    var customerIdOpt = customerProjectRepository.findFirstCustomerByProjectId(event.projectId());
    if (customerIdOpt.isEmpty()) {
      return;
    }
    UUID customerId = customerIdOpt.get();

    String action = event.action();
    if ("DELETED".equals(action)) {
      // Run the deletion BEFORE the active-or-paused agreement lookup — a historical time entry
      // tied to an already-terminated retainer must still cause the portal consumption row to be
      // removed. Re-snapshot the summary only when an active/paused agreement still exists; if the
      // retainer has been terminated, the terminal snapshot is already up-to-date.
      portalTxTemplate.executeWithoutResult(
          status -> entryRepo.deleteByTimeEntryId(customerId, event.entityId()));
      var agreementForResnapshot = agreementRepository.findActiveOrPausedByCustomerId(customerId);
      agreementForResnapshot.ifPresent(a -> syncSummary(a.getId()));
      return;
    }

    var agreementOpt = agreementRepository.findActiveOrPausedByCustomerId(customerId);
    if (agreementOpt.isEmpty()) {
      return;
    }
    RetainerAgreement agreement = agreementOpt.get();

    // CREATED / UPDATED — fetch the time entry, project + sanitise, upsert.
    var timeEntryOpt = timeEntryRepository.findById(event.entityId());
    if (timeEntryOpt.isEmpty()) {
      log.warn("Time entry not found during portal retainer sync — timeEntry={}", event.entityId());
      return;
    }
    TimeEntry entry = timeEntryOpt.get();

    // Resolve member display string via the firm-wide privacy toggle. Use the time entry's owner
    // (who actually logged the time), NOT the event's actor (who may be an admin editing someone
    // else's entry) — otherwise an edit silently rewrites attribution to the editor.
    UUID timeEntryOwnerId = entry.getMemberId();
    var member =
        timeEntryOwnerId != null ? memberRepository.findById(timeEntryOwnerId).orElse(null) : null;
    String roleName =
        (member != null && member.getOrgRoleEntity() != null)
            ? member.getOrgRoleEntity().getName()
            : null;
    PortalRetainerMemberDisplay mode = orgSettingsService.getPortalRetainerMemberDisplay();
    String memberDisplay = memberDisplayResolver.resolve(member, roleName, mode);

    String sanitisedDescription =
        sanitiser.sanitise(entry.getDescription(), DESCRIPTION_FALLBACK_LABEL, agreement.getName());
    BigDecimal hours = minutesToHours(entry.getDurationMinutes());

    PortalRetainerConsumptionEntryView entryView =
        new PortalRetainerConsumptionEntryView(
            entry.getId(),
            agreement.getId(),
            customerId,
            entry.getDate(),
            hours,
            sanitisedDescription,
            memberDisplay,
            null);

    portalTxTemplate.executeWithoutResult(status -> entryRepo.upsert(entryView));
    // Re-snapshot the summary counters from the current open period — the firm-side listener has
    // already updated `consumedHours` / `remainingHours` on that period within the just-committed
    // transaction, so reading the period now gives us the fresh absolute values.
    syncSummary(agreement.getId());
  }

  // ── Backfill ───────────────────────────────────────────────────────────

  /**
   * Rebuilds the portal retainer summary read-model for every retainer in the given tenant.
   * Intended for module activation or drift repair. Does not rebuild the per-entry consumption
   * history — consumption entries are event-driven (any repair happens naturally on the next
   * time-entry change).
   *
   * <p><strong>Reserved for admin-authenticated internal callers only.</strong> The method binds
   * the {@link #SYSTEM_ACTOR_ID} sentinel as {@code MEMBER_ID} and writes across the supplied
   * {@code orgId}'s tenant — exposing it on a public endpoint without a platform-admin guard would
   * be a privilege-escalation vector. Callers MUST already be inside an authenticated request scope
   * whose scoped {@code ORG_ID} matches the supplied {@code orgId} (verified by the preconditions
   * below); this prevents an authenticated caller for org A from triggering a backfill of org B.
   */
  public BackfillResult backfillForTenant(String orgId) {
    if (!RequestScopes.ORG_ID.isBound()) {
      throw new IllegalStateException(
          "backfillForTenant must run inside an authenticated request scope");
    }
    // Tenant-isolation guard: the authenticated caller's scoped orgId MUST match the argument.
    // Otherwise an authenticated request for org A could call backfillForTenant("orgB") and the
    // ScopedValue.where(...) below would silently rebind to org B — a cross-tenant escape.
    String scopedOrgId = RequestScopes.ORG_ID.get();
    if (!orgId.equals(scopedOrgId)) {
      throw new ForbiddenException(
          "Cross-tenant backfill denied",
          "Authenticated orgId does not match backfill target orgId");
    }
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Organization not found", "No organization found with orgId " + orgId));
    String schema = mapping.getSchemaName();
    log.info("Starting portal retainer backfill for org={} schema={}", orgId, schema);

    int[] counts = new int[] {0}; // agreements projected

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, SYSTEM_ACTOR_ID)
        .run(
            () -> {
              // Delegate to syncSummary so backfill behaves identically to the event-driven path:
              // agreements without an OPEN period get a terminal snapshot (via
              // toTerminalSummaryView)
              // rather than being silently skipped. syncSummary manages its own portal transaction
              // per row via portalTxTemplate, so no outer tx wrapper is needed here.
              for (var agreement : agreementRepository.findAll()) {
                syncSummary(agreement.getId());
                counts[0]++;
              }
            });

    log.info(
        "Portal retainer backfill complete for org={} agreementsProjected={}", orgId, counts[0]);
    return new BackfillResult(counts[0]);
  }

  public record BackfillResult(int agreementsProjected) {}

  // ── Pure helpers ────────────────────────────────────────────────────────

  /**
   * Builds a terminal portal snapshot for an agreement that no longer has an OPEN period (typically
   * TERMINATED). Status is mapped from the agreement, hours_remaining is forced to zero, and
   * period/renewal dates are blanked so the portal correctly conveys "no active period". The {@code
   * upsert} stamps a fresh {@code last_synced_at}.
   */
  private PortalRetainerSummaryView toTerminalSummaryView(RetainerAgreement agreement) {
    BigDecimal allotted =
        agreement.getAllocatedHours() != null ? agreement.getAllocatedHours() : BigDecimal.ZERO;
    return new PortalRetainerSummaryView(
        agreement.getId(),
        agreement.getCustomerId(),
        agreement.getName(),
        agreement.getFrequency().name(),
        allotted,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null,
        BigDecimal.ZERO,
        null,
        mapStatus(agreement.getStatus()),
        null);
  }

  private PortalRetainerSummaryView toSummaryView(
      RetainerAgreement agreement, RetainerPeriod period) {
    BigDecimal consumed =
        period.getConsumedHours() != null ? period.getConsumedHours() : BigDecimal.ZERO;
    BigDecimal remaining =
        period.getRemainingHours() != null ? period.getRemainingHours() : BigDecimal.ZERO;
    BigDecimal rollover =
        period.getRolloverHoursIn() != null ? period.getRolloverHoursIn() : BigDecimal.ZERO;
    return new PortalRetainerSummaryView(
        agreement.getId(),
        agreement.getCustomerId(),
        agreement.getName(),
        agreement.getFrequency().name(),
        period.getAllocatedHours(),
        consumed,
        remaining,
        period.getPeriodStart(),
        period.getPeriodEnd(),
        rollover,
        period.getPeriodEnd(),
        mapStatus(agreement.getStatus()),
        null);
  }

  /**
   * Maps firm-side {@link RetainerStatus} to the portal {@code status} column's constrained value
   * set ({@code ACTIVE | EXPIRED | PAUSED}) per the V20 migration check constraint.
   */
  private static String mapStatus(RetainerStatus status) {
    if (status == null) {
      return "EXPIRED";
    }
    return switch (status) {
      case ACTIVE -> "ACTIVE";
      case PAUSED -> "PAUSED";
      case TERMINATED -> "EXPIRED";
    };
  }

  private static BigDecimal minutesToHours(int durationMinutes) {
    return BigDecimal.valueOf(durationMinutes)
        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
  }

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      log.warn("Retainer portal sync event received without tenantId — skipping");
      return;
    }
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }
}
