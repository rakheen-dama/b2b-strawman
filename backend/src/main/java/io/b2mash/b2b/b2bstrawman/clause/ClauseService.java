package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.dto.ClauseResponse;
import io.b2mash.b2b.b2bstrawman.clause.dto.CreateClauseRequest;
import io.b2mash.b2b.b2bstrawman.clause.dto.UpdateClauseRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextBuilder;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplateSecurityValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing clauses in the clause library. */
@Service
public class ClauseService {

  private static final Logger log = LoggerFactory.getLogger(ClauseService.class);

  private final ClauseRepository clauseRepository;
  private final AuditService auditService;
  private final PdfRenderingService pdfRenderingService;
  private final List<TemplateContextBuilder> contextBuilders;

  public ClauseService(
      ClauseRepository clauseRepository,
      AuditService auditService,
      PdfRenderingService pdfRenderingService,
      List<TemplateContextBuilder> contextBuilders) {
    this.clauseRepository = clauseRepository;
    this.auditService = auditService;
    this.pdfRenderingService = pdfRenderingService;
    this.contextBuilders = contextBuilders;
  }

  @Transactional
  public ClauseResponse createClause(CreateClauseRequest request) {
    TemplateSecurityValidator.validate(request.body());

    String baseSlug = DocumentTemplate.generateSlug(request.title());
    String uniqueSlug = resolveUniqueSlug(baseSlug);

    var clause = new Clause(request.title(), uniqueSlug, request.body(), request.category());
    clause.update(
        request.title(), uniqueSlug, request.description(), request.body(), request.category());
    clause = clauseRepository.save(clause);

    log.info("Created clause {} title={}", clause.getId(), clause.getTitle());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("clause.created")
            .entityType("clause")
            .entityId(clause.getId())
            .details(Map.of("title", clause.getTitle(), "category", clause.getCategory()))
            .build());

    return ClauseResponse.from(clause);
  }

  @Transactional
  public ClauseResponse updateClause(UUID id, UpdateClauseRequest request) {
    var clause =
        clauseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", id));

    if (clause.getSource() == ClauseSource.SYSTEM) {
      throw new InvalidStateException(
          "System clause cannot be edited",
          "System clauses cannot be edited. Clone this clause to customize it.");
    }

    TemplateSecurityValidator.validate(request.body());

    String newSlug;
    if (!clause.getTitle().equals(request.title())) {
      String baseSlug = DocumentTemplate.generateSlug(request.title());
      newSlug = resolveUniqueSlug(baseSlug);
    } else {
      newSlug = clause.getSlug();
    }

    clause.update(
        request.title(), newSlug, request.description(), request.body(), request.category());
    clause = clauseRepository.save(clause);

    log.info("Updated clause {} title={}", clause.getId(), clause.getTitle());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("clause.updated")
            .entityType("clause")
            .entityId(clause.getId())
            .details(Map.of("title", clause.getTitle(), "category", clause.getCategory()))
            .build());

    return ClauseResponse.from(clause);
  }

  @Transactional
  public void deleteClause(UUID id) {
    var clause =
        clauseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", id));

    long refCount = clauseRepository.countTemplateClauseReferences(id);
    if (refCount > 0) {
      throw new ResourceConflictException(
          "Clause is referenced",
          "This clause is used by "
              + refCount
              + " template(s). Remove it from those templates first, or deactivate it instead.");
    }

    clauseRepository.delete(clause);

    log.info("Deleted clause {} title={}", clause.getId(), clause.getTitle());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("clause.deleted")
            .entityType("clause")
            .entityId(clause.getId())
            .details(Map.of("title", clause.getTitle()))
            .build());
  }

  @Transactional
  public ClauseResponse deactivateClause(UUID id) {
    var clause =
        clauseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", id));

    clause.deactivate();
    clause = clauseRepository.save(clause);

    log.info("Deactivated clause {} title={}", clause.getId(), clause.getTitle());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("clause.deactivated")
            .entityType("clause")
            .entityId(clause.getId())
            .details(Map.of("title", clause.getTitle()))
            .build());

    return ClauseResponse.from(clause);
  }

  @Transactional
  public ClauseResponse cloneClause(UUID id) {
    var original =
        clauseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", id));

    String cloneTitle = "Copy of " + original.getTitle();
    String baseSlug = DocumentTemplate.generateSlug(cloneTitle);
    String uniqueSlug = resolveUniqueSlug(baseSlug);

    var clone = Clause.cloneFrom(original, uniqueSlug);
    clone.update(
        cloneTitle,
        uniqueSlug,
        original.getDescription(),
        original.getBody(),
        original.getCategory());
    clone = clauseRepository.save(clone);

    log.info(
        "Cloned clause {} from {} title={}", clone.getId(), original.getId(), clone.getTitle());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("clause.cloned")
            .entityType("clause")
            .entityId(clone.getId())
            .details(
                Map.of(
                    "title", clone.getTitle(),
                    "sourceClauseId", original.getId().toString()))
            .build());

    return ClauseResponse.from(clone);
  }

  @Transactional(readOnly = true)
  public List<ClauseResponse> listClauses(boolean includeInactive, String category) {
    List<Clause> clauses;
    if (category != null && !category.isBlank()) {
      clauses =
          includeInactive
              ? clauseRepository.findAllByOrderByCategoryAscSortOrderAsc().stream()
                  .filter(c -> c.getCategory().equals(category))
                  .toList()
              : clauseRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(category);
    } else {
      clauses =
          includeInactive
              ? clauseRepository.findAllByOrderByCategoryAscSortOrderAsc()
              : clauseRepository.findByActiveTrueOrderByCategoryAscSortOrderAsc();
    }
    return clauses.stream().map(ClauseResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<String> listCategories() {
    return clauseRepository.findDistinctActiveCategories();
  }

  @Transactional(readOnly = true)
  public ClauseResponse getById(UUID id) {
    var clause =
        clauseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", id));
    return ClauseResponse.from(clause);
  }

  @Transactional(readOnly = true)
  public String previewClause(UUID clauseId, UUID entityId, TemplateEntityType entityType) {
    var clause =
        clauseRepository
            .findById(clauseId)
            .orElseThrow(() -> new ResourceNotFoundException("Clause", clauseId));

    var builder =
        contextBuilders.stream()
            .filter(b -> b.supports() == entityType)
            .findFirst()
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "Unsupported entity type", "No context builder for " + entityType));

    var memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    var contextMap = builder.buildContext(entityId, memberId);
    return pdfRenderingService.renderThymeleaf(clause.getBody(), contextMap);
  }

  private String resolveUniqueSlug(String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (clauseRepository.findBySlug(finalSlug).isPresent()) {
      finalSlug = baseSlug + "-" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
