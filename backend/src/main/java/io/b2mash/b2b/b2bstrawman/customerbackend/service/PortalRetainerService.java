package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalRetainerConsumptionEntryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalRetainerSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerConsumptionEntryRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerSummaryRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service backing the portal retainer usage endpoints (Epic 496A). Encapsulates the module gate,
 * read-model queries, and response-record mapping — controllers stay thin per CLAUDE.md.
 *
 * <p>Per ADR-254 ("portal surfaces hide disabled modules") a disabled {@code retainer_agreements}
 * module surfaces as 404 (not 403) so a disabled vertical feature is indistinguishable from missing
 * data.
 */
@Service
public class PortalRetainerService {

  private static final String MODULE_ID = "retainer_agreements";

  private final PortalRetainerSummaryRepository summaryRepo;
  private final PortalRetainerConsumptionEntryRepository entryRepo;
  private final VerticalModuleGuard moduleGuard;

  public PortalRetainerService(
      PortalRetainerSummaryRepository summaryRepo,
      PortalRetainerConsumptionEntryRepository entryRepo,
      VerticalModuleGuard moduleGuard) {
    this.summaryRepo = summaryRepo;
    this.entryRepo = entryRepo;
    this.moduleGuard = moduleGuard;
  }

  /**
   * Returns every retainer usage summary visible to the authenticated portal contact's customer.
   * Resolves the customer via {@link RequestScopes#requireCustomerId()} — the portal auth filter
   * binds this value on every {@code /portal/*} request.
   */
  public List<PortalRetainerSummaryResponse> listForContact() {
    requireRetainerAgreementsEnabled();
    UUID customerId = RequestScopes.requireCustomerId();
    return summaryRepo.findByCustomerId(customerId).stream()
        .map(
            v ->
                new PortalRetainerSummaryResponse(
                    v.id(),
                    v.name(),
                    v.periodType(),
                    v.hoursAllotted(),
                    v.hoursConsumed(),
                    v.hoursRemaining(),
                    v.periodStart(),
                    v.periodEnd(),
                    v.rolloverHours(),
                    v.nextRenewalDate(),
                    v.status()))
        .toList();
  }

  /**
   * Returns the consumption entries for a specific retainer owned by the authenticated portal
   * contact's customer. Enforces customer-scoping at the repo layer (the repo query joins on {@code
   * customer_id}) so a contact cannot read another tenant's rows in the shared portal schema.
   * Returns 404 when the retainer is not visible to the caller.
   */
  public List<PortalRetainerConsumptionEntryResponse> consumption(
      UUID retainerId, LocalDate from, LocalDate to) {
    requireRetainerAgreementsEnabled();
    UUID customerId = RequestScopes.requireCustomerId();
    requireRetainerVisibleToCustomer(customerId, retainerId);

    return entryRepo.findByRetainerIdAndEntryDateRange(customerId, retainerId, from, to).stream()
        .map(
            v ->
                new PortalRetainerConsumptionEntryResponse(
                    v.id(), v.occurredAt(), v.hours(), v.description(), v.memberDisplayName()))
        .toList();
  }

  // ── Gates ─────────────────────────────────────────────────────────────

  private void requireRetainerAgreementsEnabled() {
    if (!moduleGuard.isModuleEnabled(MODULE_ID)) {
      // 404 (not 403) per the portal's "module disabled looks like no resource" contract.
      throw ResourceNotFoundException.withDetail(
          "Retainers not available", "Retainer usage is not available for this organization");
    }
  }

  private void requireRetainerVisibleToCustomer(UUID customerId, UUID retainerId) {
    summaryRepo
        .findByCustomerIdAndRetainerId(customerId, retainerId)
        .orElseThrow(() -> new ResourceNotFoundException("Retainer", retainerId));
  }
}
