package io.b2mash.b2b.b2bstrawman.correspondence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body-bearing boundary record for a single correspondence (Epic 587B, {@code get_correspondence}).
 *
 * <p>Mirrors {@link CorrespondenceScope}: an in-JVM value object mapped from the {@link
 * Correspondence} entity INSIDE {@link CorrespondenceService} so the JPA entity never crosses the
 * MCP/service boundary. Unlike {@code CorrespondenceScope} (body-less, used by {@code
 * attach_document}), this record carries the {@code bodyText}/{@code bodyHtml} payload and headers
 * — the read-back content the correspondence-digest skill consumes. It is never persisted.
 *
 * <p>{@code direction} is the short {@code Direction} enum name so this record is enum-free.
 *
 * @param id correspondence id
 * @param customerId linked client id, or {@code null}
 * @param projectId linked matter id, or {@code null}
 * @param direction short {@code Direction} enum name ({@code INBOUND}/{@code OUTBOUND})
 * @param subject email subject (nullable)
 * @param bodyText plain-text body (nullable)
 * @param bodyHtml HTML body (nullable)
 * @param fromAddress sender address
 * @param toAddresses recipient addresses (nullable)
 * @param ccAddresses cc addresses (nullable)
 * @param sentAt sent timestamp (nullable)
 * @param receivedAt received timestamp (nullable)
 * @param threadKey thread grouping key (nullable)
 * @param messageId provider message id / idempotency key
 * @param attachmentCount number of attached documents
 * @param filedAt filed timestamp (non-null)
 */
public record CorrespondenceDetail(
    UUID id,
    UUID customerId,
    UUID projectId,
    String direction,
    String subject,
    String bodyText,
    String bodyHtml,
    String fromAddress,
    List<String> toAddresses,
    List<String> ccAddresses,
    Instant sentAt,
    Instant receivedAt,
    String threadKey,
    String messageId,
    long attachmentCount,
    Instant filedAt) {

  static CorrespondenceDetail of(Correspondence c, long attachmentCount) {
    return new CorrespondenceDetail(
        c.getId(),
        c.getCustomerId(),
        c.getProjectId(),
        c.getDirection() == null ? null : c.getDirection().name(),
        c.getSubject(),
        c.getBodyText(),
        c.getBodyHtml(),
        c.getFromAddress(),
        c.getToAddresses(),
        c.getCcAddresses(),
        c.getSentAt(),
        c.getReceivedAt(),
        c.getThreadKey(),
        c.getMessageId(),
        attachmentCount,
        c.getFiledAt());
  }
}
