package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.util.List;
import java.util.UUID;

/**
 * Output payload for the Inbox specialist's matter summary comment. Persisted as JSONB on {@code
 * ai_specialist_invocations.proposed_output} / {@code applied_output}.
 *
 * <p>Per architecture section 2.4 — carries the summary markdown, the lookback window, the matter
 * id, and the source references that the summary was derived from.
 *
 * <p>The lookback timestamps are stored as ISO-8601 strings rather than {@code Instant} to avoid
 * Hibernate's internal ObjectMapper lacking the JavaTimeModule for JSONB column serialisation.
 */
public record InboxSummaryPayload(
    UUID matterId,
    String lookbackFrom,
    String lookbackTo,
    String summaryMarkdown,
    List<SourceRef> sources)
    implements OutputPayload {

  public InboxSummaryPayload {
    sources = sources != null ? List.copyOf(sources) : List.of();
  }

  /**
   * A reference to a source entity that contributed to the summary. Used for audit trail and
   * traceability.
   */
  public record SourceRef(String entityType, UUID entityId) {}
}
