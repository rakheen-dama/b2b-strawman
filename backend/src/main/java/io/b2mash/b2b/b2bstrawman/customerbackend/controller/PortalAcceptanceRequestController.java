package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal acceptance request endpoint. Returns pending acceptances for the authenticated portal
 * contact. All endpoints are read-only and require a valid portal JWT (enforced by
 * CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/acceptance-requests")
public class PortalAcceptanceRequestController {

  private final PortalReadModelService portalReadModelService;

  public PortalAcceptanceRequestController(PortalReadModelService portalReadModelService) {
    this.portalReadModelService = portalReadModelService;
  }

  /** Returns pending acceptance requests (SENT or VIEWED) for the authenticated contact. */
  @GetMapping("/pending")
  public ResponseEntity<List<PendingAcceptanceResponse>> listPending() {
    UUID portalContactId = RequestScopes.requirePortalContactId();
    var acceptances = portalReadModelService.findPendingAcceptances(portalContactId);

    var response =
        acceptances.stream()
            .map(
                a ->
                    new PendingAcceptanceResponse(
                        a.id(),
                        a.documentTitle(),
                        a.requestToken(),
                        a.sentAt(),
                        a.expiresAt(),
                        a.status()))
            .toList();

    return ResponseEntity.ok(response);
  }

  public record PendingAcceptanceResponse(
      UUID id,
      String documentTitle,
      String requestToken,
      Instant sentAt,
      Instant expiresAt,
      String status) {}
}
