package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.List;
import java.util.UUID;

/** Team workload breakdown for a single member, with per-project hours. */
public record TeamWorkloadEntry(
    UUID memberId,
    String memberName,
    double totalHours,
    double billableHours,
    List<ProjectHoursEntry> projects) {}
