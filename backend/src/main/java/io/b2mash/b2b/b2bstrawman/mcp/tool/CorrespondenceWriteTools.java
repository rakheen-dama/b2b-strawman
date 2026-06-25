package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceScope;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceService;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceResult;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpCapabilityGuard;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.AttachDocumentConfirmResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.AttachDocumentInitResponse;
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
 * <p>This family ships {@code file_correspondence} (582B) and {@code attach_document} (583A).
 * {@code propose_task} (585B) lands later and adds its own service dependencies. The read tool
 * {@code resolve_matter_by_email} lives with the read-tool family, not here (§N.8).
 */
@Component
public class CorrespondenceWriteTools {

  private final CorrespondenceService correspondenceService;
  private final DocumentService documentService;
  private final McpEnablementService enablement;
  private final AuditService auditService;
  private final McpMetrics metrics;
  private final ObjectMapper objectMapper;

  public CorrespondenceWriteTools(
      CorrespondenceService correspondenceService,
      DocumentService documentService,
      McpEnablementService enablement,
      AuditService auditService,
      McpMetrics metrics,
      ObjectMapper objectMapper) {
    this.correspondenceService = correspondenceService;
    this.documentService = documentService;
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
          var result = fileInboundOrError(cmd, actor, startNanos);
          if (result == null) {
            return McpToolErrors.asResult(
                McpError.invalidRequest("Provide at least one of matterId or customerId."),
                objectMapper);
          }
          emitWriteAudit(result.correspondenceId(), result.idempotent());
          metrics.recordOk("file_correspondence", McpToolAudit.elapsed(startNanos));
          return new FileCorrespondenceToolResponse(result.correspondenceId(), result.idempotent());
        });
  }

  /**
   * Files the inbound correspondence. The try/catch wraps <b>only</b> the service call so a
   * downstream audit-emission failure can never be miscounted as a client {@code invalid_request}
   * error. Returns {@code null} (and records the error metric) when linkage is invalid (both ids
   * null).
   */
  private FileCorrespondenceResult fileInboundOrError(
      FileCorrespondenceCommand cmd, ActorContext actor, long startNanos) {
    try {
      return correspondenceService.fileInbound(cmd, actor);
    } catch (InvalidStateException e) {
      metrics.recordError("file_correspondence", McpToolAudit.elapsed(startNanos));
      return null;
    }
  }

  private void emitWriteAudit(UUID correspondenceId, boolean idempotent) {
    String eventType =
        idempotent ? "mcp.write.correspondence_refiled" : "mcp.write.correspondence_filed";
    try {
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
    } catch (RuntimeException e) {
      // Audit emission must never break a successful tool call.
    }
  }

  /**
   * Attach a document to an inbound correspondence via the presigned-upload handshake (Phase 81,
   * 583A). Two phases:
   *
   * <ul>
   *   <li>{@code INITIATE} — looks up the correspondence to resolve its scope (CUSTOMER preferred
   *       when both ids are present, else PROJECT), reserves a PENDING {@link Document}, and
   *       returns a short-lived presigned PUT URL. The caller PUTs the bytes to that URL.
   *   <li>{@code CONFIRM} — flips the document to UPLOADED and stamps {@code correspondence_id} +
   *       {@code source=EMAIL_INGEST} atomically (see {@link
   *       DocumentService#confirmAndStampCorrespondence}). Idempotent on {@code documentId}.
   * </ul>
   *
   * Carries the same {@code MCP_WRITE} write-guard preamble as {@code file_correspondence}.
   */
  @McpTool(
      name = "attach_document",
      description =
          "Attach a document to an inbound correspondence using a two-phase presigned upload."
              + " Call with phase=INITIATE (correspondenceId + fileName) to get a presigned PUT URL"
              + " and a documentId, PUT the bytes to that URL, then call again with phase=CONFIRM"
              + " (documentId + correspondenceId) to finalise. The document is stamped with the"
              + " correspondence link and source=EMAIL_INGEST. CONFIRM is idempotent on documentId."
              + " Requires the MCP_WRITE capability.")
  public Object attachDocument(
      @McpToolParam(description = "Upload phase: INITIATE or CONFIRM.") String phase,
      @McpToolParam(description = "Correspondence id to attach the document to.")
          UUID correspondenceId,
      @McpToolParam(required = false, description = "File name (INITIATE only).") String fileName,
      @McpToolParam(required = false, description = "MIME content type (INITIATE only).")
          String contentType,
      @McpToolParam(required = false, description = "File size in bytes (INITIATE only).")
          Long size,
      @McpToolParam(required = false, description = "Document id to confirm (CONFIRM only).")
          UUID documentId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        "MCP_WRITE",
        "attach_document",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          AttachPhase parsed = AttachPhase.parse(phase);
          if (parsed == null) {
            metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
            return McpToolErrors.asResult(
                McpError.invalidRequest("phase must be one of: INITIATE, CONFIRM."), objectMapper);
          }
          var actor = ActorContext.fromRequestScopes();
          return switch (parsed) {
            case INITIATE ->
                initiate(correspondenceId, fileName, contentType, size, actor, startNanos);
            case CONFIRM -> confirm(documentId, correspondenceId, actor, startNanos);
          };
        });
  }

  private Object initiate(
      UUID correspondenceId,
      String fileName,
      String contentType,
      Long size,
      ActorContext actor,
      long startNanos) {
    if (correspondenceId == null || fileName == null || fileName.isBlank()) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(
          McpError.invalidRequest("INITIATE requires correspondenceId and fileName."),
          objectMapper);
    }
    if (size == null || size <= 0) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(
          McpError.invalidRequest("INITIATE requires a positive size (bytes)."), objectMapper);
    }
    CorrespondenceScope scope;
    try {
      scope = correspondenceService.requireScopeById(correspondenceId);
    } catch (ResourceNotFoundException e) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
    }
    long sizeBytes = size;
    DocumentService.UploadInitResult result;
    try {
      // Scope = CUSTOMER (preferred when known) else PROJECT. The 581A linkage CHECK guarantees at
      // least one of customerId / projectId is non-null.
      if (scope.customerId() != null) {
        result =
            documentService.initiateCustomerUpload(
                scope.customerId(), fileName, contentType, sizeBytes);
      } else {
        result =
            documentService.initiateUpload(
                scope.projectId(), fileName, contentType, sizeBytes, actor);
      }
    } catch (ResourceNotFoundException e) {
      // The customer/project resolved off the correspondence no longer exists in-tenant.
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("scope"), objectMapper);
    } catch (InvalidStateException e) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(
          McpError.invalidRequest("Document upload could not be initiated."), objectMapper);
    }
    metrics.recordOk("attach_document", McpToolAudit.elapsed(startNanos));
    return new AttachDocumentInitResponse(
        result.documentId(), result.presignedUrl(), result.expiresInSeconds());
  }

  private Object confirm(
      UUID documentId, UUID correspondenceId, ActorContext actor, long startNanos) {
    if (documentId == null || correspondenceId == null) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(
          McpError.invalidRequest("CONFIRM requires documentId and correspondenceId."),
          objectMapper);
    }
    // Validate the correspondence exists IN-TENANT before stamping. A caller-supplied (possibly
    // fabricated or wrong-tenant) correspondenceId must never be stamped onto a document — the
    // lookup throws ResourceNotFoundException for an unknown id, which we surface as notFound.
    try {
      correspondenceService.requireScopeById(correspondenceId);
    } catch (ResourceNotFoundException e) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
    }
    Document document;
    try {
      document = documentService.confirmAndStampCorrespondence(documentId, correspondenceId, actor);
    } catch (ResourceNotFoundException e) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("document"), objectMapper);
    } catch (InvalidStateException e) {
      metrics.recordError("attach_document", McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(
          McpError.invalidRequest("Document could not be confirmed."), objectMapper);
    }
    emitDocumentAttachedAudit(document.getId(), correspondenceId, document.getFileName());
    metrics.recordOk("attach_document", McpToolAudit.elapsed(startNanos));
    return new AttachDocumentConfirmResponse(
        document.getId(), document.getStatus().name(), correspondenceId);
  }

  private void emitDocumentAttachedAudit(UUID documentId, UUID correspondenceId, String fileName) {
    try {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("mcp.write.document_attached")
              .entityType("document")
              .entityId(documentId)
              .details(
                  McpAuditMetadata.builder()
                      .rowCount(1)
                      .entityRef(documentId)
                      .entityRef(correspondenceId)
                      // fileName is best-effort: the POPIA sanitiser drops non-safe-charset
                      // tokens (spaces, ':', '@'), so a real filename may be absent here.
                      .param("fileName", fileName)
                      .build()
                      .toDetails("attach_document"))
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break a successful tool call.
    }
  }

  /** The {@code attach_document} upload phase. */
  private enum AttachPhase {
    INITIATE,
    CONFIRM;

    static AttachPhase parse(String raw) {
      if (raw == null) {
        return null;
      }
      try {
        return AttachPhase.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }
}
