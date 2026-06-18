package io.b2mash.b2b.b2bstrawman.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, sanitised metadata for the {@code mcp.tool.invoked} audit event (Epic 567A.1). Built
 * by each tool from its already-materialised result at the {@code emitInvoked} call site (there is
 * no central {@code tools/call} dispatch in Spring AI 2.0.0-M6, so "all tools inherit it" is
 * achieved by enriching the shared {@link McpToolAudit} helper plus the per-tool call sites).
 *
 * <p><b>POPIA / no-PII contract:</b> the {@code paramsSummary} map and {@code entityRefs} list
 * carry ONLY ids, enums, counts and booleans — NEVER member/client names, email addresses, or any
 * other free text. The {@link Builder#param(String, Object)} method silently drops {@code null}
 * values so absent filters never appear, and callers are responsible for only passing structural
 * values.
 *
 * <p>The resulting {@link #toDetails(String)} map is the {@code details} JSONB payload of the audit
 * event: {@code {"tool": <name>, "rowCount": <int>, "entityRefs": [<uuid strings>], "params":
 * {<id/enum summary>}}}. Keys with no data (zero rows, no refs, no params) are omitted to keep the
 * payload tight.
 */
public final class McpAuditMetadata {

  private final Integer rowCount;
  private final List<String> entityRefs;
  private final Map<String, Object> paramsSummary;

  private McpAuditMetadata(
      Integer rowCount, List<String> entityRefs, Map<String, Object> paramsSummary) {
    this.rowCount = rowCount;
    this.entityRefs = entityRefs;
    this.paramsSummary = paramsSummary;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Render the sanitised metadata into the audit {@code details} map under {@code tool}. Only
   * populated dimensions are included.
   */
  public Map<String, Object> toDetails(String tool) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("tool", tool);
    if (rowCount != null) {
      details.put("rowCount", rowCount);
    }
    if (entityRefs != null && !entityRefs.isEmpty()) {
      details.put("entityRefs", List.copyOf(entityRefs));
    }
    if (paramsSummary != null && !paramsSummary.isEmpty()) {
      details.put("params", Map.copyOf(paramsSummary));
    }
    return details;
  }

  public static final class Builder {
    private Integer rowCount;
    private final java.util.LinkedHashSet<String> entityRefs = new java.util.LinkedHashSet<>();
    private final Map<String, Object> paramsSummary = new LinkedHashMap<>();

    private Builder() {}

    /** Number of rows materialised by the read (the result row count, not the page size). */
    public Builder rowCount(int count) {
      this.rowCount = count;
      return this;
    }

    /** Add one entity id touched by the call (matter/customer/invoice/document). Ignores null. */
    public Builder entityRef(UUID id) {
      if (id != null) {
        entityRefs.add(id.toString());
      }
      return this;
    }

    /** Add a collection of entity ids touched by the call. Ignores null ids / null collection. */
    public Builder entityRefs(java.util.Collection<UUID> ids) {
      if (ids != null) {
        ids.forEach(this::entityRef);
      }
      return this;
    }

    /**
     * Record a single request parameter in the sanitised summary. <b>Pass ONLY ids/enums/counts —
     * never free-text PII.</b> Null values are dropped so absent filters do not appear.
     */
    public Builder param(String key, Object value) {
      if (key != null && value != null) {
        if (value instanceof UUID id) {
          paramsSummary.put(key, id.toString());
        } else {
          paramsSummary.put(key, value);
        }
      }
      return this;
    }

    public McpAuditMetadata build() {
      return new McpAuditMetadata(rowCount, List.copyOf(entityRefs), Map.copyOf(paramsSummary));
    }
  }
}
