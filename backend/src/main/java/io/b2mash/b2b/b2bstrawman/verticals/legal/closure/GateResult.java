package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import java.util.Map;

/**
 * Result of evaluating a single {@link ClosureGate} (Phase 67, Epic 489A, architecture §67.3.4).
 *
 * @param passed true if the gate is satisfied; false if closure should be blocked on this rule
 * @param code stable gate code (matches {@link ClosureGate#code()})
 * @param message human-readable outcome, interpolated with counts / amounts (e.g. {@code "3
 *     disbursements are unapproved."})
 * @param detail optional structured drill-down (counts, ids, amounts) for the UI; may be {@link
 *     Map#of()} if no detail is needed
 */
public record GateResult(boolean passed, String code, String message, Map<String, Object> detail) {}
