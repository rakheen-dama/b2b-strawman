package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.GateReportItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain-layer aggregate result of running every {@link ClosureGate} against a matter (Phase 67,
 * Epic 489B, architecture §67.3.4). Used by {@code MatterClosureService.evaluate} and stamped into
 * {@code MatterClosureLog.gate_report} at close-time.
 */
public record MatterClosureReport(
    UUID projectId, Instant evaluatedAt, boolean allPassed, List<GateReportItem> gates) {}
