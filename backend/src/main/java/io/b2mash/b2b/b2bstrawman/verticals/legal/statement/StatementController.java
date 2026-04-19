package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for Statement of Account generation (Phase 67, Epic 491A, architecture §67.4.3 /
 * ADR-250). Every endpoint is a one-liner delegating to {@link StatementService}; module guard,
 * context build, rendering, audit, and event publish all live in the service.
 *
 * <p>Co-gated under the {@code disbursements} module — the service throws {@code
 * ModuleNotEnabledException} (-> 403 ProblemDetail) if the tenant does not have it enabled.
 */
@RestController
@RequestMapping("/api/matters/{projectId}/statements")
public class StatementController {

  private final StatementService statementService;

  public StatementController(StatementService statementService) {
    this.statementService = statementService;
  }

  @PostMapping
  @RequiresCapability("GENERATE_STATEMENT_OF_ACCOUNT")
  public ResponseEntity<StatementResponse> generate(
      @PathVariable UUID projectId, @Valid @RequestBody GenerateStatementRequest request) {
    var response = statementService.generate(projectId, request, RequestScopes.requireMemberId());
    return ResponseEntity.created(
            URI.create("/api/matters/" + projectId + "/statements/" + response.id()))
        .body(response);
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<StatementResponse>> list(
      @PathVariable UUID projectId, Pageable pageable) {
    return ResponseEntity.ok(statementService.list(projectId, pageable));
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<StatementResponse> getById(
      @PathVariable UUID projectId, @PathVariable UUID id) {
    return ResponseEntity.ok(statementService.getById(projectId, id));
  }
}
