package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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

  public ReportExecutionService(
      List<ReportQuery> queries, ReportDefinitionRepository reportDefinitionRepository) {
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
  }

  /** True if the current caller holds any extra capability the report's query requires. */
  public boolean canAccess(String slug) {
    var query = queryMap.get(slug);
    return query == null
        || query.requiredCapability().map(RequestScopes::hasCapability).orElse(true);
  }

  /**
   * Throws 403 when the caller lacks the extra capability the report requires (e.g. VIEW_TRUST).
   */
  public void assertCanAccess(String slug) {
    if (!canAccess(slug)) {
      throw new ForbiddenException(
          "Insufficient permissions",
          "This report requires the "
              + queryMap.get(slug).requiredCapability().orElseThrow()
              + " capability");
    }
  }

  /**
   * Runs a report query with pagination and returns the {@code REPORT_GENERATED} row with it, not
   * logged: on the HTTP path this runs inside {@link ReportExportService}'s read-only transaction,
   * where a joined write is never flushed. {@link ReportRunService} writes the row after that
   * transaction has committed (Phase 92 FU-12).
   */
  @Transactional
  public AuditedReport<ReportExecutionResponse> execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    assertCanAccess(slug);
    var query = queryMap.get(slug);
    if (query == null) {
      throw new InvalidStateException(
          "Report not executable", "No query implementation registered for report slug: " + slug);
    }

    var result = query.execute(parameters, pageable);

    var audit =
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
            .build();

    return new AuditedReport<>(toResponse(definition, parameters, result, pageable), audit);
  }

  /**
   * Execute report for export (PDF/CSV). No pagination. Audit logging (REPORT_EXPORTED) is done by
   * {@link ReportExportService} after rendering completes, because it knows the export format.
   */
  @Transactional(readOnly = true)
  public ReportResult executeForExport(String slug, Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    assertCanAccess(slug);
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
