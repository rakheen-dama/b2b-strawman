package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.document.DocumentController.DocumentResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact {@code search_documents} row (§11.4): {@code {id, name, scope, contentType, sizeBytes,
 * createdAt}}. <b>Metadata only — never bytes.</b> Download bytes are obtained out-of-band via
 * {@code get_document_url} (a presigned URL), never inlined.
 *
 * <p>Mapping: {@code name <- fileName}, {@code sizeBytes <- size}; {@code scope}/{@code
 * contentType}/{@code createdAt} map directly.
 */
public record McpDocumentDto(
    UUID id,
    String name,
    String scope,
    String contentType,
    long sizeBytes,
    UUID projectId,
    UUID customerId,
    Instant createdAt) {

  public static McpDocumentDto from(DocumentResponse doc) {
    return new McpDocumentDto(
        doc.id(),
        doc.fileName(),
        doc.scope(),
        doc.contentType(),
        doc.size(),
        doc.projectId(),
        doc.customerId(),
        doc.createdAt());
  }
}
