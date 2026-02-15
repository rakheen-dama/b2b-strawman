package io.b2mash.b2b.b2bstrawman.event;

import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a document is generated from a template. Used for notifications and activity feed.
 */
public record DocumentGeneratedEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt,
    Map<String, Object> details,
    String templateName,
    TemplateEntityType primaryEntityType,
    UUID primaryEntityId,
    String fileName,
    UUID generatedDocumentId)
    implements DomainEvent {}
