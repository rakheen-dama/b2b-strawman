package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.UUID;

/** Per-matter hour breakdown for the personal dashboard. */
public record MatterBreakdownEntry(
    UUID matterId, String matterName, double hours, double percent) {}
