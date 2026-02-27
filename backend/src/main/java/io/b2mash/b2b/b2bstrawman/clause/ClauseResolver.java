package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.ClauseSelection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

/**
 * Resolves clause selections into full Clause entities for document generation. Handles three
 * scenarios: explicit clause selection with validation, fallback to template defaults, and backward
 * compatible empty list when no associations exist.
 */
@Component
public class ClauseResolver {

  private static final Logger log = LoggerFactory.getLogger(ClauseResolver.class);

  private final TemplateClauseRepository templateClauseRepository;
  private final ClauseRepository clauseRepository;

  public ClauseResolver(
      TemplateClauseRepository templateClauseRepository, ClauseRepository clauseRepository) {
    this.templateClauseRepository = templateClauseRepository;
    this.clauseRepository = clauseRepository;
  }

  /**
   * Resolves clause selections for document generation.
   *
   * <p>Resolution rules:
   *
   * <ol>
   *   <li>If {@code clauseSelections} is null: load template defaults from associations, return
   *       clauses in template sort order. Returns empty list if template has no associations.
   *   <li>If {@code clauseSelections} is provided: validate required clauses are present (422 if
   *       missing), validate all IDs exist (400 if invalid), return clauses in caller-specified
   *       sort order.
   * </ol>
   *
   * @param templateId the template ID to resolve clauses for
   * @param clauseSelections nullable list of explicit clause selections
   * @return resolved Clause entities in the correct order, or empty list
   */
  public List<Clause> resolveClauses(UUID templateId, List<ClauseSelection> clauseSelections) {
    if (clauseSelections == null) {
      return loadTemplateDefaults(templateId);
    }
    return resolveExplicitSelections(templateId, clauseSelections);
  }

  private List<Clause> loadTemplateDefaults(UUID templateId) {
    var associations = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
    if (associations.isEmpty()) {
      return List.of();
    }

    var clauseIds = associations.stream().map(TemplateClause::getClauseId).toList();
    Map<UUID, Clause> clauseMap =
        clauseRepository.findAllById(clauseIds).stream()
            .collect(Collectors.toMap(Clause::getId, Function.identity()));

    return associations.stream()
        .map(tc -> clauseMap.get(tc.getClauseId()))
        .filter(c -> c != null)
        .toList();
  }

  private List<Clause> resolveExplicitSelections(
      UUID templateId, List<ClauseSelection> selections) {
    // Validate required clauses are present
    var associations = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
    var requiredClauseIds =
        associations.stream()
            .filter(TemplateClause::isRequired)
            .map(TemplateClause::getClauseId)
            .toList();

    var selectedClauseIds = selections.stream().map(ClauseSelection::clauseId).toList();

    var missingRequired =
        requiredClauseIds.stream().filter(id -> !selectedClauseIds.contains(id)).toList();

    if (!missingRequired.isEmpty()) {
      var problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.UNPROCESSABLE_ENTITY, "Required clause(s) missing: " + missingRequired);
      problem.setTitle("Missing required clauses");
      throw new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY, problem, null);
    }

    // Validate all clause IDs exist
    var loadedClauses = clauseRepository.findAllById(selectedClauseIds);
    Map<UUID, Clause> clauseMap =
        loadedClauses.stream().collect(Collectors.toMap(Clause::getId, Function.identity()));

    var invalidIds = selectedClauseIds.stream().filter(id -> !clauseMap.containsKey(id)).toList();

    if (!invalidIds.isEmpty()) {
      throw new InvalidStateException(
          "Invalid clause IDs", "The following clause IDs do not exist: " + invalidIds);
    }

    // Return clauses in caller-specified sort order
    return selections.stream()
        .sorted(Comparator.comparingInt(ClauseSelection::sortOrder))
        .map(s -> clauseMap.get(s.clauseId()))
        .toList();
  }
}
