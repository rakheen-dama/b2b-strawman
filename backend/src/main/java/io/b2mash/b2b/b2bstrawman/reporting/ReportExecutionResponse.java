package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.List;
import java.util.Map;

public record ReportExecutionResponse(
    String reportName,
    Map<String, Object> parameters,
    String generatedAt,
    List<ColumnDefinition> columns,
    List<Map<String, Object>> rows,
    Map<String, Object> summary,
    PaginationInfo pagination) {
  public record PaginationInfo(int page, int size, long totalElements, int totalPages) {}
}
