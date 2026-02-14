package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.UUID;

/** Per-project hours breakdown within a team workload entry. */
public record ProjectHoursEntry(UUID projectId, String projectName, double hours) {}
