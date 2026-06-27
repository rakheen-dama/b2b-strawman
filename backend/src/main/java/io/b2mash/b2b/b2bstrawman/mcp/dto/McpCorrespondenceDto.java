package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flat MCP-facing detail DTO for {@code get_correspondence} (Epic 587B). Mapped from the in-JVM
 * {@link CorrespondenceDetail} boundary record (the JPA entity never reaches here). Follows the
 * {@link McpMatterDto} shape: a flat record with short enum names ({@code direction}) and {@link
 * Instant} timestamps (Jackson serialises them as ISO-8601 by default).
 *
 * <p>{@code bodyText}/{@code bodyHtml} are the read-back payload egressed to the LLM by design —
 * gated only by the existing MCP enablement + POPIA read-egress consent ({@code
 * McpEnablementService.effectiveState()}), the same posture as {@code get_matter}/{@code
 * get_client}. No new consent flag or audit family.
 */
public record McpCorrespondenceDto(
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

  public static McpCorrespondenceDto from(CorrespondenceDetail d) {
    return new McpCorrespondenceDto(
        d.id(),
        d.customerId(),
        d.projectId(),
        d.direction(),
        d.subject(),
        d.bodyText(),
        d.bodyHtml(),
        d.fromAddress(),
        d.toAddresses(),
        d.ccAddresses(),
        d.sentAt(),
        d.receivedAt(),
        d.threadKey(),
        d.messageId(),
        d.attachmentCount(),
        d.filedAt());
  }
}
