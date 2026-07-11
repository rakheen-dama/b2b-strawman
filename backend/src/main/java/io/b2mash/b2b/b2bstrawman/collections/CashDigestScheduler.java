package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly fanout for the cash digest (Phase 83, ADR-328). Enqueues one {@code cash_digest} job per
 * tenant via the Phase 75 job queue; per-tenant work happens in {@link CashDigestHandler} with
 * tenant scope pre-bound by {@code JobWorker}. Monday 07:00 — before the portal digest (08:00). No
 * dual-mode: {@code cash_digest} is a new job type with no legacy inline path to migrate.
 */
@Component
public class CashDigestScheduler {

  private static final Logger log = LoggerFactory.getLogger(CashDigestScheduler.class);

  private final JobEnqueuer jobEnqueuer;

  public CashDigestScheduler(JobEnqueuer jobEnqueuer) {
    this.jobEnqueuer = jobEnqueuer;
  }

  @SchedulerLock(name = "cash_digest_weekly", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 7 ? * MON")
  public void enqueueWeeklyDigest() {
    log.debug("CashDigestScheduler: fanning out cash_digest");
    jobEnqueuer.fanOutToAllTenants("cash_digest", null);
  }
}
