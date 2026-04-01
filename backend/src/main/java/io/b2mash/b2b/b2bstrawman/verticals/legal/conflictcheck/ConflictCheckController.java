package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ConflictCheckResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.PerformConflictCheckRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conflict-checks")
public class ConflictCheckController {

  private final ConflictCheckService conflictCheckService;

  public ConflictCheckController(ConflictCheckService conflictCheckService) {
    this.conflictCheckService = conflictCheckService;
  }

  @PostMapping
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<ConflictCheckResponse> performCheck(
      @Valid @RequestBody PerformConflictCheckRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(conflictCheckService.performCheck(request, RequestScopes.requireMemberId()));
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<ConflictCheckResponse>> list(
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String checkType,
      @RequestParam(required = false) UUID checkedBy,
      @RequestParam(required = false) Instant dateFrom,
      @RequestParam(required = false) Instant dateTo,
      Pageable pageable) {
    return ResponseEntity.ok(
        conflictCheckService.list(result, checkType, checkedBy, dateFrom, dateTo, pageable));
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<ConflictCheckResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(conflictCheckService.getById(id));
  }

  @PostMapping("/{id}/resolve")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<ConflictCheckResponse> resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
    return ResponseEntity.ok(
        conflictCheckService.resolve(id, request, RequestScopes.requireMemberId()));
  }
}
