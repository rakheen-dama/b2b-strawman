package io.b2mash.b2b.b2bstrawman.portal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal activity timeline endpoint. Surfaces audit events for the authenticated portal contact:
 * either their own actions, firm actions on their matter, or both.
 *
 * <p>Authenticated via portal JWT (CustomerAuthFilter). Customer/contact scoping is enforced by the
 * service layer via RequestScopes.
 */
@RestController
public class PortalActivityController {

  private final PortalActivityService service;

  public PortalActivityController(PortalActivityService service) {
    this.service = service;
  }

  @GetMapping("/portal/activity")
  public ResponseEntity<Page<PortalActivityEventResponse>> list(
      @RequestParam(name = "tab", defaultValue = "ALL") PortalActivityService.Tab tab,
      // Native queries already ORDER BY occurred_at DESC; we deliberately do not pass
      // a Pageable sort because Spring would append it as a JPA-property-name expression
      // (`ae.occurredAt`) which Postgres rejects against the snake_case column.
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(service.listActivity(tab, pageable));
  }
}
