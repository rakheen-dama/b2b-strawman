package io.b2mash.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable rate limiting thresholds. Defaults are generous — tune per deployment. */
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

  /** Whether rate limiting is enabled. Disable in tests or dev if needed. */
  private boolean enabled = true;

  /** Max burst size per IP address. */
  private int ipCapacity = 100;

  /** Tokens refilled per interval for IP buckets. */
  private int ipRefillTokens = 100;

  /** Refill interval for IP buckets. */
  private Duration ipRefillInterval = Duration.ofSeconds(1);

  /** Max burst size per tenant (org). */
  private int tenantCapacity = 200;

  /** Tokens refilled per interval for tenant buckets. */
  private int tenantRefillTokens = 200;

  /** Refill interval for tenant buckets. */
  private Duration tenantRefillInterval = Duration.ofSeconds(1);

  // Getters and setters

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getIpCapacity() {
    return ipCapacity;
  }

  public void setIpCapacity(int ipCapacity) {
    this.ipCapacity = ipCapacity;
  }

  public int getIpRefillTokens() {
    return ipRefillTokens;
  }

  public void setIpRefillTokens(int ipRefillTokens) {
    this.ipRefillTokens = ipRefillTokens;
  }

  public Duration getIpRefillInterval() {
    return ipRefillInterval;
  }

  public void setIpRefillInterval(Duration ipRefillInterval) {
    this.ipRefillInterval = ipRefillInterval;
  }

  public int getTenantCapacity() {
    return tenantCapacity;
  }

  public void setTenantCapacity(int tenantCapacity) {
    this.tenantCapacity = tenantCapacity;
  }

  public int getTenantRefillTokens() {
    return tenantRefillTokens;
  }

  public void setTenantRefillTokens(int tenantRefillTokens) {
    this.tenantRefillTokens = tenantRefillTokens;
  }

  public Duration getTenantRefillInterval() {
    return tenantRefillInterval;
  }

  public void setTenantRefillInterval(Duration tenantRefillInterval) {
    this.tenantRefillInterval = tenantRefillInterval;
  }
}
