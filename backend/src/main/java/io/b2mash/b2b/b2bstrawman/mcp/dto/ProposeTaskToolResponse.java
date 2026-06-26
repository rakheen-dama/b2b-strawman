package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.UUID;

/**
 * Response of the {@code propose_task} MCP write tool (Epic 585, ADR-322). {@code propose_task}
 * NEVER creates a Task directly — it creates a PENDING approval gate that an authorised member must
 * approve in Kazi (AI_REVIEW) before the task is created.
 *
 * <p>{@code gateId} is the id of the created (or, when {@code duplicate} is {@code true}, the
 * existing) gate. {@code status} is always {@code "PENDING"} on success. {@code duplicate} is
 * {@code true} when an open gate for this correspondence already existed and the call returned it
 * instead of creating a second one (the v1 open-gate dedupe guard). {@code message} is a
 * human-readable explanation for the caller.
 *
 * <p>Tool DTOs live in {@code mcp/dto/} — kept separate from the domain DTOs.
 */
public record ProposeTaskToolResponse(
    UUID gateId, String status, boolean duplicate, String message) {}
