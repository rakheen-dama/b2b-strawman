package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.util.UUID;

/** Response returned by {@code POST /api/matters/{matterId}/closure/reopen}. */
public record ReopenMatterResponse(
    UUID matterId, String status, Instant reopenedAt, UUID closureLogId) {}
