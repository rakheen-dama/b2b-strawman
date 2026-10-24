package io.b2mash.b2b.b2bstrawman.template;

import java.util.UUID;

/**
 * Phase 88 U13 / ADR-350 — published by {@link GeneratedDocumentService#share} after a generated
 * matter document is shared with the client. {@code PortalDocumentNotificationHandler} emails the
 * client's portal contact AFTER_COMMIT when {@code notifyClient} is true.
 *
 * <p>Deliberately NOT a {@code DomainEvent}: it must not wake the firm-side "document generated"
 * in-app notifications ({@code NotificationEventHandler.onDocumentGenerated}), and it carries the
 * shard id explicitly because the listener runs after the publishing request scope has ended.
 *
 * @param templateSlug the template SLUG (drives the email headline + dedup key); may be null when
 *     the template has since been deleted
 * @param visibility the linked Document's visibility after the share (SHARED or PORTAL)
 */
public record GeneratedDocumentSharedEvent(
    String tenantId,
    String orgId,
    String shardId,
    UUID projectId,
    UUID generatedDocumentId,
    String templateSlug,
    String fileName,
    String visibility,
    boolean notifyClient) {}
