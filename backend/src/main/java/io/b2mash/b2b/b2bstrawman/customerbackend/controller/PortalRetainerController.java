package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalRetainerConsumptionEntryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalRetainerSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalRetainerService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal retainer usage endpoints (Epic 496A). All routes require a valid portal JWT (enforced by
 * {@code CustomerAuthFilter} on {@code /portal/*}) and are module-gated inside {@link
 * PortalRetainerService} — if the authenticated customer's tenant does not have {@code
 * retainer_agreements} enabled, every endpoint returns 404 (ADR-254: portal surfaces hide disabled
 * modules).
 *
 * <p>Mirrors the shape of {@link PortalTrustController}: thin delegate to the service layer, no
 * business logic in the controller.
 */
@RestController
@RequestMapping("/portal/retainers")
public class PortalRetainerController {

  private final PortalRetainerService portalRetainerService;

  public PortalRetainerController(PortalRetainerService portalRetainerService) {
    this.portalRetainerService = portalRetainerService;
  }

  /**
   * Returns every retainer usage summary visible to the authenticated portal contact's customer.
   */
  @GetMapping
  public ResponseEntity<List<PortalRetainerSummaryResponse>> listRetainers() {
    return ResponseEntity.ok(portalRetainerService.listForContact());
  }

  /**
   * Returns consumption entries for a specific retainer owned by the authenticated portal contact's
   * customer. Both {@code from} and {@code to} are optional ISO-8601 date parameters — null bounds
   * are treated as open-ended by the repository.
   */
  @GetMapping("/{id}/consumption")
  public ResponseEntity<List<PortalRetainerConsumptionEntryResponse>> listConsumption(
      @PathVariable UUID id,
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return ResponseEntity.ok(portalRetainerService.consumption(id, from, to));
  }
}
