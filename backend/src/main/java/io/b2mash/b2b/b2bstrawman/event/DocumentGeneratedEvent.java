package io.b2mash.b2b.b2bstrawman.event;

import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a document is generated from a template. Used for notifications and activity feed.
 *
 * <p>Phase 88 U13 / ADR-350: {@code details.notify_client} ({@link Boolean}) carries the explicit
 * client-email intent. {@code PortalDocumentNotificationHandler} emails the client's portal contact
 * only when it is {@code true} and the document is portal-visible; publishers that have no intent
 * put {@code false}.
 *
 * @param templateName template SLUG (e.g., {@code "matter-closure-letter"}) — drives the
 *     portal-document-ready email headline and its dedup key. The human-readable display name lives
 *     in {@code details.template_name} for activity-feed UX (GAP-L-97).
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
