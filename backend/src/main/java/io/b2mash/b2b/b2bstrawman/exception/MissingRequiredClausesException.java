package io.b2mash.b2b.b2bstrawman.exception;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a document generation request is missing required clauses. Results in HTTP 422
 * Unprocessable Entity.
 */
public class MissingRequiredClausesException extends ErrorResponseException {

  public MissingRequiredClausesException(List<UUID> missingClauseIds) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ProblemDetailFactory.create(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Missing required clauses",
            "Required clause(s) missing: " + missingClauseIds,
            Map.of("missingClauseIds", missingClauseIds)),
        null);
  }
}
