package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the distributed job queue. Bound from {@code kazi.job-queue} in
 * application YAML.
 */
@ConfigurationProperties("kazi.job-queue")
public class JobQueueProperties {

  private boolean enabled = false;
  private int batchSize = 20;
  private long pollIntervalMs = 2000;
  private int staleClaimTimeoutMinutes = 15;
  private int maxRetriesDefault = 3;
  private int backoffBaseSeconds = 10;
  private Map<String, Boolean> dualMode = new HashMap<>();

  /**
   * Whether a job type should execute in dual mode (both inline and via queue) during migration.
   *
   * @param jobType the job type to check
   * @return true if dual mode is enabled for this job type
   */
  public boolean isDualMode(String jobType) {
    return dualMode.getOrDefault(jobType, false);
  }

  // --- Getters and Setters ---

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public int getStaleClaimTimeoutMinutes() {
    return staleClaimTimeoutMinutes;
  }

  public void setStaleClaimTimeoutMinutes(int staleClaimTimeoutMinutes) {
    this.staleClaimTimeoutMinutes = staleClaimTimeoutMinutes;
  }

  public int getMaxRetriesDefault() {
    return maxRetriesDefault;
  }

  public void setMaxRetriesDefault(int maxRetriesDefault) {
    this.maxRetriesDefault = maxRetriesDefault;
  }

  public int getBackoffBaseSeconds() {
    return backoffBaseSeconds;
  }

  public void setBackoffBaseSeconds(int backoffBaseSeconds) {
    this.backoffBaseSeconds = backoffBaseSeconds;
  }

  public Map<String, Boolean> getDualMode() {
    return dualMode;
  }

  public void setDualMode(Map<String, Boolean> dualMode) {
    this.dualMode = dualMode;
  }
}
