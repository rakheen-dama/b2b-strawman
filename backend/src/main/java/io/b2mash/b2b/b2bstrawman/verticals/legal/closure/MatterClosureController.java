package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.CloseMatterResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureLogResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReportResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ReopenMatterResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ReopenRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for matter closure (Phase 67, Epic 489B, architecture §67.4.2). Every endpoint is a
 * one-liner delegation to {@link MatterClosureService}; the module guard + override capability
 * check live inside the service.
 */
@RestController
@RequestMapping("/api/matters/{matterId}/closure")
public class MatterClosureController {

  private final MatterClosureService matterClosureService;

  public MatterClosureController(MatterClosureService matterClosureService) {
    this.matterClosureService = matterClosureService;
  }

  @GetMapping("/evaluate")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<ClosureReportResponse> evaluate(@PathVariable UUID matterId) {
    return ResponseEntity.ok(matterClosureService.evaluateForController(matterId));
  }

  @PostMapping("/close")
  @RequiresCapability("CLOSE_MATTER")
  public ResponseEntity<CloseMatterResponse> close(
      @PathVariable UUID matterId, @Valid @RequestBody ClosureRequest request) {
    return ResponseEntity.ok(
        matterClosureService.close(matterId, request, RequestScopes.requireMemberId()));
  }

  @PostMapping("/reopen")
  @RequiresCapability("CLOSE_MATTER")
  public ResponseEntity<ReopenMatterResponse> reopen(
      @PathVariable UUID matterId, @Valid @RequestBody ReopenRequest request) {
    return ResponseEntity.ok(
        matterClosureService.reopen(matterId, request, RequestScopes.requireMemberId()));
  }

  @GetMapping("/log")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<ClosureLogResponse>> log(@PathVariable UUID matterId) {
    return ResponseEntity.ok(matterClosureService.getLog(matterId));
  }
}
