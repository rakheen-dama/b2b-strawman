package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/jobs")
@PreAuthorize("@platformSecurityService.isPlatformAdmin()")
public class JobQueueAdminController {

  private final JobQueueAdminService jobQueueAdminService;

  public JobQueueAdminController(JobQueueAdminService jobQueueAdminService) {
    this.jobQueueAdminService = jobQueueAdminService;
  }

  @GetMapping
  public ResponseEntity<Page<JobQueue>> listJobs(
      @RequestParam JobStatus status,
      @RequestParam(required = false) String jobType,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(jobQueueAdminService.listJobs(status, jobType, limit));
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<RetryJobResponse> retryJob(@PathVariable UUID id) {
    return ResponseEntity.ok(RetryJobResponse.from(jobQueueAdminService.retryJob(id)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
    jobQueueAdminService.deleteJob(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/stats")
  public ResponseEntity<JobQueueStatsResponse> getStats() {
    return ResponseEntity.ok(jobQueueAdminService.getStats());
  }

  // --- DTOs ---

  record RetryJobResponse(UUID id, JobStatus status, int retryCount, Instant nextAttemptAt) {
    static RetryJobResponse from(JobQueue job) {
      return new RetryJobResponse(
          job.getId(), job.getStatus(), job.getRetryCount(), job.getNextAttemptAt());
    }
  }

  record JobQueueStatsResponse(
      Map<JobStatus, Long> byStatus,
      Map<String, Map<JobStatus, Long>> byJobType,
      Instant oldestPending,
      Instant oldestClaimed) {}
}
