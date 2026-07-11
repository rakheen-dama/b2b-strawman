package io.b2mash.b2b.b2bstrawman.reporting;

/**
 * Shared invoice-aging bucket definition (Phase 83, 593A.1). The day-count boundaries (0 / 30 / 60
 * / 90, relative to an invoice's due date) live here <em>once</em> and are projected two ways:
 *
 * <ul>
 *   <li><strong>Five-way report split</strong> — {@link #reportCaseSql(String)} + {@link
 *       #reportBucketLabel(String)}: {@code CURRENT} / {@code 1_30} / {@code 31_60} / {@code 61_90}
 *       / {@code 90_PLUS}. Consumed by {@link InvoiceAgingReportQuery}; its keys and labels are
 *       contractual (pinned by {@code InvoiceAgingReportQueryTest}).
 *   <li><strong>Four-way debtor-book / digest split</strong> — {@link #currentPredicate(String)} /
 *       {@link #d30Predicate(String)} / {@link #d60Predicate(String)} / {@link
 *       #d90PlusPredicate(String)}: {@code current} (≤ 0) / {@code d30} (1–30) / {@code d60}
 *       (31–60) / {@code d90plus} (≥ 61, i.e. 61–90 and 90+ merged). Consumed by {@code
 *       CollectionsReadService} and {@code CashDigestService}.
 * </ul>
 *
 * <p>Each method takes the SQL expression that yields the integer days-overdue (e.g. {@code
 * "CAST(:asOfDate AS DATE) - i.due_date"} for the report or {@code "CURRENT_DATE - i.due_date"} for
 * the debtor book) so the same boundaries apply regardless of how a caller spells "days overdue".
 * Callers keep their column aliases and surrounding SQL; only the boundary arithmetic is shared.
 */
public final class AgingBuckets {

  private AgingBuckets() {}

  // ── Shared day-count boundaries (days overdue relative to due date). Defined ONCE. ──
  static final int CURRENT_UPPER_DAYS = 0;
  static final int DAYS_30 = 30;
  static final int DAYS_60 = 60;
  static final int DAYS_90 = 90;

  // ── Five-way report bucket keys (contractual — pinned by InvoiceAgingReportQueryTest). ──
  public static final String KEY_CURRENT = "CURRENT";
  public static final String KEY_1_30 = "1_30";
  public static final String KEY_31_60 = "31_60";
  public static final String KEY_61_90 = "61_90";
  public static final String KEY_90_PLUS = "90_PLUS";

  /**
   * The five-way {@code CASE} expression producing the report {@code age_bucket} key from an
   * integer days-overdue expression. Semantically identical to the inline CASE that used to live in
   * {@link InvoiceAgingReportQuery}: {@code ≤ 0 → CURRENT}, {@code 1–30 → 1_30}, {@code 31–60 →
   * 31_60}, {@code 61–90 → 61_90}, {@code else → 90_PLUS}.
   */
  public static String reportCaseSql(String daysOverdueExpr) {
    return """
        CASE
            WHEN %1$s <= %2$d THEN '%3$s'
            WHEN %1$s BETWEEN %4$d AND %5$d THEN '%6$s'
            WHEN %1$s BETWEEN %7$d AND %8$d THEN '%9$s'
            WHEN %1$s BETWEEN %10$d AND %11$d THEN '%12$s'
            ELSE '%13$s'
        END"""
        .formatted(
            daysOverdueExpr,
            CURRENT_UPPER_DAYS,
            KEY_CURRENT,
            CURRENT_UPPER_DAYS + 1,
            DAYS_30,
            KEY_1_30,
            DAYS_30 + 1,
            DAYS_60,
            KEY_31_60,
            DAYS_60 + 1,
            DAYS_90,
            KEY_61_90,
            KEY_90_PLUS);
  }

  /** The human-readable label for a five-way report bucket key. */
  public static String reportBucketLabel(String bucket) {
    return switch (bucket) {
      case KEY_CURRENT -> "Current";
      case KEY_1_30 -> "1-30 Days";
      case KEY_31_60 -> "31-60 Days";
      case KEY_61_90 -> "61-90 Days";
      case KEY_90_PLUS -> "90+ Days";
      default -> bucket;
    };
  }

  // ── Four-way debtor-book / digest predicates (for FILTER (WHERE …) clauses). ──

  /** {@code current} bucket: not yet overdue (≤ 0 days past due). */
  public static String currentPredicate(String daysOverdueExpr) {
    return "%s <= %d".formatted(daysOverdueExpr, CURRENT_UPPER_DAYS);
  }

  /** {@code d30} bucket: 1–30 days past due. */
  public static String d30Predicate(String daysOverdueExpr) {
    return "%s BETWEEN %d AND %d".formatted(daysOverdueExpr, CURRENT_UPPER_DAYS + 1, DAYS_30);
  }

  /** {@code d60} bucket: 31–60 days past due. */
  public static String d60Predicate(String daysOverdueExpr) {
    return "%s BETWEEN %d AND %d".formatted(daysOverdueExpr, DAYS_30 + 1, DAYS_60);
  }

  /** {@code d90plus} bucket: 61+ days past due (61–90 and 90+ merged). */
  public static String d90PlusPredicate(String daysOverdueExpr) {
    return "%s >= %d".formatted(daysOverdueExpr, DAYS_60 + 1);
  }
}
