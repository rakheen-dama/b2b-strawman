package io.b2mash.b2b.b2bstrawman.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable, sanitised metadata for the {@code mcp.tool.invoked} audit event (Epic 567A.1). Built
 * by each tool from its already-materialised result at the {@code emitInvoked} call site (there is
 * no central {@code tools/call} dispatch in Spring AI 2.0.0-M6, so "all tools inherit it" is
 * achieved by enriching the shared {@link McpToolAudit} helper plus the per-tool call sites).
 *
 * <p>A record (repo convention for value objects); the compact canonical constructor defensively
 * copies the collections so the value object is genuinely immutable. The {@link Builder} is kept
 * because every tool call site uses it.
 *
 * <p><b>POPIA / no-PII contract:</b> the {@code paramsSummary} map and {@code entityRefs} list
 * carry ONLY ids, enums, counts and booleans — NEVER member/client names, email addresses, or any
 * other free text. The {@link Builder#param(String, Object)} method enforces this <b>at the
 * sink</b>: it runs every value through {@link #sanitizeParamValue(Object)} and drops anything that
 * is not a structurally-safe token (id / enum / number / boolean / short safe-charset string), so
 * the no-PII guarantee holds for ALL callers regardless of what they pass.
 *
 * <p>The resulting {@link #toDetails(String)} map is the {@code details} JSONB payload of the audit
 * event: {@code {"tool": <name>, "rowCount": <int>, "entityRefs": [<uuid strings>], "params":
 * {<id/enum summary>}}}. Keys with no data (zero rows, no refs, no params) are omitted to keep the
 * payload tight.
 */
public record McpAuditMetadata(
    Integer rowCount, List<String> entityRefs, Map<String, Object> paramsSummary) {

  /**
   * A structurally-safe free-text token: ids/enums/short codes only (letters, digits, and the
   * {@code _ . : -} separators), 1–64 chars. Anything with spaces, {@code @}, or other punctuation
   * (i.e. names / emails / free text / PII) fails to match and is dropped at {@link
   * Builder#param(String, Object)}.
   */
  private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

  /** Canonical constructor: defensively copy (and null-tolerantly normalise) the collections. */
  public McpAuditMetadata {
    entityRefs = entityRefs == null ? List.of() : List.copyOf(entityRefs);
    paramsSummary = paramsSummary == null ? Map.of() : Map.copyOf(paramsSummary);
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
    if (!entityRefs.isEmpty()) {
      details.put("entityRefs", entityRefs);
    }
    if (!paramsSummary.isEmpty()) {
      details.put("params", paramsSummary);
    }
    return details;
  }

  /**
   * Coerce {@code value} to a structurally-safe audit token, or return {@code null} if it carries
   * (or might carry) free text / PII. This is the single sink that enforces the no-PII contract for
   * every {@link Builder#param} caller:
   *
   * <ul>
   *   <li>{@link UUID} → its canonical string id
   *   <li>{@link Enum} → its {@code name()} (a fixed structural constant, never free text)
   *   <li>{@link Number} / {@link Boolean} → as-is (counts / flags carry no PII)
   *   <li>{@link CharSequence} matching {@link #SAFE_TOKEN} → its string (short id/enum/code)
   *   <li>anything else (incl. free-text strings with spaces/@/punctuation) → {@code null}
   *       (dropped)
   * </ul>
   */
  private static Object sanitizeParamValue(Object value) {
    return switch (value) {
      case null -> null;
      case UUID id -> id.toString();
      case Enum<?> e -> e.name();
      case Number n -> n;
      case Boolean b -> b;
      case CharSequence cs -> {
        String s = cs.toString();
        yield SAFE_TOKEN.matcher(s).matches() ? s : null;
      }
      default -> null;
    };
  }

  public static final class Builder {
    private Integer rowCount;
    private final java.util.LinkedHashSet<String> entityRefs = new java.util.LinkedHashSet<>();
    private final Map<String, Object> paramsSummary = new LinkedHashMap<>();

    private Builder() {}

    /** Number of rows materialised by the read (the result row count, not the page size). */
    public Builder rowCount(int count) {
      if (count < 0) {
        throw new IllegalArgumentException("rowCount must be >= 0");
      }
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
     * Record a single request parameter in the sanitised summary. The value is run through {@link
     * McpAuditMetadata#sanitizeParamValue(Object)} so ONLY structurally-safe tokens
     * (ids/enums/counts/booleans/short safe-charset strings) are stored — free text / PII is
     * dropped. Null keys and values (and values that sanitise to null) never appear, so absent
     * filters do not pollute the summary.
     */
    public Builder param(String key, Object value) {
      if (key == null) {
        return this;
      }
      Object safe = sanitizeParamValue(value);
      if (safe != null) {
        paramsSummary.put(key, safe);
      }
      return this;
    }

    public McpAuditMetadata build() {
      return new McpAuditMetadata(rowCount, List.copyOf(entityRefs), Map.copyOf(paramsSummary));
    }
  }
}
