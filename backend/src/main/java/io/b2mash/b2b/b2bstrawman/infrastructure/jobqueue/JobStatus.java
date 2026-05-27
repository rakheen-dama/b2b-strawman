package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

/** Status of a job in the distributed job queue. */
public enum JobStatus {
  PENDING,
  CLAIMED,
  COMPLETED,
  FAILED,
  DEAD_LETTER
}
