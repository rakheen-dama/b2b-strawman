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
 * Backfills {@code portal.portal_tasks} once per tenant schema (Phase 89 633A, §14.3.13, §14.8.4,
 * G88-F19): for every {@code org_schema_mapping} row without a {@code portal_task_backfill} marker
 * in {@code portal.portal_backfill_markers}, project the tasks of every client-linked matter that
 * are missing from the portal (matters instantiated from a template before the live path published
 * task events), then record the marker with the number of rows inserted. Runs after tenant
 * migrations ({@code TenantMigrationRunner} {@code @Order(50)}) and the directory backfill
 * ({@code @Order(60)}), before {@code PackReconciliationRunner} ({@code @Order(100)}), inside one
 * ShedLock lease so two instances cannot race. Never fails application startup: each tenant is
 * isolated, the whole run is guarded, and unmarked tenants are retried at the next start.
 */
@Component
@Order(70)
public class PortalTaskBackfillRunner implements ApplicationRunner {

  static final String NAME = "portal_task_backfill";

  private static final Logger log = LoggerFactory.getLogger(PortalTaskBackfillRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final PortalBackfillMarkerRepository markerRepository;
  private final PortalTaskBackfillService backfillService;

  public PortalTaskBackfillRunner(
      OrgSchemaMappingRepository mappingRepository,
      PortalBackfillMarkerRepository markerRepository,
      PortalTaskBackfillService backfillService) {
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
      log.error("Portal task backfill aborted", e);
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
            "Portal task backfill failed for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getExternalOrgId(),
            e);
      }
    }
    log.info(
        "Portal task backfill: {} tenants scanned, {} skipped (already marked), {} failed, {} rows"
            + " upserted",
        scanned,
        skipped,
        failed,
        rowsUpserted);
  }

  /**
   * Backfills one tenant. Returns the number of portal task rows inserted, or {@code -1} when the
   * tenant's marker already existed (skipped). Package-private for targeted tests.
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
