package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.List;
import java.util.Map;

public record ReportResult(
    List<Map<String, Object>> rows,
    Map<String, Object> summary,
    long totalElements,
    int totalPages) {
  /** Convenience constructor for unpaginated results. */
  public ReportResult(List<Map<String, Object>> rows, Map<String, Object> summary) {
    this(rows, summary, rows.size(), 1);
  }
}
