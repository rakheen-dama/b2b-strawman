package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.UUID;

/**
 * Response of the {@code file_correspondence} MCP write tool. {@code idempotent} is {@code false}
 * when a new {@code Correspondence} row was created, {@code true} when an existing row keyed on
 * {@code messageId} was re-filed (a no-op).
 *
 * <p>Tool DTOs live in {@code mcp/dto/} — kept separate from the domain DTOs in {@code
 * correspondence/dto/}.
 */
public record FileCorrespondenceToolResponse(UUID correspondenceId, boolean idempotent) {}
