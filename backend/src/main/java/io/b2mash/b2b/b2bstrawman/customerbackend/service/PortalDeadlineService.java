package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalDeadlineResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service backing the portal deadline endpoints (Epic 497A). Owns the module gate, default
 * date-range computation (today..today+60d), read-model queries, and response-record mapping so the
 * controller stays thin per CLAUDE.md controller discipline.
 *
 * <p>Per ADR-254 ("portal surfaces hide disabled modules") a disabled {@code deadlines} module
 * surfaces as 404 (not 403) so a disabled vertical feature is indistinguishable from missing data.
 */
@Service
public class PortalDeadlineService {

  private static final String MODULE_ID = "deadlines";

  /** Default list window when {@code to} is not supplied by the caller. */
  private static final int DEFAULT_LOOKAHEAD_DAYS = 60;

  private static final Set<String> VALID_STATUS_FILTERS =
      Set.of("UPCOMING", "DUE_SOON", "OVERDUE", "COMPLETED", "CANCELLED");

  private final PortalDeadlineViewRepository deadlineRepo;
  private final VerticalModuleGuard moduleGuard;

  public PortalDeadlineService(
      PortalDeadlineViewRepository deadlineRepo, VerticalModuleGuard moduleGuard) {
    this.deadlineRepo = deadlineRepo;
    this.moduleGuard = moduleGuard;
  }

  /**
   * Lists deadlines visible to the authenticated portal contact's customer. Null {@code from} /
   * {@code to} default to {@code today} / {@code today+60d}. Null {@code status} returns every
   * status. A non-null {@code status} that is not one of the constrained values is rejected with
   * 400 to avoid silently returning an empty list.
   */
  public List<PortalDeadlineResponse> listForContact(LocalDate from, LocalDate to, String status) {
    requireDeadlinesEnabled();
    LocalDate today = LocalDate.now();
    LocalDate effectiveFrom = from != null ? from : today;
    LocalDate effectiveTo = to != null ? to : today.plusDays(DEFAULT_LOOKAHEAD_DAYS);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new InvalidStateException("Invalid date range", "from must be on or before to");
    }
    String normalisedStatus = null;
    if (status != null && !status.isBlank()) {
      String upper = status.toUpperCase();
      if (!VALID_STATUS_FILTERS.contains(upper)) {
        throw new InvalidStateException(
            "Invalid status filter",
            "status must be one of UPCOMING, DUE_SOON, OVERDUE, COMPLETED, CANCELLED");
      }
      normalisedStatus = upper;
    }
    UUID customerId = RequestScopes.requireCustomerId();
    return deadlineRepo
        .findByCustomer(customerId, effectiveFrom, effectiveTo, normalisedStatus)
        .stream()
        .map(PortalDeadlineService::toResponse)
        .toList();
  }

  /**
   * Returns a single deadline identified by {@code (sourceEntity, id)}. Customer scoping happens in
   * the repo query — a portal contact for tenant A cannot read a tenant B deadline even by guessing
   * a valid pair, because the shared portal schema is keyed on {@code customer_id}.
   */
  public PortalDeadlineResponse getForContact(String sourceEntity, UUID id) {
    requireDeadlinesEnabled();
    UUID customerId = RequestScopes.requireCustomerId();
    return deadlineRepo
        .findByCustomerIdAndSourceEntityAndId(customerId, sourceEntity, id)
        .map(PortalDeadlineService::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Deadline", id));
  }

  // ── Gates ─────────────────────────────────────────────────────────────

  private void requireDeadlinesEnabled() {
    if (!moduleGuard.isModuleEnabled(MODULE_ID)) {
      throw ResourceNotFoundException.withDetail(
          "Deadlines not available", "Deadline visibility is not available for this organization");
    }
  }

  private static PortalDeadlineResponse toResponse(PortalDeadlineView v) {
    return new PortalDeadlineResponse(
        v.id(),
        v.sourceEntity(),
        v.deadlineType(),
        v.label(),
        v.dueDate(),
        v.status(),
        v.descriptionSanitised(),
        v.matterId());
  }
}
