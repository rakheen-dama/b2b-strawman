package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportExecutionService {

  private final Map<String, ReportQuery> queryMap;
  private final ReportDefinitionRepository reportDefinitionRepository;
  private final AuditService auditService;

  public ReportExecutionService(
      List<ReportQuery> queries,
      ReportDefinitionRepository reportDefinitionRepository,
      AuditService auditService) {
    this.queryMap =
        queries.stream()
            .collect(
                Collectors.toMap(
                    ReportQuery::getSlug,
                    Function.identity(),
                    (existing, duplicate) -> {
                      throw new IllegalStateException(
                          "Duplicate ReportQuery slug '"
                              + existing.getSlug()
                              + "': "
                              + existing.getClass().getSimpleName()
                              + " and "
                              + duplicate.getClass().getSimpleName());
                    }));
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.auditService = auditService;
  }

  @Transactional
  public ReportExecutionResponse execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var query = queryMap.get(slug);
    if (query == null) {
      throw new InvalidStateException(
          "Report not executable", "No query implementation registered for report slug: " + slug);
    }

    var result = query.execute(parameters, pageable);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REPORT_GENERATED")
            .entityType("REPORT")
            .entityId(definition.getId())
            .details(
                Map.of(
                    "slug",
                    slug,
                    "parameters",
                    parameters,
                    "format",
                    "preview",
                    "rowCount",
                    result.totalElements()))
            .build());

    return toResponse(definition, parameters, result, pageable);
  }

  /**
   * Execute report for export (PDF/CSV). No pagination. Audit logging (REPORT_EXPORTED) is handled
   * by the controller after rendering completes, because the controller knows the export format.
   */
  @Transactional(readOnly = true)
  public ReportResult executeForExport(String slug, Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var query = queryMap.get(slug);
    if (query == null) {
      throw new InvalidStateException(
          "Report not executable", "No query implementation registered for report slug: " + slug);
    }
    return query.executeAll(parameters);
  }

  @SuppressWarnings("unchecked")
  private ReportExecutionResponse toResponse(
      ReportDefinition definition,
      Map<String, Object> parameters,
      ReportResult result,
      Pageable pageable) {
    var colDefs = definition.getColumnDefinitions();
    var rawColumns = colDefs.get("columns");
    if (rawColumns == null) {
      throw new InvalidStateException(
          "Invalid report definition",
          "ReportDefinition '"
              + definition.getSlug()
              + "' has invalid column_definitions: missing 'columns' array");
    }
    var columns =
        ((List<Map<String, String>>) rawColumns)
            .stream()
                .map(
                    c ->
                        new ColumnDefinition(
                            c.get("key"), c.get("label"), c.get("type"), c.get("format")))
                .toList();

    return new ReportExecutionResponse(
        definition.getName(),
        parameters,
        Instant.now().toString(),
        columns,
        result.rows(),
        result.summary(),
        new ReportExecutionResponse.PaginationInfo(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            result.totalElements(),
            result.totalPages()));
  }
}
