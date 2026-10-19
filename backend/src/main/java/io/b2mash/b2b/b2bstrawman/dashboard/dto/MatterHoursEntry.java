package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.UUID;

/** Per-matter hours breakdown within a team workload entry. */
public record MatterHoursEntry(UUID matterId, String matterName, double hours) {}
