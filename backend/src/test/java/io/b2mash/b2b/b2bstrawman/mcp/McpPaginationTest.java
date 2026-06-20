package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests for the {@link McpPagination} slicing/clamping contract (Epic 562C). No Spring
 * context — the helper is pure. Covers the edge cases an LLM-sourced {@code page}/{@code size} can
 * hit so a list tool never hands the model a broken or unbounded page.
 */
class McpPaginationTest {

  private static List<Integer> range(int n) {
    return IntStream.range(0, n).boxed().toList();
  }

  // ---- clampSize --------------------------------------------------------------

  @Test
  void clampSizeDefaultsNonPositiveRequests() {
    assertThat(McpPagination.clampSize(0, McpPagination.DEFAULT_MAX_SIZE)).isEqualTo(50);
    assertThat(McpPagination.clampSize(-5, McpPagination.DEFAULT_MAX_SIZE)).isEqualTo(50);
  }

  @Test
  void clampSizeCapsAtHardMax() {
    assertThat(McpPagination.clampSize(999, McpPagination.DEFAULT_MAX_SIZE)).isEqualTo(50);
    assertThat(McpPagination.clampSize(999, McpPagination.AUDIT_MAX_SIZE)).isEqualTo(200);
  }

  @Test
  void clampSizePassesThroughInRangeRequests() {
    assertThat(McpPagination.clampSize(20, McpPagination.DEFAULT_MAX_SIZE)).isEqualTo(20);
  }

  // ---- paginate ---------------------------------------------------------------

  @Test
  void paginateEmptyList() {
    McpPage<Integer> page = McpPagination.paginate(List.of(), 0, 10, 50);
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.truncated()).isFalse();
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(10);
  }

  @Test
  void paginateNullPageAndSizeDefaultToFirstPage() {
    // Optional MCP paging params arrive as null when the client omits them. paginate must NOT
    // unbox-NPE — it defaults to page 0 / the server default size. (Regression: list_clients and
    // the other paginated tools threw NPE when called without page/size.)
    McpPage<Integer> page = McpPagination.paginate(range(120), null, null, 50);
    assertThat(page.items()).hasSize(50).containsExactlyElementsOf(range(50));
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.total()).isEqualTo(120);
    assertThat(page.truncated()).isTrue();
  }

  @Test
  void paginateFirstPageWithMoreToCome() {
    McpPage<Integer> page = McpPagination.paginate(range(120), 0, 50, 50);
    assertThat(page.items()).hasSize(50).containsExactlyElementsOf(range(50));
    assertThat(page.total()).isEqualTo(120);
    assertThat(page.truncated()).isTrue();
  }

  @Test
  void paginatePartialLastPage() {
    // 120 items, size 50 → page 2 has the final 20 items, not truncated.
    McpPage<Integer> page = McpPagination.paginate(range(120), 2, 50, 50);
    assertThat(page.items()).hasSize(20);
    assertThat(page.items().get(0)).isEqualTo(100);
    assertThat(page.total()).isEqualTo(120);
    assertThat(page.truncated()).isFalse();
  }

  @Test
  void paginateExactFitLastPageIsNotTruncated() {
    // 100 items, size 50 → page 1 is the last full page, nothing after it.
    McpPage<Integer> page = McpPagination.paginate(range(100), 1, 50, 50);
    assertThat(page.items()).hasSize(50);
    assertThat(page.truncated()).isFalse();
  }

  @Test
  void paginatePageBeyondEndReturnsEmptySliceNotError() {
    McpPage<Integer> page = McpPagination.paginate(range(10), 99, 50, 50);
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(10);
    assertThat(page.truncated()).isFalse();
  }

  @Test
  void paginateNegativePageIsClampedToZero() {
    McpPage<Integer> page = McpPagination.paginate(range(10), -3, 50, 50);
    assertThat(page.page()).isZero();
    assertThat(page.items()).hasSize(10);
  }

  @Test
  void paginateClampsOversizedRequestedSize() {
    McpPage<Integer> page = McpPagination.paginate(range(300), 0, 10_000, 50);
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.items()).hasSize(50);
    assertThat(page.truncated()).isTrue();
  }

  @Test
  void paginateHugePageIndexDoesNotOverflowOrThrow() {
    // Regression: an LLM-sourced runaway page index must not overflow int (p * size) and throw
    // IndexOutOfBoundsException — it must return an empty page with the correct total.
    McpPage<Integer> page = McpPagination.paginate(range(10), Integer.MAX_VALUE, 50, 50);
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(10);
    assertThat(page.truncated()).isFalse();
  }

  // ---- response ceiling -------------------------------------------------------

  @Test
  void exceedsResponseCeilingHonoursTheItemCap() {
    assertThat(McpPagination.exceedsResponseCeiling(McpPagination.RESPONSE_ITEM_CEILING)).isFalse();
    assertThat(McpPagination.exceedsResponseCeiling(McpPagination.RESPONSE_ITEM_CEILING + 1))
        .isTrue();
  }
}
