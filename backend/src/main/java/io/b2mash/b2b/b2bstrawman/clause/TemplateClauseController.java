package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseDetail;
import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseRequest.AddClauseToTemplateRequest;
import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseRequest.SetTemplateClausesRequest;
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
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing clause associations on document templates. */
@RestController
@RequestMapping("/api/templates/{templateId}/clauses")
public class TemplateClauseController {

  private final TemplateClauseService templateClauseService;

  public TemplateClauseController(TemplateClauseService templateClauseService) {
    this.templateClauseService = templateClauseService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TemplateClauseDetail>> getTemplateClauses(
      @PathVariable UUID templateId) {
    return ResponseEntity.ok(templateClauseService.getTemplateClauses(templateId));
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TemplateClauseDetail>> setTemplateClauses(
      @PathVariable UUID templateId, @Valid @RequestBody SetTemplateClausesRequest request) {
    return ResponseEntity.ok(
        templateClauseService.setTemplateClauses(templateId, request.clauses()));
  }

  /**
   * Deprecated: Clause associations are now synced from document JSON on template save. See
   * ADR-123. Changes made through this endpoint will be overwritten on next template save.
   */
  @Deprecated(forRemoval = true)
  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateClauseDetail> addClauseToTemplate(
      @PathVariable UUID templateId, @Valid @RequestBody AddClauseToTemplateRequest request) {
    var detail =
        templateClauseService.addClauseToTemplate(
            templateId, request.clauseId(), request.required());
    return ResponseEntity.created(
            URI.create("/api/templates/" + templateId + "/clauses/" + detail.clauseId()))
        .body(detail);
  }

  /**
   * Deprecated: Clause associations are now synced from document JSON on template save. See
   * ADR-123. Changes made through this endpoint will be overwritten on next template save.
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{clauseId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> removeClauseFromTemplate(
      @PathVariable UUID templateId, @PathVariable UUID clauseId) {
    templateClauseService.removeClauseFromTemplate(templateId, clauseId);
    return ResponseEntity.noContent().build();
  }
}
