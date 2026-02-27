package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.clause.dto.ClausePreviewRequest;
import io.b2mash.b2b.b2bstrawman.clause.dto.ClauseResponse;
import io.b2mash.b2b.b2bstrawman.clause.dto.CreateClauseRequest;
import io.b2mash.b2b.b2bstrawman.clause.dto.UpdateClauseRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for clause library management. */
@RestController
@RequestMapping("/api/clauses")
public class ClauseController {

  private final ClauseService clauseService;

  public ClauseController(ClauseService clauseService) {
    this.clauseService = clauseService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ClauseResponse>> listClauses(
      @RequestParam(defaultValue = "false") boolean includeInactive,
      @RequestParam(required = false) String category) {
    return ResponseEntity.ok(clauseService.listClauses(includeInactive, category));
  }

  @GetMapping("/categories")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<String>> listCategories() {
    return ResponseEntity.ok(clauseService.listCategories());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ClauseResponse> getClause(@PathVariable UUID id) {
    return ResponseEntity.ok(clauseService.getById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ClauseResponse> createClause(
      @Valid @RequestBody CreateClauseRequest request) {
    var response = clauseService.createClause(request);
    return ResponseEntity.created(URI.create("/api/clauses/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ClauseResponse> updateClause(
      @PathVariable UUID id, @Valid @RequestBody UpdateClauseRequest request) {
    return ResponseEntity.ok(clauseService.updateClause(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteClause(@PathVariable UUID id) {
    clauseService.deleteClause(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ClauseResponse> deactivateClause(@PathVariable UUID id) {
    return ResponseEntity.ok(clauseService.deactivateClause(id));
  }

  @PostMapping("/{id}/clone")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ClauseResponse> cloneClause(@PathVariable UUID id) {
    var response = clauseService.cloneClause(id);
    return ResponseEntity.created(URI.create("/api/clauses/" + response.id())).body(response);
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<String> previewClause(
      @PathVariable UUID id, @Valid @RequestBody ClausePreviewRequest request) {
    return ResponseEntity.ok(
        clauseService.previewClause(id, request.entityId(), request.entityType()));
  }
}
