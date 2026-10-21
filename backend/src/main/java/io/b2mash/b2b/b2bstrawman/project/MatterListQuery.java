package io.b2mash.b2b.b2bstrawman.project;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Parsed {@code GET /api/matters} list parameters (Phase 87, Epic 615A, arch §12.3.4). {@code
 * customerId} keeps the legacy query-param name the frontend and regression script send today.
 * {@code allParams} carries the {@code customField[slug]=…} and {@code tags=…} filters.
 *
 * <p>All filtering happens in {@code ProjectService.listMatterRows}, never in the controller:
 * {@code inactiveSince} is a SQL predicate; {@code mine}, {@code hasUnbilled} and the
 * custom-field/tag params filter the loaded rows in Java.
 *
 * @param mine keep only matters the caller has a {@code project_members} row on
 * @param hasUnbilled keep only matters with unbilled billable minutes &gt; 0
 * @param inactiveSince keep only matters with no activity on or after this date (null = no filter)
 */
public record MatterListQuery(
    UUID view,
    String status,
    LocalDate dueBefore,
    UUID customerId,
    boolean mine,
    boolean hasUnbilled,
    LocalDate inactiveSince,
    Map<String, String> allParams) {}
