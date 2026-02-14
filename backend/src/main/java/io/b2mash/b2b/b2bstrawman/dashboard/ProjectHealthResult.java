package io.b2mash.b2b.b2bstrawman.dashboard;

import java.util.List;

/**
 * Result of the project health calculation.
 *
 * @param status the overall health status (worst across all fired rules)
 * @param reasons human-readable list of reasons explaining the status
 */
public record ProjectHealthResult(HealthStatus status, List<String> reasons) {}
