package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-definitions")
public class ReportingController {

  private static final Map<String, String> CATEGORY_LABELS =
      Map.of(
          "TIME_ATTENDANCE", "Time & Attendance",
          "FINANCIAL", "Financial",
          "PROJECT", "Project");

  private final ReportDefinitionRepository reportDefinitionRepository;

  public ReportingController(ReportDefinitionRepository reportDefinitionRepository) {
    this.reportDefinitionRepository = reportDefinitionRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CategorizedReportsResponse> listReportDefinitions() {
    var definitions = reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();

    var categories =
        definitions.stream()
            .collect(
                Collectors.groupingBy(
                    ReportDefinition::getCategory,
                    java.util.LinkedHashMap::new,
                    Collectors.toList()))
            .entrySet()
            .stream()
            .map(
                entry ->
                    new CategoryGroup(
                        entry.getKey(),
                        CATEGORY_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue().stream()
                            .map(
                                def ->
                                    new ReportSummary(
                                        def.getSlug(), def.getName(), def.getDescription()))
                            .toList()))
            .toList();

    return ResponseEntity.ok(new CategorizedReportsResponse(categories));
  }

  @GetMapping("/{slug}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportDetailResponse> getReportDefinition(@PathVariable String slug) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    return ResponseEntity.ok(
        new ReportDetailResponse(
            definition.getSlug(),
            definition.getName(),
            definition.getDescription(),
            definition.getCategory(),
            definition.getParameterSchema(),
            definition.getColumnDefinitions(),
            definition.isSystem()));
  }

  // --- Response DTOs ---

  public record CategorizedReportsResponse(List<CategoryGroup> categories) {}

  public record CategoryGroup(String category, String label, List<ReportSummary> reports) {}

  public record ReportSummary(String slug, String name, String description) {}

  public record ReportDetailResponse(
      String slug,
      String name,
      String description,
      String category,
      Map<String, Object> parameterSchema,
      Map<String, Object> columnDefinitions,
      boolean isSystem) {}
}
