package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.util.UUID;

/** Response returned by {@code POST /api/matters/{projectId}/closure/reopen}. */
public record ReopenMatterResponse(
    UUID projectId, String status, Instant reopenedAt, UUID closureLogId) {}
