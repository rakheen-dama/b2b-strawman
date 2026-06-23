package io.b2mash.b2b.b2bstrawman.crm;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input filter for {@link PipelineSummaryService#getSummary(SummaryFilter)} (Epic 578A).
 *
 * @param from start of the win-rate / days-to-close window (inclusive); defaults to trailing 90
 *     days when null
 * @param to end of the window (inclusive); defaults to today when null
 * @param ownerId optional deal-owner filter; when null the summary aggregates all owners
 */
public record SummaryFilter(LocalDate from, LocalDate to, UUID ownerId) {}
