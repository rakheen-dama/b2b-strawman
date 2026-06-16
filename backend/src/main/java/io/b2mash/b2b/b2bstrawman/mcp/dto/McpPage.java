package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.List;

/**
 * Pagination envelope for every MCP list tool (ADR-306). Flat named fields, no ORM entity
 * serialisation — money is carried as minor units + currency and dates as ISO-8601 by the item DTOs
 * themselves. The envelope reports {@code total} and {@code truncated} so an LLM can tell when more
 * data exists beyond the current page.
 *
 * @param items the page slice (never unbounded — clamped at the MCP boundary by {@link
 *     io.b2mash.b2b.b2bstrawman.mcp.McpPagination})
 * @param page zero-based page index actually returned
 * @param size effective page size after clamping
 * @param total total number of items across all pages
 * @param truncated {@code true} when more items exist beyond this page
 */
public record McpPage<T>(List<T> items, int page, int size, long total, boolean truncated) {

  public static <T> McpPage<T> of(
      List<T> items, int page, int size, long total, boolean truncated) {
    return new McpPage<>(List.copyOf(items), page, size, total, truncated);
  }
}
