package io.b2mash.b2b.b2bstrawman.clause.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Request DTO for a single clause entry when setting the full clause list on a template. */
public record TemplateClauseRequest(
    @NotNull(message = "clauseId is required") UUID clauseId, int sortOrder, boolean required) {

  /** Request DTO for atomically replacing the full clause list on a template. */
  public record SetTemplateClausesRequest(
      @NotNull(message = "clauses list is required") @Valid List<TemplateClauseRequest> clauses) {}

  /** Request DTO for adding a single clause to a template. */
  public record AddClauseToTemplateRequest(
      @NotNull(message = "clauseId is required") UUID clauseId, boolean required) {}
}
