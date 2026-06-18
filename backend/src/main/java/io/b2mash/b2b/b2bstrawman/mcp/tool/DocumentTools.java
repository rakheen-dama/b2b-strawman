package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpDocumentDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpDownloadUrl;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only MCP tools over the firm's documents: {@code search_documents} (metadata) and {@code
 * get_document_url} (a short-lived presigned download URL) (Epic 563A, §11.4).
 *
 * <p><b>Never inlines document bytes.</b> {@code search_documents} returns metadata only; bytes are
 * fetched out-of-band via the presigned URL from {@code get_document_url}.
 *
 * <p>Access model is scope-dependent. A {@code projectId} search is <b>project-access</b> gated
 * (the service calls {@code requireViewAccess}); an {@code ORG}/{@code CUSTOMER} scope search is
 * org-wide (relying on tenant schema isolation). {@code get_document_url} enforces access inside
 * the service. Service-thrown {@link ResourceNotFoundException}/{@link InvalidStateException} are
 * caught here and surfaced as non-leaking {@link McpError}s.
 */
@Component
public class DocumentTools {

  private static final String GATE_PROJECT_ACCESS = "project-access";

  private final DocumentService documentService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public DocumentTools(
      DocumentService documentService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.documentService = documentService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "search_documents",
      description =
          "List document metadata (never the bytes). Provide projectId to search one matter"
              + " (project-access enforced), or scope=ORG / scope=CUSTOMER for org-wide search"
              + " (customerId is required when scope=CUSTOMER). Paginated — page size capped at 50.")
  public Object searchDocuments(
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          int size,
      @McpToolParam(
              required = false,
              description = "Search documents within this matter (project).")
          UUID projectId,
      @McpToolParam(
              required = false,
              description = "Org-wide scope: ORG or CUSTOMER. Ignored when projectId is given.")
          String scope,
      @McpToolParam(required = false, description = "Required when scope=CUSTOMER.")
          UUID customerId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    try {
      List<McpDocumentDto> docs;
      if (projectId != null) {
        docs =
            documentService.listDocumentResponses(projectId, actor).stream()
                .map(McpDocumentDto::from)
                .toList();
      } else if (scope != null) {
        docs =
            documentService.listDocumentsByScope(scope, customerId).stream()
                .map(McpDocumentDto::from)
                .toList();
      } else {
        return McpToolErrors.asResult(
            McpError.invalidRequest("Provide either projectId or scope (ORG/CUSTOMER)."),
            objectMapper);
      }
      // Guard against an unbounded materialised result set (org/customer scope can be large): fail
      // with a structured error rather than ever emitting a truncated page when the full list
      // exceeds the per-call ceiling.
      if (McpPagination.exceedsResponseCeiling(docs.size())) {
        return McpToolErrors.asResult(McpError.responseTooLarge(), objectMapper);
      }
      McpPage<McpDocumentDto> pageResult =
          McpPagination.paginate(docs, page, size, McpPagination.DEFAULT_MAX_SIZE);
      var meta =
          McpAuditMetadata.builder()
              .rowCount(pageResult.items().size())
              .entityRef(projectId)
              .entityRef(customerId)
              .param("scope", projectId != null ? null : scope)
              .build();
      emitInvoked("search_documents", meta, startNanos);
      return pageResult;
    } catch (ResourceNotFoundException e) {
      // Generic resource noun: search_documents serves project, org, and customer scopes, so a
      // matter-specific message would mislead on the org/customer paths. A projectId search that
      // 404s is a project-access denial — emit mcp.access.denied for that case only.
      if (projectId != null) {
        McpToolAudit.emitDenied(
            "search_documents",
            GATE_PROJECT_ACCESS,
            auditService,
            metrics,
            McpToolAudit.elapsed(startNanos));
      }
      return McpToolErrors.asResult(McpError.notFound("document"), objectMapper);
    } catch (InvalidStateException e) {
      return McpToolErrors.asResult(
          McpError.invalidRequest(
              "Invalid scope. Use scope=ORG, or scope=CUSTOMER with a customerId."),
          objectMapper);
    }
  }

  @McpTool(
      name = "get_document_url",
      description =
          "Return a short-lived presigned download URL (plus its expiry in seconds) for one"
              + " document. Never returns the document bytes. Returns a non-leaking not-found error"
              + " if the document does not exist or you cannot access it.")
  public Object getDocumentUrl(@McpToolParam(description = "Document id.") UUID documentId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    try {
      var result = documentService.getPresignedDownloadUrl(documentId, actor);
      var dto = new McpDownloadUrl(result.url(), result.expiresInSeconds());
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(documentId).build();
      emitInvoked("get_document_url", meta, startNanos);
      return dto;
    } catch (ResourceNotFoundException e) {
      // project-access denial (or genuinely absent) — non-leaking not_found, but audit the refusal.
      McpToolAudit.emitDenied(
          "get_document_url",
          GATE_PROJECT_ACCESS,
          auditService,
          metrics,
          McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("document"), objectMapper);
    } catch (InvalidStateException e) {
      return McpToolErrors.asResult(McpError.notFound("document"), objectMapper);
    }
  }

  private void emitInvoked(String tool, McpAuditMetadata meta, long startNanos) {
    McpToolAudit.emitInvoked(tool, meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
  }
}
