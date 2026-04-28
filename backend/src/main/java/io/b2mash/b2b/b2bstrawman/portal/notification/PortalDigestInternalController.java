package io.b2mash.b2b.b2bstrawman.portal.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for the weekly portal digest sweep (GAP-L-99).
 *
 * <p>{@link PortalDigestScheduler} is otherwise driven only by the {@code @Scheduled(cron = "0 0 8
 * ? * MON")} cron annotation. QA backends rarely stay up across a Monday 08:00 SAST tick, leaving
 * the digest path unverifiable end-to-end. This controller exposes a single {@code POST
 * /internal/portal/digest/run-weekly} endpoint that delegates to {@link
 * PortalDigestScheduler#runWeeklyDigest(PortalDigestScheduler.RunOptions)} so QA / dev tooling can
 * fire the sweep on demand. The cron path is unaffected.
 *
 * <p>Authentication is delegated to {@code ApiKeyAuthFilter}, which already gates every URI under
 * {@code /internal/*} via the {@code X-API-KEY} header — no per-controller annotation required.
 */
@RestController
@RequestMapping("/internal/portal/digest")
public class PortalDigestInternalController {

  private static final Logger log = LoggerFactory.getLogger(PortalDigestInternalController.class);

  private final PortalDigestScheduler scheduler;

  public PortalDigestInternalController(PortalDigestScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @PostMapping("/run-weekly")
  public ResponseEntity<PortalDigestScheduler.RunResult> runWeekly(
      @RequestParam(required = false) String orgId,
      @RequestParam(required = false) String targetEmail,
      @RequestParam(defaultValue = "false") boolean dryRun) {
    log.info(
        "Manual portal digest trigger received: orgId={}, targetEmail={}, dryRun={}",
        orgId,
        targetEmail,
        dryRun);
    PortalDigestScheduler.RunResult result =
        scheduler.runWeeklyDigest(new PortalDigestScheduler.RunOptions(orgId, targetEmail, dryRun));
    log.info(
        "Manual portal digest trigger complete: tenantsProcessed={}, digestsSent={}, skipped={},"
            + " dryRun={}, errors={}",
        result.tenantsProcessed(),
        result.digestsSent(),
        result.skipped(),
        result.dryRun(),
        result.errors().size());
    return ResponseEntity.ok(result);
  }
}
