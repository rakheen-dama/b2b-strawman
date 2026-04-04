package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.ModuleStatusResponse;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stub controller for the trust accounting module. */
@RestController
@RequestMapping("/api/trust-accounting")
public class TrustAccountingController {

  private final VerticalModuleGuard moduleGuard;

  public TrustAccountingController(VerticalModuleGuard moduleGuard) {
    this.moduleGuard = moduleGuard;
  }

  @GetMapping("/status")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<ModuleStatusResponse> getStatus() {
    moduleGuard.requireModule("trust_accounting");
    return ResponseEntity.ok(
        new ModuleStatusResponse(
            "trust_accounting",
            "stub",
            "Trust Accounting is not yet implemented. It will be available in a future release."));
  }
}
