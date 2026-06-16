package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact {@code list_matters} row (§11.4). Projected from the firm-side {@code ProjectResponse}.
 *
 * <p>DRIFT note (vs §11.4): the spec sketches {@code customerName}/{@code startDate}, but {@code
 * Project}/{@code ProjectResponse} expose neither — only {@code customerId} (no name) and {@code
 * createdAt}/{@code dueDate} (no startDate). We deliberately carry {@code customerId} (resolving
 * names would re-introduce the per-row enrichment we are avoiding for token-efficiency) and use
 * {@code createdAt} as the "started"-ish timestamp. {@code status} is the short enum name.
 *
 * @param id matter (project) id
 * @param name matter name
 * @param status short status enum name (e.g. {@code ACTIVE})
 * @param customerId linked client id, or {@code null} if unlinked
 * @param dueDate due date, or {@code null}
 * @param createdAt creation timestamp
 */
public record McpMatterListItem(
    UUID id, String name, String status, UUID customerId, LocalDate dueDate, Instant createdAt) {}
