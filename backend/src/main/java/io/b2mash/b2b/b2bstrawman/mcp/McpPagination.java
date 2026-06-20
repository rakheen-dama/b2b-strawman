package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import java.util.List;

/**
 * Normalises {@code page}/{@code size} at the MCP boundary so an LLM never receives an unbounded
 * blob (§11.4). Firm-side returns are uneven (entities, controller-private records, unbounded
 * lists); this helper clamps the requested page size to the server hard max and slices an unbounded
 * {@link List} into a {@link McpPage}, setting {@code total}/{@code truncated}.
 *
 * <p>Caps mirror the existing controller limits: default/activity max <b>50</b>, audit max
 * <b>200</b>. A global per-call item ceiling ({@link #RESPONSE_ITEM_CEILING}) lets tools fail with
 * a structured {@link McpError#responseTooLarge()} rather than ever emitting a truncated blob.
 */
public final class McpPagination {

  /** Default and activity-class hard max page size. */
  public static final int DEFAULT_MAX_SIZE = 50;

  /** Audit-class hard max page size (mirrors the audit controller cap). */
  public static final int AUDIT_MAX_SIZE = 200;

  /**
   * Hard per-call total-count ceiling (§11.13). A tool whose underlying result set exceeds this
   * should return {@link McpError#responseTooLarge()} rather than a truncated blob, so the LLM is
   * told to narrow its query instead of silently losing rows. Independent of {@link
   * #AUDIT_MAX_SIZE} by design: this guards the total response size per call, not a per-page audit
   * cap, so raising the audit page cap later must not silently move this ceiling.
   */
  public static final int RESPONSE_ITEM_CEILING = 200;

  private McpPagination() {}

  /** Clamp a requested page size into {@code (0, hardMax]}, defaulting non-positive requests. */
  public static int clampSize(int requestedSize, int hardMax) {
    if (hardMax <= 0) {
      // hardMax is always a positive server constant (DEFAULT_MAX_SIZE / AUDIT_MAX_SIZE) at the
      // real call sites; fail loudly rather than let a misconfigured cap yield a negative window
      // that would make subList throw IndexOutOfBoundsException downstream.
      throw new IllegalArgumentException("hardMax must be > 0, was " + hardMax);
    }
    if (requestedSize <= 0) {
      return Math.min(DEFAULT_MAX_SIZE, hardMax);
    }
    return Math.min(requestedSize, hardMax);
  }

  /**
   * Slice an unbounded firm-side list into a page envelope, clamping size to {@code hardMax} and
   * page to a non-negative index.
   */
  public static <T> McpPage<T> paginate(
      List<T> all, Integer page, Integer requestedSize, int hardMax) {
    // page/size are optional MCP tool params. Spring AI binds an omitted param to null, so they
    // arrive boxed and nullable — never unbox blindly (that NPEs the whole tool call). Default a
    // null/negative page to 0 and a null/non-positive size to the server default (via clampSize).
    int size = clampSize(requestedSize == null ? 0 : requestedSize, hardMax);
    int p = (page == null) ? 0 : Math.max(page, 0);
    long total = all.size();
    // Widen to long before multiplying: page is LLM-sourced, so a runaway page index would
    // otherwise overflow int, wrap negative, and make subList throw IndexOutOfBoundsException.
    long offset = (long) p * size;
    int from = (int) Math.min(offset, total);
    int to = (int) Math.min(offset + size, total);
    List<T> slice = all.subList(from, to);
    boolean truncated = total > offset + size;
    return McpPage.of(slice, p, size, total, truncated);
  }

  /**
   * Global per-call response-size guard. Returns {@code true} when the total result count exceeds
   * {@link #RESPONSE_ITEM_CEILING}, signalling the tool should return {@link
   * McpError#responseTooLarge()} instead of a (truncated) page.
   */
  public static boolean exceedsResponseCeiling(long totalItems) {
    return totalItems > RESPONSE_ITEM_CEILING;
  }
}
