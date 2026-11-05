package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalBackfillMarkerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Backfills {@code portal.portal_deadline_view} once per tenant schema (Phase 89 633B, ADR-363,
 * §14.3.14, §14.7.3, §5b row 19): for every {@code org_schema_mapping} row without a {@code
 * portal_deadline_backfill} marker in {@code portal.portal_backfill_markers}, project the tenant's
 * future SCHEDULED court dates and live future prescription trackers (R6.4 — never POSTPONED, never
 * past rows) through the same {@code DeadlineSource} builders the live listeners use, then record
 * the marker with the row count. {@code @Order(80)}: after the task backfill ({@code 70}), before
 * {@code PackReconciliationRunner} ({@code 100}), inside one ShedLock lease so two instances cannot
 * race. Never fails application startup: each tenant is isolated, the whole run is guarded, and
 * unmarked tenants are retried at the next start.
 */
@Component
@Order(80)
public class PortalDeadlineBackfillRunner implements ApplicationRunner {

  static final String NAME = "portal_deadline_backfill";

  private static final Logger log = LoggerFactory.getLogger(PortalDeadlineBackfillRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final PortalBackfillMarkerRepository markerRepository;
  private final PortalDeadlineBackfillService backfillService;

  public PortalDeadlineBackfillRunner(
      OrgSchemaMappingRepository mappingRepository,
      PortalBackfillMarkerRepository markerRepository,
      PortalDeadlineBackfillService backfillService) {
    this.mappingRepository = mappingRepository;
    this.markerRepository = markerRepository;
    this.backfillService = backfillService;
  }

  @Override
  @SchedulerLock(name = NAME, lockAtMostFor = "30m")
  public void run(ApplicationArguments args) {
    try {
      backfillAll();
    } catch (Exception e) {
      // Never block startup — unmarked tenants are retried at the next start.
      log.error("Portal deadline backfill aborted", e);
    }
  }

  private void backfillAll() {
    List<OrgSchemaMapping> mappings = mappingRepository.findAll();
    int scanned = 0;
    int skipped = 0;
    int failed = 0;
    int rowsUpserted = 0;
    for (var mapping : mappings) {
      try {
        int rows = backfillTenant(mapping);
        if (rows < 0) {
          skipped++;
        } else {
          scanned++;
          rowsUpserted += rows;
        }
      } catch (Exception e) {
        failed++;
        log.error(
            "Portal deadline backfill failed for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getExternalOrgId(),
            e);
      }
    }
    log.info(
        "Portal deadline backfill: {} tenants scanned, {} skipped (already marked), {} failed, {}"
            + " rows upserted",
        scanned,
        skipped,
        failed,
        rowsUpserted);
  }

  /**
   * Backfills one tenant. Returns the number of portal deadline rows upserted, or {@code -1} when
   * the tenant's marker already existed (skipped). Package-private for targeted tests.
   */
  int backfillTenant(OrgSchemaMapping mapping) {
    if (markerRepository.hasMarker(NAME, mapping.getSchemaName())) {
      return -1;
    }
    int rows =
        backfillService.backfillTenant(
            mapping.getExternalOrgId(), mapping.getSchemaName(), mapping.getShardId());
    markerRepository.insertMarker(NAME, mapping.getSchemaName(), rows);
    return rows;
  }
}
