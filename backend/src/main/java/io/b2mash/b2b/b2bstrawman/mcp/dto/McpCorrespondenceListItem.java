package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact {@code list_correspondence} row (Epic 587A). Projected from the firm-side {@link
 * CorrespondenceListResponse} (the same metadata projection the in-app REST tab uses), so the
 * shared REST DTO is never touched and no new repository method is introduced.
 *
 * <p>MINIMAL-PATH NOTE (587A.2): {@code CorrespondenceListResponse} does NOT carry {@code
 * messageId}, and sourcing it would require either modifying that shared REST DTO or adding a
 * repository method — both out of scope. The Open Questions explicitly authorise the minimal path:
 * the list row omits {@code messageId} (the digest cross-checks by {@code id}); {@code
 * get_correspondence} returns {@code messageId} in full.
 *
 * <p>{@code direction} is rendered as the short enum name (e.g. {@code INBOUND}) so the JPA {@code
 * Direction} enum never crosses the MCP boundary. {@code receivedAt} is an {@link Instant} —
 * Jackson serialises it as ISO-8601 by default, matching the other MCP DTOs.
 *
 * @param id correspondence id
 * @param subject email subject (nullable)
 * @param fromAddress sender address
 * @param receivedAt received timestamp, or {@code null}
 * @param attachmentCount number of attached documents
 * @param direction short {@code Direction} enum name ({@code INBOUND}/{@code OUTBOUND})
 */
public record McpCorrespondenceListItem(
    UUID id,
    String subject,
    String fromAddress,
    Instant receivedAt,
    long attachmentCount,
    String direction) {

  public static McpCorrespondenceListItem from(CorrespondenceListResponse row) {
    return new McpCorrespondenceListItem(
        row.id(),
        row.subject(),
        row.fromAddress(),
        row.receivedAt(),
        row.attachmentCount(),
        row.direction() == null ? null : row.direction().name());
  }
}
