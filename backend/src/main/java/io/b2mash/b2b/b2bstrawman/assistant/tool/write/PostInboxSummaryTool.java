package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload.SourceRef;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SystemPromptBuilder;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Write tool: posts (REVIEW: queue / DIRECT: write) a matter summary comment. DIRECT mode is only
 * legal for INBOX specialist + comment-posting per ADR-267.
 */
@Component
public class PostInboxSummaryTool implements AssistantTool {

  private static final String SPECIALIST_ID = "inbox-za";

  private final AiSpecialistInvocationService invocationService;
  private final AiSpecialistInvocationRepository invocationRepository;
  private final SystemPromptBuilder promptBuilder;

  public PostInboxSummaryTool(
      AiSpecialistInvocationService invocationService,
      AiSpecialistInvocationRepository invocationRepository,
      SystemPromptBuilder promptBuilder) {
    this.invocationService = invocationService;
    this.invocationRepository = invocationRepository;
    this.promptBuilder = promptBuilder;
  }

  @Override
  public String name() {
    return "PostInboxSummary";
  }

  @Override
  public String description() {
    return "Post (REVIEW: queue / DIRECT: write) a matter summary comment."
        + " DIRECT only legal for INBOX + comment-posting per ADR-267.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "matterId",
            Map.of("type", "string", "format", "uuid"),
            "summaryMarkdown",
            Map.of("type", "string", "maxLength", 8000),
            "lookbackFrom",
            Map.of("type", "string", "format", "date-time"),
            "lookbackTo",
            Map.of("type", "string", "format", "date-time"),
            "sources",
            Map.of(
                "type",
                "array",
                "items",
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "entityType",
                        Map.of("type", "string"),
                        "entityId",
                        Map.of("type", "string", "format", "uuid")),
                    "required",
                    List.of("entityType", "entityId"))),
            "mode",
            Map.of("type", "string", "enum", List.of("REVIEW", "DIRECT"))),
        "required",
        List.of("matterId", "summaryMarkdown", "lookbackFrom", "lookbackTo", "sources", "mode"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("AI_ASSISTANT_USE");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    // Parse inputs
    UUID matterId;
    try {
      matterId = UUID.fromString((String) input.get("matterId"));
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid matterId format");
    }

    var summaryMarkdown = (String) input.get("summaryMarkdown");
    if (summaryMarkdown == null || summaryMarkdown.isBlank()) {
      return Map.of("error", "summaryMarkdown must not be empty");
    }

    String lookbackFrom;
    String lookbackTo;
    try {
      // Validate as parseable Instant, but store as ISO-8601 string for JSONB compatibility
      lookbackFrom = Instant.parse((String) input.get("lookbackFrom")).toString();
      lookbackTo = Instant.parse((String) input.get("lookbackTo")).toString();
    } catch (Exception e) {
      return Map.of("error", "Invalid lookbackFrom/lookbackTo format: " + e.getMessage());
    }

    var rawSources = (List<Map<String, Object>>) input.get("sources");
    List<SourceRef> sources;
    try {
      sources =
          rawSources.stream()
              .map(
                  m ->
                      new SourceRef(
                          (String) m.get("entityType"),
                          UUID.fromString((String) m.get("entityId"))))
              .toList();
    } catch (Exception e) {
      return Map.of("error", "Malformed sources: " + e.getMessage());
    }

    var mode = (String) input.get("mode");
    var payload =
        new InboxSummaryPayload(matterId, lookbackFrom, lookbackTo, summaryMarkdown, sources);
    var promptVersion = promptBuilder.promptVersion(SPECIALIST_ID);

    if ("DIRECT".equals(mode)) {
      return executeDirect(matterId, payload, promptVersion, context);
    } else {
      return executeReview(matterId, payload, promptVersion, context);
    }
  }

  private Map<String, Object> executeReview(
      UUID matterId, InboxSummaryPayload payload, String promptVersion, TenantToolContext context) {
    var inv =
        invocationService.recordRunning(
            SPECIALIST_ID,
            InvocationSource.MEMBER,
            context.memberId(),
            null,
            "project",
            matterId,
            promptVersion);
    invocationService.recordProposal(inv.getId(), payload);
    invocationService.markPendingApproval(inv.getId());

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("status", InvocationStatus.PENDING_APPROVAL.name());
    return result;
  }

  private Map<String, Object> executeDirect(
      UUID matterId, InboxSummaryPayload payload, String promptVersion, TenantToolContext context) {
    // Dedupe check: same specialist + context entity + same hour.
    // NOTE: This is a best-effort check subject to TOCTOU races — two concurrent requests
    // for the same matter in the same hour can both pass before either writes. Acceptable
    // because duplicate summaries are cosmetic (extra comment) not data-corrupting, and the
    // window is narrow. A pessimistic/advisory lock would add complexity for minimal gain.
    Instant now = Instant.now();
    Instant hourStart = now.truncatedTo(ChronoUnit.HOURS);
    Instant hourEnd = hourStart.plus(1, ChronoUnit.HOURS);

    var existing =
        invocationRepository.findBySpecialistIdAndContextEntityIdAndStatusAndCreatedAtBetween(
            SPECIALIST_ID, matterId, InvocationStatus.AUTO_APPLIED, hourStart, hourEnd);

    if (existing != null && !existing.isEmpty()) {
      var result = new LinkedHashMap<String, Object>();
      result.put(
          "error",
          "Duplicate: an inbox summary was already posted for this matter within the current hour");
      result.put("existingInvocationId", existing.get(0).getId().toString());
      return result;
    }

    // Record + apply + mark AUTO_APPLIED atomically via the service's transactional method.
    // This prevents partial state where a comment is posted but the invocation stays RUNNING.
    var inv =
        invocationService.recordAndAutoApply(
            SPECIALIST_ID,
            InvocationSource.MEMBER,
            context.memberId(),
            "project",
            matterId,
            promptVersion,
            payload);

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("status", InvocationStatus.AUTO_APPLIED.name());
    return result;
  }
}
