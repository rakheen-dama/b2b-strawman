package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.portal.PortalTerminologyService.PortalTerminologyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated portal endpoint returning the firm's terminology namespace (GAP-L-65).
 *
 * <p>The returned {@code namespace} is the vertical-profile id (e.g. {@code "legal-za"}) which the
 * portal feeds to its mirrored {@code <TerminologyProvider verticalProfile={namespace}>}. Routed
 * under {@code /portal/} so the existing {@code CustomerAuthFilter} chain authenticates the call;
 * deviates from the spec's {@code /api/portal/...} suggestion because that prefix is
 * unauthenticated in this codebase.
 */
@RestController
@RequestMapping("/portal/terminology")
public class PortalTerminologyController {

  private final PortalTerminologyService portalTerminologyService;

  public PortalTerminologyController(PortalTerminologyService portalTerminologyService) {
    this.portalTerminologyService = portalTerminologyService;
  }

  @GetMapping
  public ResponseEntity<PortalTerminologyResponse> getTerminology() {
    return ResponseEntity.ok(portalTerminologyService.getTerminology());
  }
}
