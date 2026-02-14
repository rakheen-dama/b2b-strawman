package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Upcoming task deadline for the personal dashboard. */
public record UpcomingDeadline(
    UUID taskId, String taskName, String projectName, LocalDate dueDate) {}
