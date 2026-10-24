package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.util.Set;

/**
 * The debtor-book definition (Phase 88, §13.8.4, OQ #12): ONE predicate and ONE amount expression
 * shared by every debtor-book SQL site — the debtor book, its count and drill-in ({@link
 * CollectionsReadService}), the outstanding summary ({@code GET
 * /api/invoices/outstanding-summary}), the client list ({@code ClientListRowRepository}), the cash
 * digest ({@link CashDigestService}), the aging report ({@code InvoiceAgingReportQuery}) and the
 * dunning scan ({@link CollectionsScanService}) — so no two surfaces can disagree about what a
 * client owes (ADR-347).
 *
 * <p>Both expressions assume the invoices table is aliased {@code i} at every site (adjust the
 * alias at the site, never the constant). Both are compile-time constants so they can be
 * concatenated into {@code @Query} annotation values. The predicate is a pure AND-conjunction —
 * safe to splice after another {@code AND}; if it ever gains an {@code OR}, every site must wrap it
 * in parentheses. {@code CollectionsSqlSourceScanTest} fails the build on any inline copy of the
 * due-date guard outside this class.
 *
 * <p>Java-side status filters over loaded {@code Invoice} entities that mean "in the debtor book"
 * (e.g. {@link CollectionsTriageService}'s outstanding set) read {@link #OUTSTANDING_STATUSES}
 * rather than comparing against {@code InvoiceStatus.SENT} inline, so the SQL and Java views of the
 * book cannot drift.
 *
 * <p>Semantics (Phase 88 622D, ADR-347 A3): a fee note is in the debtor book while it is SENT or
 * PARTIALLY_PAID and carries a due date, and it contributes its balance due {@code total -
 * amount_paid} ({@code amount_paid} is the persisted running sum, CHECK-bounded to {@code [0,
 * total]} by V4 §3). 620B introduced the constants with the SENT/total definition; 622D flipped all
 * three together.
 */
public final class CollectionsSql {

  /** A fee note in the debtor book: sent or part-paid, carrying a due date (alias {@code i}). */
  public static final String OUTSTANDING_PREDICATE =
      "i.status IN ('SENT','PARTIALLY_PAID') AND i.due_date IS NOT NULL";

  /** The amount a debtor-book fee note contributes — its balance due (alias {@code i}). */
  public static final String OUTSTANDING_AMOUNT = "(i.total - i.amount_paid)";

  /**
   * The Java companion of {@link #OUTSTANDING_PREDICATE}: the invoice statuses that put a fee note
   * in the debtor book. It MUST list exactly the statuses quoted in {@code OUTSTANDING_PREDICATE}
   * ({@code CollectionsSqlSourceScanTest} pins the two together). Status only: callers apply their
   * own due-date conditions.
   */
  public static final Set<InvoiceStatus> OUTSTANDING_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID);

  private CollectionsSql() {}
}
