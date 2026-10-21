package io.b2mash.b2b.b2bstrawman.customer;

import java.util.Map;
import java.util.UUID;

/**
 * Parsed {@code GET /api/clients} list parameters (Phase 87, Epic 615B, arch §12.3.4/§12.4). {@code
 * allParams} carries the {@code customField[slug]=…} and {@code tags=…} filters.
 *
 * <p>All filtering happens in {@code CustomerService.listClientRows}, never in the controller.
 *
 * @param ficaStatus {@code OUTSTANDING} (NOT_STARTED or IN_PROGRESS) or {@code DONE}, case
 *     insensitive; null/blank = no filter, anything else is a 400
 * @param hasTrustBalance keep only clients with a client-level trust balance &gt; 0 (no rows when
 *     the caller cannot see trust)
 * @param showIncomplete keep only clients whose completeness is below 100%
 */
public record ClientListQuery(
    UUID view,
    LifecycleStatus lifecycleStatus,
    String ficaStatus,
    boolean hasTrustBalance,
    boolean showIncomplete,
    Map<String, String> allParams) {}
