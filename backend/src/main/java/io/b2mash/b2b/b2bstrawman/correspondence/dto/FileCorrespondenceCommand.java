package io.b2mash.b2b.b2bstrawman.correspondence.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Input to {@code CorrespondenceService.fileInbound}. {@code matterId} == projectId on the entity
 * (matters are {@code Project}s in Kazi).
 */
public record FileCorrespondenceCommand(
    UUID matterId, // == projectId; the matter the email is filed into
    UUID customerId,
    String messageId, // idempotency key, required
    String subject,
    String bodyText,
    String bodyHtml,
    String fromAddress,
    List<String> toAddresses,
    List<String> ccAddresses,
    Instant sentAt,
    Instant receivedAt,
    String threadKey,
    String source) {}
