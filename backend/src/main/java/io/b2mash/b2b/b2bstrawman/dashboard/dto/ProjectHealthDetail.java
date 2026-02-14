package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import io.b2mash.b2b.b2bstrawman.dashboard.HealthStatus;
import java.util.List;

/**
 * Response DTO for the project health endpoint. Contains the overall health status, human-readable
 * reasons, and the underlying metrics.
 *
 * @param healthStatus the overall health status (UNKNOWN, HEALTHY, AT_RISK, CRITICAL)
 * @param healthReasons human-readable list of reasons explaining the status
 * @param metrics the raw metrics used for health scoring
 */
public record ProjectHealthDetail(
    HealthStatus healthStatus, List<String> healthReasons, ProjectHealthMetrics metrics) {}
