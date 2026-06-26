package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.UUID;

/**
 * INITIATE response of the {@code attach_document} MCP write tool: a presigned PUT URL + the new
 * (PENDING) document id. The caller PUTs the bytes to {@code presignedUrl}, then calls the tool
 * again with {@code phase=CONFIRM} and the returned {@code documentId} to finalise the upload and
 * stamp the correspondence link.
 *
 * <p>Tool DTOs live in {@code mcp/dto/} — kept separate from the domain DTOs in {@code
 * document/dto/}.
 */
public record AttachDocumentInitResponse(
    UUID documentId, String presignedUrl, long expiresInSeconds) {}
