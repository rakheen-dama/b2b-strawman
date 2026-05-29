package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Poll-based job worker that claims and executes jobs from the distributed job queue. Uses virtual
 * threads for execution and {@code FOR UPDATE SKIP LOCKED} for contention-free claiming across
 * multiple pods.
 *
 * <p>Implements {@link SmartLifecycle} with a high phase value to ensure the poll loop stops before
 * the DataSource pool closes during shutdown.
 */
@Component
@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")
public class JobWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private static final long SHUTDOWN_TIMEOUT_MS = 30_000;

  private volatile boolean running = false;
  private Thread pollThread;

  private final JobQueueRepository repository;
  private final JobHandlerRegistry handlerRegistry;
  private final JobQueueProperties properties;
  private final PlatformTransactionManager transactionManager;
  private final JobQueueMetrics metrics;

  public JobWorker(
      JobQueueRepository repository,
      JobHandlerRegistry handlerRegistry,
      JobQueueProperties properties,
      PlatformTransactionManager transactionManager,
      JobQueueMetrics metrics) {
    this.repository = repository;
    this.handlerRegistry = handlerRegistry;
    this.properties = properties;
    this.transactionManager = transactionManager;
    this.metrics = metrics;
  }

  @Override
  public void start() {
    if (running) {
      log.debug("JobWorker.start() called but already running — ignoring");
      return;
    }
    running = true;
    pollThread = Thread.ofVirtual().name("job-worker-poll").start(this::pollLoop);
    log.info(
        "JobWorker started — pollInterval={}ms, batchSize={}",
        properties.getPollIntervalMs(),
        properties.getBatchSize());
  }

  private void pollLoop() {
    while (running) {
      try {
        List<JobQueue> claimed = claimBatch();
        for (var job : claimed) {
          processJob(job);
        }
        if (claimed.isEmpty()) {
          Thread.sleep(properties.getPollIntervalMs());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Poll loop error: {}", e.getMessage(), e);
        try {
          Thread.sleep(properties.getPollIntervalMs());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  private List<JobQueue> claimBatch() {
    var tt = new TransactionTemplate(transactionManager);
    var result =
        tt.execute(
            status -> {
              var jobs = repository.findClaimable(properties.getBatchSize());
              var now = Instant.now();
              String pod = podId();
              for (var job : jobs) {
                job.setStatus(JobStatus.CLAIMED);
                job.setClaimedBy(pod);
                job.setClaimedAt(now);
              }
              repository.flush();
              return jobs;
            });
    return result != null ? result : List.of();
  }

  private void processJob(JobQueue job) {
    MDC.put("tenantId", job.getTenantId());
    MDC.put("orgId", job.getOrgId());
    MDC.put("shardId", job.getShardId());
    MDC.put("jobType", job.getJobType());
    MDC.put("jobId", job.getId().toString());
    try {
      // Execute the handler in shard-aware tenant scope — only the handler runs with TENANT_ID,
      // ORG_ID and SHARD_ID bound. Binding SHARD_ID is mandatory: without it the
      // TenantIdentifierResolver defaults to the primary shard, so a job for a secondary-shard
      // tenant would silently execute against the wrong database (review finding D1).
      // The worker's own DB operations (markCompleted/handleFailure) must run against the
      // public schema since job_queue lives in public, not in a tenant schema.
      //
      // Note: handlers that operate only on global (public.*) tables — e.g. the subscription
      // expiry handlers — still run inside this scope, but ignore the bound schema. That is
      // intentional; there is no separate global-handler type today (review finding S3).
      RequestScopes.runForTenantOnShard(
          job.getTenantId(),
          job.getOrgId(),
          job.getShardId(),
          () -> {
            var handler = handlerRegistry.getHandler(job.getJobType());
            handler.execute(job.getPayload());
          });
      markCompleted(job);
    } catch (Exception e) {
      handleFailure(job, e);
    } finally {
      MDC.remove("tenantId");
      MDC.remove("orgId");
      MDC.remove("shardId");
      MDC.remove("jobType");
      MDC.remove("jobId");
    }
  }

  private void markCompleted(JobQueue job) {
    var tt = new TransactionTemplate(transactionManager);
    tt.executeWithoutResult(
        status -> {
          job.setStatus(JobStatus.COMPLETED);
          job.setCompletedAt(Instant.now());
          repository.save(job);
        });
    metrics.recordCompleted(job.getJobType(), job.getCreatedAt(), job.getClaimedAt());
  }

  private void handleFailure(JobQueue job, Exception e) {
    Instant claimedAt = job.getClaimedAt();
    var tt = new TransactionTemplate(transactionManager);
    tt.executeWithoutResult(
        status -> {
          int newRetryCount = job.getRetryCount() + 1;
          job.setRetryCount(newRetryCount);

          String errorMsg = e.getMessage();
          if (errorMsg != null && errorMsg.length() > MAX_ERROR_MESSAGE_LENGTH) {
            errorMsg = errorMsg.substring(0, MAX_ERROR_MESSAGE_LENGTH);
          }
          job.setErrorMessage(errorMsg);

          if (newRetryCount >= job.getMaxRetries()) {
            job.setStatus(JobStatus.DEAD_LETTER);
            job.setCompletedAt(Instant.now());
            log.error(
                "Job dead-lettered: id={}, type={}, tenant={}, error={}",
                job.getId(),
                job.getJobType(),
                job.getTenantId(),
                errorMsg);
          } else {
            job.setStatus(JobStatus.PENDING);
            long backoffSeconds =
                (long) Math.pow(2, newRetryCount) * properties.getBackoffBaseSeconds();
            job.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
            job.setClaimedBy(null);
            job.setClaimedAt(null);
            log.warn(
                "Job failed, will retry: id={}, type={}, tenant={}, retry={}/{}, nextAttempt={}",
                job.getId(),
                job.getJobType(),
                job.getTenantId(),
                newRetryCount,
                job.getMaxRetries(),
                job.getNextAttemptAt());
          }
          repository.save(job);
        });

    // Record metrics after the transaction commits
    int retryCount = job.getRetryCount();
    if (retryCount >= job.getMaxRetries()) {
      metrics.recordDeadLettered(job.getJobType(), claimedAt);
    } else {
      metrics.recordFailed(job.getJobType(), claimedAt);
    }
  }

  @Override
  public void stop() {
    if (!running) {
      log.debug("JobWorker.stop() called but not running — ignoring");
      return;
    }
    running = false;
    if (pollThread != null) {
      pollThread.interrupt();
      try {
        pollThread.join(SHUTDOWN_TIMEOUT_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (pollThread.isAlive()) {
        log.warn(
            "JobWorker poll thread still alive after {}ms shutdown timeout", SHUTDOWN_TIMEOUT_MS);
      }
      pollThread = null;
    }
    log.info("JobWorker stopped");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean isAutoStartup() {
    return properties.isAutoStart();
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 10;
  }

  private String podId() {
    String hostname = System.getenv("HOSTNAME");
    if (hostname != null && !hostname.isBlank()) {
      return hostname;
    }
    try {
      return java.net.InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown-pod";
    }
  }
}
