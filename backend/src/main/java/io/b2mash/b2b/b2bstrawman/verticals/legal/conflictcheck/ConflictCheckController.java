package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.ModuleStatusResponse;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stub controller for the conflict check module. */
@RestController
@RequestMapping("/api/conflict-check")
public class ConflictCheckController {

  private final VerticalModuleGuard moduleGuard;

  public ConflictCheckController(VerticalModuleGuard moduleGuard) {
    this.moduleGuard = moduleGuard;
  }

  @GetMapping("/status")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<ModuleStatusResponse> getStatus() {
    moduleGuard.requireModule("conflict_check");
    return ResponseEntity.ok(
        new ModuleStatusResponse(
            "conflict_check",
            "stub",
            "Conflict Check is not yet implemented. It will be available in a future release."));
  }
}
