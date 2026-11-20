package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface ReportQuery {

  /** The slug that links this query to a ReportDefinition. */
  String getSlug();

  /** Execute the report with pagination (for HTML preview). */
  ReportResult execute(Map<String, Object> parameters, Pageable pageable);

  /** Execute the report without pagination (for PDF/CSV export). */
  ReportResult executeAll(Map<String, Object> parameters);

  /**
   * An additional capability (beyond FINANCIAL_VISIBILITY) the caller must hold to see this report,
   * e.g. VIEW_TRUST for reports that expose trust figures. Enforced by ReportExecutionService.
   */
  default Optional<String> requiredCapability() {
    return Optional.empty();
  }
}
