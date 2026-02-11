package io.b2mash.b2b.b2bstrawman.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Non-JPA DTO representing the data needed to create an audit event. Passed to {@code
 * AuditService.log()} by domain services and the {@link AuditEventBuilder}.
 */
public record AuditEventRecord(
    String eventType,
    String entityType,
    UUID entityId,
    UUID actorId,
    String actorType,
    String source,
    String ipAddress,
    String userAgent,
    Map<String, Object> details) {}
