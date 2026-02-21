package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface ReportQuery {

  /** The slug that links this query to a ReportDefinition. */
  String getSlug();

  /** Execute the report with pagination (for HTML preview). */
  ReportResult execute(Map<String, Object> parameters, Pageable pageable);

  /** Execute the report without pagination (for PDF/CSV export). */
  ReportResult executeAll(Map<String, Object> parameters);
}
