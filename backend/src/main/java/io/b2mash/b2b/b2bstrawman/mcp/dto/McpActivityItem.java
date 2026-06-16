package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.activity.ActivityItem;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact {@code get_matter_activity} row (§11.4): {@code {occurredAt, entityType, action, actor}}.
 *
 * <p>Mapping: {@code action <- eventType} (machine code), {@code message} carries the
 * human-readable sentence, {@code actor <- actorName}. {@code entityType}/{@code entityId}/{@code
 * entityName} are carried through so the LLM can correlate the event with the referenced entity.
 */
public record McpActivityItem(
    Instant occurredAt,
    String entityType,
    UUID entityId,
    String entityName,
    String action,
    String message,
    String actor) {

  public static McpActivityItem from(ActivityItem item) {
    return new McpActivityItem(
        item.occurredAt(),
        item.entityType(),
        item.entityId(),
        item.entityName(),
        item.eventType(),
        item.message(),
        item.actorName());
  }
}
