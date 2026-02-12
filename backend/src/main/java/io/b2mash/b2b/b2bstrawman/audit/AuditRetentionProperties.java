package io.b2mash.b2b.b2bstrawman.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for audit event retention. The scheduled purge job is not implemented in
 * Phase 6 -- only the configuration is defined here for future use.
 *
 * @param domainEventsDays retention period for domain events (e.g., task.created) in days
 * @param securityEventsDays retention period for security events (e.g., auth.login) in days
 * @param purgeEnabled whether the scheduled purge job is enabled
 */
@ConfigurationProperties(prefix = "audit.retention")
public record AuditRetentionProperties(
    int domainEventsDays, int securityEventsDays, boolean purgeEnabled) {}
