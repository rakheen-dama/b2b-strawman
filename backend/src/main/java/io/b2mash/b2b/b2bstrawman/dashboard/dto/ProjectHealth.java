package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import io.b2mash.b2b.b2bstrawman.dashboard.HealthStatus;
import java.util.List;
import java.util.UUID;

/**
 * Summary item for the company-level project health list. Includes project identity, health status,
 * task progress, and time metrics.
 */
public record ProjectHealth(
    UUID projectId,
    String projectName,
    String customerName,
    HealthStatus healthStatus,
    List<String> healthReasons,
    int tasksDone,
    int tasksTotal,
    double completionPercent,
    Double budgetConsumedPercent,
    double hoursLogged) {}
