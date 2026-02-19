package io.b2mash.b2b.b2bstrawman.projecttemplate.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a project template is created (manually or from a project). */
public record TemplateCreatedEvent(
    UUID templateId,
    String templateName,
    String source,
    UUID actorMemberId,
    String tenantId,
    String orgId,
    Instant occurredAt) {}
