package io.b2mash.b2b.b2bstrawman.portal.directory;

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
 * Backfills {@code portal.portal_contact_directory} once per tenant schema (A8, §13.7.2): for every
 * {@code org_schema_mapping} row without a {@code portal_contact_directory_backfill} marker,
 * rebuild the org's rows from its ACTIVE portal contacts and record the marker with the row count.
 * Runs after tenant migrations ({@code TenantMigrationRunner} is {@code @Order(50)}) inside one
 * ShedLock lease so two instances cannot race. Tenants provisioned later are covered by the normal
 * write path. Never fails application startup: each tenant is isolated and the whole run is
 * guarded.
 */
@Component
@Order(60)
public class PortalContactDirectoryBackfillRunner implements ApplicationRunner {

  private static final Logger log =
      LoggerFactory.getLogger(PortalContactDirectoryBackfillRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final PortalContactDirectoryRepository directoryRepository;
  private final PortalContactDirectoryService directoryService;

  public PortalContactDirectoryBackfillRunner(
      OrgSchemaMappingRepository mappingRepository,
      PortalContactDirectoryRepository directoryRepository,
      PortalContactDirectoryService directoryService) {
    this.mappingRepository = mappingRepository;
    this.directoryRepository = directoryRepository;
    this.directoryService = directoryService;
  }

  @Override
  @SchedulerLock(name = "portal_contact_directory_backfill", lockAtMostFor = "30m")
  public void run(ApplicationArguments args) {
    try {
      backfillAll();
    } catch (Exception e) {
      // Never block startup — unmarked tenants are retried at the next start.
      log.error("Portal contact directory backfill aborted", e);
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
            "Portal contact directory backfill failed for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getExternalOrgId(),
            e);
      }
    }
    log.info(
        "Portal contact directory backfill: {} tenants scanned, {} skipped (already marked), {}"
            + " failed, {} rows upserted",
        scanned,
        skipped,
        failed,
        rowsUpserted);
  }

  /**
   * Backfills one tenant. Returns the number of directory rows upserted, or {@code -1} when the
   * tenant's marker already existed (skipped). Package-private for targeted tests.
   */
  int backfillTenant(OrgSchemaMapping mapping) {
    if (directoryRepository.hasBackfillMarker(mapping.getSchemaName())) {
      return -1;
    }
    int rows =
        directoryService.reconcileOrg(
            mapping.getExternalOrgId(), mapping.getSchemaName(), mapping.getShardId());
    directoryRepository.insertBackfillMarker(mapping.getSchemaName(), rows);
    return rows;
  }
}
