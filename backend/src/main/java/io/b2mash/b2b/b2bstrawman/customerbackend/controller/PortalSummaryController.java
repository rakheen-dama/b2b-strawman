package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal summary endpoint. Returns time/billing rollup for a project linked to the authenticated
 * customer. All endpoints are read-only and require a valid portal JWT (enforced by
 * CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/projects/{projectId}/summary")
public class PortalSummaryController {

  private final PortalReadModelService portalReadModelService;

  public PortalSummaryController(PortalReadModelService portalReadModelService) {
    this.portalReadModelService = portalReadModelService;
  }

  /**
   * Returns the project summary (time/billing rollup). Returns zeroed stub if no summary row
   * exists. Returns 404 if the project is not linked to the customer.
   */
  @GetMapping
  public ResponseEntity<PortalSummaryResponse> getProjectSummary(@PathVariable UUID projectId) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var summary = portalReadModelService.getProjectSummary(projectId, customerId, orgId);

    var response =
        summary
            .map(
                s ->
                    new PortalSummaryResponse(
                        projectId, s.totalHours(), s.billableHours(), s.lastActivityAt()))
            .orElse(new PortalSummaryResponse(projectId, BigDecimal.ZERO, BigDecimal.ZERO, null));

    return ResponseEntity.ok(response);
  }

  public record PortalSummaryResponse(
      UUID projectId, BigDecimal totalHours, BigDecimal billableHours, Instant lastActivityAt) {}
}
