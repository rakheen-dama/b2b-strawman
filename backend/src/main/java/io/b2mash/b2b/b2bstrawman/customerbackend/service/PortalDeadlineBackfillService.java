package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.DeadlinePortalSyncService.DeadlineSource;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-tenant portal deadline backfill (Phase 89 633B, ADR-363, R6.4, ADR-358): projects the
 * tenant's SCHEDULED court dates and live (not INTERRUPTED / EXPIRED) prescription trackers dated
 * today or later into {@code portal.portal_deadline_view}. Tenant reads run in the tenant's scope
 * on its shard; rows go through {@link DeadlinePortalSyncService#upsertFromDeadline} (package-
 * private — same package) so the sanitiser, the copy rule, the owning-client rule and the status
 * derivation are the live path's. "Today" is South African business time from the injected {@link
 * Clock}. {@code FILING_SCHEDULE} stays dormant (ADR-330).
 */
@Service
public class PortalDeadlineBackfillService {

  private static final String SCHEDULED = "SCHEDULED";
  private static final List<String> DEAD_TRACKER_STATUSES = List.of("INTERRUPTED", "EXPIRED");

  private final CourtDateRepository courtDateRepository;
  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final DeadlinePortalSyncService syncService;
  private final PortalDeadlineViewRepository deadlineRepo;
  private final TransactionTemplate portalTx;
  private final Clock clock;

  public PortalDeadlineBackfillService(
      CourtDateRepository courtDateRepository,
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      DeadlinePortalSyncService syncService,
      PortalDeadlineViewRepository deadlineRepo,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager,
      Clock clock) {
    this.courtDateRepository = courtDateRepository;
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.syncService = syncService;
    this.deadlineRepo = deadlineRepo;
    this.portalTx = new TransactionTemplate(portalTxManager);
    this.clock = clock;
  }

  /**
   * Upserts the portal rows for one tenant and returns how many are present afterwards (rows
   * removed by {@link #reconcile} are not counted).
   */
  public int backfillTenant(String orgId, String tenantSchema, @Nullable String shardId) {
    LocalDate today = LocalDate.now(clock);
    List<DeadlineSource> sources =
        RequestScopes.callForTenantOnShard(tenantSchema, orgId, shardId, () -> collect(today));
    if (sources.isEmpty()) {
      return 0;
    }
    // One portal transaction per row (the existing write seam).
    sources.forEach(syncService::upsertFromDeadline);
    return sources.size() - reconcile(orgId, tenantSchema, shardId, sources);
  }

  /** Runs inside the tenant scope. */
  private List<DeadlineSource> collect(LocalDate today) {
    List<DeadlineSource> out = new ArrayList<>();
    for (var cd :
        courtDateRepository.findByStatusAndScheduledDateGreaterThanEqual(SCHEDULED, today)) {
      DeadlineSource src = courtDateSource(cd);
      if (src != null) {
        out.add(src);
      }
    }
    for (var t :
        prescriptionTrackerRepository.findByStatusNotInAndPrescriptionDateGreaterThanEqual(
            DEAD_TRACKER_STATUSES, today)) {
      DeadlineSource src = prescriptionSource(t);
      if (src != null) {
        out.add(src);
      }
    }
    return out;
  }

  /**
   * D7 (633A precedent, PR #1730): a cancel / postpone / outcome / interrupt that committed between
   * our tenant read and our upsert had its live listener run first, so our upsert may have
   * re-created or overwritten its row. Re-reads the written ids in the tenant and re-syncs them
   * from current state: a court date that is gone, CANCELLED or POSTPONED and a tracker that is
   * gone or INTERRUPTED are deleted; the rest are re-upserted (HEARD → COMPLETED). Never touches
   * ids this run did not write. Returns how many rows were removed. Package-private for tests.
   */
  int reconcile(
      String orgId, String tenantSchema, @Nullable String shardId, List<DeadlineSource> written) {
    List<UUID> courtIds = idsOf(written, DeadlinePortalSyncService.COURT_DATE_SOURCE);
    List<UUID> trackerIds = idsOf(written, DeadlinePortalSyncService.PRESCRIPTION_SOURCE);
    record Resync(List<DeadlineSource> upserts, List<DeadlineSource> deletes) {}
    Resync resync =
        RequestScopes.callForTenantOnShard(
            tenantSchema,
            orgId,
            shardId,
            () -> {
              Map<UUID, CourtDate> courts =
                  courtDateRepository.findAllById(courtIds).stream()
                      .collect(Collectors.toMap(CourtDate::getId, Function.identity()));
              Map<UUID, PrescriptionTracker> trackers =
                  prescriptionTrackerRepository.findAllById(trackerIds).stream()
                      .collect(Collectors.toMap(PrescriptionTracker::getId, Function.identity()));
              List<DeadlineSource> upserts = new ArrayList<>();
              List<DeadlineSource> deletes = new ArrayList<>();
              for (var src : written) {
                DeadlineSource current =
                    DeadlinePortalSyncService.COURT_DATE_SOURCE.equals(src.sourceEntity())
                        ? currentCourtDate(courts.get(src.id()))
                        : currentTracker(trackers.get(src.id()));
                if (current == null) {
                  deletes.add(src);
                } else {
                  upserts.add(current);
                }
              }
              return new Resync(upserts, deletes);
            });
    resync.upserts().forEach(syncService::upsertFromDeadline);
    for (var src : resync.deletes()) {
      portalTx.executeWithoutResult(
          s -> deadlineRepo.deleteBySourceEntityAndId(src.sourceEntity(), src.id()));
    }
    return resync.deletes().size();
  }

  /** Current mapping of a court date, or null when it is no longer a portal deadline. */
  private DeadlineSource currentCourtDate(@Nullable CourtDate cd) {
    if (cd == null || !(SCHEDULED.equals(cd.getStatus()) || "HEARD".equals(cd.getStatus()))) {
      return null;
    }
    return courtDateSource(cd);
  }

  /** Current mapping of a tracker, or null when it is no longer a portal deadline. */
  private DeadlineSource currentTracker(@Nullable PrescriptionTracker t) {
    if (t == null || "INTERRUPTED".equals(t.getStatus())) {
      return null;
    }
    return prescriptionSource(t);
  }

  /** Maps through the live builder; null when the matter has no owning client (tenant scope). */
  private DeadlineSource courtDateSource(CourtDate cd) {
    UUID owner = syncService.resolveOwningCustomer(cd.getCustomerId(), cd.getProjectId());
    if (owner == null) {
      return null;
    }
    return DeadlinePortalSyncService.courtDateSource(
        cd.getId(),
        owner,
        cd.getProjectId(),
        cd.getDateType(),
        cd.getScheduledDate(),
        cd.getCourtName(),
        cd.getCourtReference(),
        cd.getStatus());
  }

  /** Maps through the live builder; null when the matter has no owning client (tenant scope). */
  private DeadlineSource prescriptionSource(PrescriptionTracker t) {
    UUID owner = syncService.resolveOwningCustomer(t.getCustomerId(), t.getProjectId());
    if (owner == null) {
      return null;
    }
    return DeadlinePortalSyncService.prescriptionSource(
        t.getId(), owner, t.getProjectId(), t.getPrescriptionType(), t.getPrescriptionDate());
  }

  private static List<UUID> idsOf(List<DeadlineSource> sources, String sourceEntity) {
    return sources.stream()
        .filter(s -> sourceEntity.equals(s.sourceEntity()))
        .map(DeadlineSource::id)
        .toList();
  }
}
