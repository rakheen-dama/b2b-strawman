package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.util.Map;

/**
 * Serialisable shape of a single gate result for REST responses and the {@code gate_report} JSONB
 * column on {@link io.b2mash.b2b.b2bstrawman.verticals.legal.closure.MatterClosureLog}.
 */
public record GateReportItem(
    int order, String code, boolean passed, String message, Map<String, Object> detail) {}
