package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.ModuleStatusResponse;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stub controller for the court calendar module. */
@RestController
@RequestMapping("/api/court-calendar")
public class CourtCalendarController {

  private final VerticalModuleGuard moduleGuard;

  public CourtCalendarController(VerticalModuleGuard moduleGuard) {
    this.moduleGuard = moduleGuard;
  }

  @GetMapping("/status")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<ModuleStatusResponse> getStatus() {
    moduleGuard.requireModule("court_calendar");
    return ResponseEntity.ok(
        new ModuleStatusResponse(
            "court_calendar",
            "stub",
            "Court Calendar is not yet implemented. It will be available in a future release."));
  }
}
