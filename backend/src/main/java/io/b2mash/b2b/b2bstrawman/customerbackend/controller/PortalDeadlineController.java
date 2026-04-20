package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalDeadlineResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalDeadlineService;
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
 * Portal deadline endpoints (Epic 497A). All routes require a valid portal JWT (enforced by {@code
 * CustomerAuthFilter} on {@code /portal/*}) and are module-gated inside {@link
 * PortalDeadlineService} — if the authenticated customer's tenant does not have {@code deadlines}
 * enabled, every endpoint returns 404 (ADR-254: portal surfaces hide disabled modules).
 *
 * <p>Mirrors the shape of {@code PortalRetainerController}: thin delegate to a single service
 * method per endpoint, no business logic in the controller.
 */
@RestController
@RequestMapping("/portal/deadlines")
public class PortalDeadlineController {

  private final PortalDeadlineService portalDeadlineService;

  public PortalDeadlineController(PortalDeadlineService portalDeadlineService) {
    this.portalDeadlineService = portalDeadlineService;
  }

  /**
   * Lists deadlines visible to the authenticated portal contact's customer. Defaults to {@code
   * today..today+60d} when {@code from}/{@code to} are omitted (computed by the service).
   */
  @GetMapping
  public ResponseEntity<List<PortalDeadlineResponse>> list(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(value = "status", required = false) String status) {
    return ResponseEntity.ok(portalDeadlineService.listForContact(from, to, status));
  }

  /**
   * Returns a single deadline identified by its source entity + id. Customer scoping is enforced by
   * the repository so that a contact cannot read another tenant's rows in the shared portal schema.
   */
  @GetMapping("/{sourceEntity}/{id}")
  public ResponseEntity<PortalDeadlineResponse> get(
      @PathVariable String sourceEntity, @PathVariable UUID id) {
    return ResponseEntity.ok(portalDeadlineService.getForContact(sourceEntity, id));
  }
}
