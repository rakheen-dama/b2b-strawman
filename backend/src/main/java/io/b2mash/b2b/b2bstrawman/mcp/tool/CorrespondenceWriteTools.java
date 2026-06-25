package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceService;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpCapabilityGuard;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP <b>write</b> tools for inbound correspondence (Phase 81, ADR-321). This is the first write
 * chapter on the otherwise read-only Phase 78 MCP server.
 *
 * <p>Each tool carries the inline write-guard preamble: enablement + POPIA-consent check, then the
 * {@code MCP_WRITE} capability gate via {@link McpCapabilityGuard#gatedTool}. Spring AI M6 has no
 * central {@code tools/call} interceptor, so the enforcement is per-tool by construction.
 *
 * <p>This slice (582B) ships only {@code file_correspondence}. {@code attach_document} (583A) and
 * {@code propose_task} (585B) land later and add their own service dependencies — they are NOT
 * injected here. The read tool {@code resolve_matter_by_email} lives with the read-tool family, not
 * here (§N.8).
 */
@Component
public class CorrespondenceWriteTools {

  private final CorrespondenceService correspondenceService;
  private final McpEnablementService enablement;
  private final AuditService auditService;
  private final McpMetrics metrics;
  private final ObjectMapper objectMapper;

  public CorrespondenceWriteTools(
      CorrespondenceService correspondenceService,
      McpEnablementService enablement,
      AuditService auditService,
      McpMetrics metrics,
      ObjectMapper objectMapper) {
    this.correspondenceService = correspondenceService;
    this.enablement = enablement;
    this.auditService = auditService;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "file_correspondence",
      description =
          "File an inbound email into a matter and/or client. Provide at least one of matterId or"
              + " customerId. Idempotent on messageId — re-filing the same messageId is a no-op that"
              + " returns the existing correspondence id. Requires the MCP_WRITE capability.")
  public Object fileCorrespondence(
      @McpToolParam(required = false, description = "Matter (project) id to file the email into.")
          UUID matterId,
      @McpToolParam(
              required = false,
              description = "Client (customer) id to file the email against.")
          UUID customerId,
      @McpToolParam(description = "Stable RFC-822 Message-ID or externalId — the idempotency key.")
          String messageId,
      @McpToolParam(required = false, description = "Email subject line.") String subject,
      @McpToolParam(required = false, description = "Plain-text body of the email.")
          String bodyText,
      @McpToolParam(required = false, description = "HTML body of the email.") String bodyHtml,
      @McpToolParam(required = false, description = "Sender email address.") String fromAddress,
      @McpToolParam(required = false, description = "Recipient (To) addresses.")
          List<String> toAddresses,
      @McpToolParam(required = false, description = "Carbon-copy (Cc) addresses.")
          List<String> ccAddresses,
      @McpToolParam(required = false, description = "When the email was sent (ISO-8601 instant).")
          Instant sentAt,
      @McpToolParam(
              required = false,
              description = "When the email was received (ISO-8601 instant).")
          Instant receivedAt,
      @McpToolParam(
              required = false,
              description = "Provider thread key to group the conversation.")
          String threadKey,
      @McpToolParam(required = false, description = "Source system label (defaults to MCP).")
          String source) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        "MCP_WRITE",
        "file_correspondence",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          var actor = ActorContext.fromRequestScopes();
          var cmd =
              new FileCorrespondenceCommand(
                  matterId,
                  customerId,
                  messageId,
                  subject,
                  bodyText,
                  bodyHtml,
                  fromAddress,
                  toAddresses,
                  ccAddresses,
                  sentAt,
                  receivedAt,
                  threadKey,
                  source);
          try {
            var result = correspondenceService.fileInbound(cmd, actor);
            emitWriteAudit(result.correspondenceId(), result.idempotent());
            metrics.recordOk("file_correspondence", McpToolAudit.elapsed(startNanos));
            return new FileCorrespondenceToolResponse(
                result.correspondenceId(), result.idempotent());
          } catch (InvalidStateException e) {
            metrics.recordError("file_correspondence", McpToolAudit.elapsed(startNanos));
            return McpToolErrors.asResult(
                McpError.invalidRequest("Provide at least one of matterId or customerId."),
                objectMapper);
          }
        });
  }

  private void emitWriteAudit(UUID correspondenceId, boolean idempotent) {
    String eventType =
        idempotent ? "mcp.write.correspondence_refiled" : "mcp.write.correspondence_filed";
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("correspondence")
            .entityId(correspondenceId)
            .details(
                McpAuditMetadata.builder()
                    .rowCount(1)
                    .entityRef(correspondenceId)
                    .param("idempotent", idempotent)
                    .build()
                    .toDetails("file_correspondence"))
            .build());
  }
}
