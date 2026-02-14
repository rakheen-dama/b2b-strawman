package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.UUID;

/** Per-project hour breakdown for the personal dashboard. */
public record ProjectBreakdownEntry(
    UUID projectId, String projectName, double hours, double percent) {}
