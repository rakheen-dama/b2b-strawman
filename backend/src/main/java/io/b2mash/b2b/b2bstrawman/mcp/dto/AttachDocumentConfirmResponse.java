package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.UUID;

/**
 * CONFIRM response of the {@code attach_document} MCP write tool: the document is now {@code
 * UPLOADED} and stamped with its correspondence link plus {@code source=EMAIL_INGEST}.
 *
 * <p>Tool DTOs live in {@code mcp/dto/} — kept separate from the domain DTOs in {@code
 * document/dto/}.
 */
public record AttachDocumentConfirmResponse(
    UUID documentId, String status, UUID correspondenceId) {}
