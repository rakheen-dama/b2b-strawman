package io.b2mash.b2b.b2bstrawman.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Non-JPA DTO passed to {@link AuditService#log(AuditEventRecord)} for recording audit events.
 * Constructed by {@link AuditEventBuilder} which auto-populates actor, source, and request
 * metadata.
 *
 * @param eventType free-form event type following {@code {entity}.{action}} convention
 * @param entityType the kind of entity being audited (e.g., "project", "task")
 * @param entityId ID of the affected entity (not a FK -- entity may be deleted later)
 * @param actorId member ID of the acting user; null for system-initiated events
 * @param actorType USER, SYSTEM, or WEBHOOK
 * @param source origin of the action: API, INTERNAL, WEBHOOK, SCHEDULED
 * @param ipAddress client IP; null for non-HTTP sources
 * @param userAgent truncated User-Agent header; null for non-HTTP sources
 * @param details key field changes as JSONB; nullable
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
