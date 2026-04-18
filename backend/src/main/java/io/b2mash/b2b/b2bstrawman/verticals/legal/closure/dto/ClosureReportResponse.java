package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response for {@code GET /api/matters/{projectId}/closure/evaluate}. */
public record ClosureReportResponse(
    UUID projectId, Instant evaluatedAt, boolean allPassed, List<GateReportItem> gates) {}
