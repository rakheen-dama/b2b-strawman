package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseDetail;
import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseRequest;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing clause associations on document templates. */
@Service
public class TemplateClauseService {

  private static final Logger log = LoggerFactory.getLogger(TemplateClauseService.class);

  private final TemplateClauseRepository templateClauseRepository;
  private final ClauseRepository clauseRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final AuditService auditService;

  public TemplateClauseService(
      TemplateClauseRepository templateClauseRepository,
      ClauseRepository clauseRepository,
      DocumentTemplateRepository documentTemplateRepository,
      AuditService auditService) {
    this.templateClauseRepository = templateClauseRepository;
    this.clauseRepository = clauseRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.auditService = auditService;
  }

  /**
   * Returns the enriched clause details for a template, ordered by sort order.
   *
   * @param templateId the template ID
   * @return list of enriched template clause details
   */
  @Transactional(readOnly = true)
  public List<TemplateClauseDetail> getTemplateClauses(UUID templateId) {
    requireTemplateExists(templateId);

    var associations = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
    if (associations.isEmpty()) {
      return List.of();
    }

    var clauseIds = associations.stream().map(TemplateClause::getClauseId).toList();
    var clauseMap =
        clauseRepository.findAllById(clauseIds).stream()
            .collect(Collectors.toMap(Clause::getId, Function.identity()));

    return associations.stream()
        .map(
            tc -> {
              var clause = clauseMap.get(tc.getClauseId());
              if (clause == null) {
                return null;
              }
              return toDetail(tc, clause);
            })
        .filter(d -> d != null)
        .toList();
  }

  /**
   * Atomically replaces the full clause list for a template. Validates that all clause IDs exist
   * and are active.
   *
   * @param templateId the template ID
   * @param requests the new clause list
   * @return the enriched clause details after replacement
   */
  @Transactional
  public List<TemplateClauseDetail> setTemplateClauses(
      UUID templateId, List<TemplateClauseRequest> requests) {
    requireTemplateExists(templateId);

    // Validate all clause IDs exist and are active
    var clauseIds = requests.stream().map(TemplateClauseRequest::clauseId).toList();
    var clauses = clauseRepository.findAllById(clauseIds);
    if (clauses.size() != clauseIds.size()) {
      var foundIds = clauses.stream().map(Clause::getId).collect(Collectors.toSet());
      var missing = clauseIds.stream().filter(id -> !foundIds.contains(id)).toList();
      throw new IllegalArgumentException("Clause(s) not found: " + missing);
    }

    var inactiveClauses = clauses.stream().filter(c -> !c.isActive()).map(Clause::getId).toList();
    if (!inactiveClauses.isEmpty()) {
      throw new IllegalArgumentException("Clause(s) are inactive: " + inactiveClauses);
    }

    // Delete existing associations and insert new ones
    templateClauseRepository.deleteAllByTemplateId(templateId);
    templateClauseRepository.flush();

    var clauseMap = clauses.stream().collect(Collectors.toMap(Clause::getId, Function.identity()));

    var newAssociations =
        requests.stream()
            .map(
                req ->
                    new TemplateClause(templateId, req.clauseId(), req.sortOrder(), req.required()))
            .toList();
    templateClauseRepository.saveAll(newAssociations);

    log.info("Set {} clauses on template {}", newAssociations.size(), templateId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template_clause.configured")
            .entityType("document_template")
            .entityId(templateId)
            .details(Map.of("operation", "set", "clauseCount", newAssociations.size()))
            .build());

    return newAssociations.stream()
        .map(tc -> toDetail(tc, clauseMap.get(tc.getClauseId())))
        .toList();
  }

  /**
   * Adds a single clause to a template at the end of the sort order.
   *
   * @param templateId the template ID
   * @param clauseId the clause ID to add
   * @param required whether the clause is required
   * @return the enriched detail for the new association
   */
  @Transactional
  public TemplateClauseDetail addClauseToTemplate(
      UUID templateId, UUID clauseId, boolean required) {
    requireTemplateExists(templateId);

    var clause =
        clauseRepository
            .findById(clauseId)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", clauseId));

    if (templateClauseRepository.existsByTemplateIdAndClauseId(templateId, clauseId)) {
      throw new ResourceConflictException(
          "Clause already linked",
          "Clause " + clauseId + " is already associated with template " + templateId);
    }

    int nextSortOrder = templateClauseRepository.findMaxSortOrderByTemplateId(templateId) + 1;
    var association = new TemplateClause(templateId, clauseId, nextSortOrder, required);
    association = templateClauseRepository.save(association);

    log.info("Added clause {} to template {} at sortOrder {}", clauseId, templateId, nextSortOrder);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template_clause.configured")
            .entityType("document_template")
            .entityId(templateId)
            .details(Map.of("operation", "add", "clauseId", clauseId.toString()))
            .build());

    return toDetail(association, clause);
  }

  /**
   * Removes a clause from a template. Idempotent -- does not fail if the association does not
   * exist.
   *
   * @param templateId the template ID
   * @param clauseId the clause ID to remove
   */
  @Transactional
  public void removeClauseFromTemplate(UUID templateId, UUID clauseId) {
    requireTemplateExists(templateId);

    templateClauseRepository.deleteByTemplateIdAndClauseId(templateId, clauseId);

    log.info("Removed clause {} from template {}", clauseId, templateId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template_clause.configured")
            .entityType("document_template")
            .entityId(templateId)
            .details(Map.of("operation", "remove", "clauseId", clauseId.toString()))
            .build());
  }

  private void requireTemplateExists(UUID templateId) {
    if (!documentTemplateRepository.existsById(templateId)) {
      throw new ResourceNotFoundException("DocumentTemplate", templateId);
    }
  }

  private TemplateClauseDetail toDetail(TemplateClause tc, Clause clause) {
    String bodyPreview =
        clause.getBody() != null && clause.getBody().length() > 200
            ? clause.getBody().substring(0, 200)
            : clause.getBody();

    return new TemplateClauseDetail(
        tc.getId(),
        clause.getId(),
        clause.getTitle(),
        clause.getSlug(),
        clause.getCategory(),
        clause.getDescription(),
        bodyPreview,
        tc.isRequired(),
        tc.getSortOrder(),
        clause.isActive());
  }
}
