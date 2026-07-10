package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily fanout for the collections scan (Phase 83, ADR-325). Enqueues one {@code collections_scan}
 * job per tenant via the Phase 75 job queue; per-tenant work happens in {@link
 * CollectionsScanHandler} with tenant scope pre-bound by {@code JobWorker}. No dual-mode: {@code
 * collections_scan} is a new job type with no legacy inline path to migrate.
 */
@Component
public class CollectionsScanScheduler {

  private static final Logger log = LoggerFactory.getLogger(CollectionsScanScheduler.class);

  private final JobEnqueuer jobEnqueuer;

  public CollectionsScanScheduler(JobEnqueuer jobEnqueuer) {
    this.jobEnqueuer = jobEnqueuer;
  }

  @SchedulerLock(name = "collections_scan_daily", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 6 * * *")
  public void enqueueDailyScan() {
    log.debug("CollectionsScanScheduler: fanning out collections_scan");
    jobEnqueuer.fanOutToAllTenants("collections_scan", null);
  }
}
