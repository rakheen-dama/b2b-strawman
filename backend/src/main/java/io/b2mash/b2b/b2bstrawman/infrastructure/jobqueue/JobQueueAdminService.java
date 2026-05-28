package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueueAdminService {

  private final JobQueueRepository jobQueueRepository;

  public JobQueueAdminService(JobQueueRepository jobQueueRepository) {
    this.jobQueueRepository = jobQueueRepository;
  }

  @Transactional(readOnly = true)
  public Page<JobQueue> listJobs(JobStatus status, String jobType, int limit) {
    return jobQueueRepository.findByStatusAndJobType(
        status, jobType, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  @Transactional
  public JobQueue retryJob(UUID id) {
    var job =
        jobQueueRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id));

    if (job.getStatus() != JobStatus.DEAD_LETTER) {
      throw new InvalidStateException(
          "Invalid job status",
          "Only DEAD_LETTER jobs can be retried; current status is " + job.getStatus());
    }

    job.setStatus(JobStatus.PENDING);
    job.setRetryCount(0);
    job.setClaimedBy(null);
    job.setClaimedAt(null);
    job.setNextAttemptAt(Instant.now());
    job.setErrorMessage(null);

    return jobQueueRepository.save(job);
  }

  @Transactional
  public void deleteJob(UUID id) {
    var job =
        jobQueueRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id));

    if (job.getStatus() != JobStatus.DEAD_LETTER) {
      throw new InvalidStateException(
          "Invalid job status",
          "Only DEAD_LETTER jobs can be deleted; current status is " + job.getStatus());
    }

    jobQueueRepository.delete(job);
  }

  @Transactional(readOnly = true)
  public JobQueueStatsResponse getStats() {
    // Aggregate by status
    Map<JobStatus, Long> byStatus = new EnumMap<>(JobStatus.class);
    for (Object[] row : jobQueueRepository.countByStatus()) {
      byStatus.put((JobStatus) row[0], (Long) row[1]);
    }

    // Aggregate by jobType and status
    Map<String, Map<JobStatus, Long>> byJobType = new HashMap<>();
    for (Object[] row : jobQueueRepository.countByStatusAndJobType()) {
      var status = (JobStatus) row[0];
      var jobType = (String) row[1];
      var count = (Long) row[2];
      byJobType.computeIfAbsent(jobType, k -> new EnumMap<>(JobStatus.class)).put(status, count);
    }

    Instant oldestPending = jobQueueRepository.findOldestPendingCreatedAt();
    Instant oldestClaimed = jobQueueRepository.findOldestClaimedAt();

    return new JobQueueStatsResponse(byStatus, byJobType, oldestPending, oldestClaimed);
  }

  // --- DTOs ---

  record JobQueueStatsResponse(
      Map<JobStatus, Long> byStatus,
      Map<String, Map<JobStatus, Long>> byJobType,
      Instant oldestPending,
      Instant oldestClaimed) {}
}
