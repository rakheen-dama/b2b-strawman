package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.dto.OutstandingSummaryResponse;
import io.b2mash.b2b.b2bstrawman.reporting.AgingBuckets;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the collections domain (Phase 83, §4.1). Serves the debtor book, one customer's
 * chase drill-in, and the per-invoice activity ledger. All queries are pure reads — no writes, no
 * audit.
 *
 * <p>Tenant isolation is by {@code search_path} (schema-per-tenant); the native queries carry no
 * {@code tenant_id} predicate. The debtor book aggregates outstanding invoices (the shared
 * debtor-book predicate and amount in {@link CollectionsSql}, Phase 88 620B; also read by the
 * outstanding summary, the client list, the cash digest, the aging report and the dunning scan) per
 * {@code (customer, currency)} pair — invoice currency is per-invoice and a customer with
 * mixed-currency invoices surfaces as one row per currency, each carrying a single {@code currency}
 * — using the 4-bucket §4.1 aging split ({@code current} ≤ 0 days, {@code d30} = 1–30, {@code d60}
 * = 31–60, {@code d90plus} = 61+). Unlike the collections SCAN, the debtor book INCLUDES {@code
 * collections_exempt} customers (surfacing the flag) and INCLUDES not-yet-overdue SENT and
 * PARTIALLY_PAID fee notes (they land in the {@code current} bucket). Amounts are balances due
 * ({@link CollectionsSql#OUTSTANDING_AMOUNT}, 622D).
 *
 * <p>The four-way bucket boundaries are shared with the aging report and cash digest via {@link
 * AgingBuckets} (extracted 593A). {@code signals} is populated by {@link CollectionsTriageService}
 * (wired 592A.4) — one deterministic-signal computation per debtor-book row.
 */
@Service
public class CollectionsReadService {

  /**
   * The SQL expression yielding integer days-overdue for a joined {@code invoices i} row. Fed to
   * {@link AgingBuckets} so the debtor-book four-way split (593A) shares its boundaries with the
   * aging report and the cash digest.
   */
  private static final String AGE_EXPR = "CURRENT_DATE - i.due_date";

  private static final String DEBTOR_BOOK_SQL =
      """
      SELECT
          i.customer_id AS customer_id,
          MAX(c.name) AS customer_name,
          bool_or(c.collections_exempt) AS collections_exempt,
          SUM(%5$s) AS outstanding_total,
          i.currency AS currency,
          COUNT(*) AS invoice_count,
          MAX(CURRENT_DATE - i.due_date) AS oldest_days_overdue,
          COALESCE(SUM(%5$s) FILTER (WHERE %1$s), 0) AS bucket_current,
          COALESCE(SUM(%5$s) FILTER (WHERE %2$s), 0) AS bucket_d30,
          COALESCE(SUM(%5$s) FILTER (WHERE %3$s), 0) AS bucket_d60,
          COALESCE(SUM(%5$s) FILTER (WHERE %4$s), 0) AS bucket_d90plus,
          MAX(la.stage) AS last_stage,
          MAX(la.status) AS last_status,
          MAX(la.at) AS last_at
      FROM invoices i
      JOIN customers c ON c.id = i.customer_id
      LEFT JOIN LATERAL (
          SELECT a.stage AS stage, a.status AS status, a.updated_at AS at
          FROM collection_activities a
          WHERE a.customer_id = i.customer_id
          ORDER BY a.created_at DESC, a.id DESC
          LIMIT 1
      ) la ON true
      WHERE %6$s
      GROUP BY i.customer_id, i.currency
      ORDER BY outstanding_total DESC, i.customer_id, i.currency
      LIMIT :limit OFFSET :offset
      """
          .formatted(
              AgingBuckets.currentPredicate(AGE_EXPR),
              AgingBuckets.d30Predicate(AGE_EXPR),
              AgingBuckets.d60Predicate(AGE_EXPR),
              AgingBuckets.d90PlusPredicate(AGE_EXPR),
              CollectionsSql.OUTSTANDING_AMOUNT,
              CollectionsSql.OUTSTANDING_PREDICATE);

  /**
   * Counts debtor-book groups (one per {@code (customer_id, currency)} pair) for the {@link Page}
   * total. Mirrors {@link #DEBTOR_BOOK_SQL}'s WHERE/GROUP BY exactly so the count agrees with the
   * paged rows.
   */
  private static final String DEBTOR_BOOK_COUNT_SQL =
      """
      SELECT COUNT(*) FROM (
          SELECT 1
          FROM invoices i
          WHERE %s
          GROUP BY i.customer_id, i.currency
      ) grouped
      """
          .formatted(CollectionsSql.OUTSTANDING_PREDICATE);

  private static final String OUTSTANDING_INVOICES_SQL =
      """
      SELECT
          i.id AS invoice_id,
          i.invoice_number AS invoice_number,
          i.total AS total,
          %1$s AS balance_due,
          i.currency AS currency,
          i.due_date AS due_date,
          (CURRENT_DATE - i.due_date) AS days_overdue
      FROM invoices i
      WHERE i.customer_id = CAST(:customerId AS UUID)
        AND %2$s
      ORDER BY i.due_date
      """
          .formatted(CollectionsSql.OUTSTANDING_AMOUNT, CollectionsSql.OUTSTANDING_PREDICATE);

  /**
   * The Fee notes page summary (Phase 88 U3, §13.4.3): one statement, org default currency only.
   * Outstanding = the debtor-book definition; overdue = outstanding with {@code due_date <
   * CURRENT_DATE} (the same DB clock as the debtor book's {@code current} bucket, so overdue equals
   * the d30 + d60 + d90plus bucket sum); approved-unsent = APPROVED (no payments can exist yet).
   */
  private static final String OUTSTANDING_SUMMARY_SQL =
      """
      SELECT
          COALESCE(SUM(%1$s) FILTER (WHERE %2$s), 0) AS outstanding_total,
          COUNT(*) FILTER (WHERE %2$s) AS outstanding_count,
          COALESCE(SUM(%1$s) FILTER (WHERE %2$s AND i.due_date < CURRENT_DATE), 0) AS overdue_total,
          COUNT(*) FILTER (WHERE %2$s AND i.due_date < CURRENT_DATE) AS overdue_count,
          COALESCE(SUM(i.total) FILTER (WHERE i.status = 'APPROVED'), 0) AS approved_unsent_total,
          COUNT(*) FILTER (WHERE i.status = 'APPROVED') AS approved_unsent_count
      FROM invoices i
      WHERE i.currency = :currency
      """
          .formatted(CollectionsSql.OUTSTANDING_AMOUNT, CollectionsSql.OUTSTANDING_PREDICATE);

  private final EntityManager entityManager;
  private final CustomerRepository customerRepository;
  private final CollectionActivityRepository activityRepository;
  private final InvoiceRepository invoiceRepository;
  private final CollectionsTriageService triageService;
  private final OrgSettingsRepository orgSettingsRepository;

  public CollectionsReadService(
      EntityManager entityManager,
      CustomerRepository customerRepository,
      CollectionActivityRepository activityRepository,
      InvoiceRepository invoiceRepository,
      CollectionsTriageService triageService,
      OrgSettingsRepository orgSettingsRepository) {
    this.entityManager = entityManager;
    this.customerRepository = customerRepository;
    this.activityRepository = activityRepository;
    this.invoiceRepository = invoiceRepository;
    this.triageService = triageService;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  /**
   * Paged debtor book: per-{@code (customer, currency)} outstanding aggregation. Pagination is
   * pushed into SQL ({@code LIMIT}/{@code OFFSET}) with a separate {@code COUNT} for the total, so
   * only the requested page is materialised (no full-book slice in Java).
   */
  @Transactional(readOnly = true)
  public Page<DebtorResponse> getDebtors(Pageable pageable) {
    long total = countDebtorRows();
    if (pageable.getOffset() >= total) {
      return new PageImpl<>(List.of(), pageable, total);
    }
    var rows = queryDebtorRows(pageable.getPageSize(), pageable.getOffset());
    return new PageImpl<>(rows, pageable, total);
  }

  /** One customer's outstanding invoices plus a paged chase history. */
  @Transactional(readOnly = true)
  public DebtorDetailResponse getDebtor(UUID customerId, Pageable activitiesPageable) {
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    var outstanding = queryOutstandingInvoices(customerId);
    Page<ActivityResponse> activities =
        activityRepository.findByCustomerId(customerId, activitiesPageable).map(this::toActivity);
    return new DebtorDetailResponse(
        customer.getId(),
        customer.getName(),
        customer.isCollectionsExempt(),
        outstanding,
        activities);
  }

  /** The activity ledger for one invoice (invoice detail tab), newest-first. */
  @Transactional(readOnly = true)
  public List<ActivityResponse> getInvoiceActivities(UUID invoiceId) {
    if (!invoiceRepository.existsById(invoiceId)) {
      throw new ResourceNotFoundException("Invoice", invoiceId);
    }
    return activityRepository.findByInvoiceId(invoiceId).stream()
        .sorted(
            (a, b) -> {
              int byCreated = b.getCreatedAt().compareTo(a.getCreatedAt());
              return byCreated != 0 ? byCreated : b.getId().compareTo(a.getId());
            })
        .map(this::toActivity)
        .toList();
  }

  /**
   * Fee notes page tiles (Phase 88 U3, §13.4.3) on the debtor-book definition so the page, Home and
   * the client list cannot disagree. Org default currency only — fee notes in other currencies are
   * excluded (the debtor book lists them as separate per-currency rows). No actor scoping,
   * mirroring {@link #getDebtors}: the capability gate on the endpoint is the whole authorization.
   * The gates are NOT the same capability, though (decision D8): the debtor-book endpoints ({@code
   * CollectionsController}, incl. {@code getDebtors}) require {@code INVOICING}, while {@code GET
   * /api/invoices/outstanding-summary} requires {@code FINANCIAL_VISIBILITY} (§13.9.3) — a custom
   * role with INVOICING alone sees the debtor book but gets 403 here.
   */
  @Transactional(readOnly = true)
  public OutstandingSummaryResponse outstandingSummary() {
    String currency =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getDefaultCurrency)
            .orElse(OrgSettingsService.DEFAULT_CURRENCY);
    var query = entityManager.createNativeQuery(OUTSTANDING_SUMMARY_SQL, Tuple.class);
    query.setParameter("currency", currency);
    Tuple t = (Tuple) query.getSingleResult();
    return new OutstandingSummaryResponse(
        currency,
        money(t.get("outstanding_total")),
        toInt(t.get("outstanding_count")),
        money(t.get("overdue_total")),
        toInt(t.get("overdue_count")),
        money(t.get("approved_unsent_total")),
        toInt(t.get("approved_unsent_count")));
  }

  private long countDebtorRows() {
    var query = entityManager.createNativeQuery(DEBTOR_BOOK_COUNT_SQL);
    return ((Number) query.getSingleResult()).longValue();
  }

  private List<DebtorResponse> queryDebtorRows(int limit, long offset) {
    var query = entityManager.createNativeQuery(DEBTOR_BOOK_SQL, Tuple.class);
    query.setParameter("limit", limit);
    query.setParameter("offset", offset);
    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    var rows = new ArrayList<DebtorResponse>(tuples.size());
    // A mixed-currency customer surfaces as one row per currency; triage signals are per-CUSTOMER,
    // so compute them once per DISTINCT customer on the page rather than once per (customer,
    // currency) row (memoised across a customer's per-currency rows; bounded by page size). The
    // single TriageResult carries both the §3.4 signal names and the advisor-contributed detail
    // strings (592B) from ONE advisor pass. 593A extracts a set-based batch API for the seam.
    Map<UUID, CollectionsTriageService.TriageResult> triageByCustomer = new HashMap<>();
    for (Tuple t : tuples) {
      UUID customerId = t.get("customer_id", UUID.class);
      var triage = triageByCustomer.computeIfAbsent(customerId, triageService::triageFor);
      var buckets =
          new Buckets(
              toBigDecimal(t.get("bucket_current")),
              toBigDecimal(t.get("bucket_d30")),
              toBigDecimal(t.get("bucket_d60")),
              toBigDecimal(t.get("bucket_d90plus")));
      LastActivity lastActivity = toLastActivity(t);
      rows.add(
          new DebtorResponse(
              customerId,
              t.get("customer_name", String.class),
              toBigDecimal(t.get("outstanding_total")),
              t.get("currency", String.class),
              toInt(t.get("invoice_count")),
              toInt(t.get("oldest_days_overdue")),
              buckets,
              triage.signals(),
              triage.signalDetails(),
              Boolean.TRUE.equals(t.get("collections_exempt", Boolean.class)),
              lastActivity));
    }
    return rows;
  }

  private List<OutstandingInvoice> queryOutstandingInvoices(UUID customerId) {
    var query = entityManager.createNativeQuery(OUTSTANDING_INVOICES_SQL, Tuple.class);
    query.setParameter("customerId", customerId.toString());
    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    var rows = new ArrayList<OutstandingInvoice>(tuples.size());
    for (Tuple t : tuples) {
      rows.add(
          new OutstandingInvoice(
              t.get("invoice_id", UUID.class),
              t.get("invoice_number", String.class),
              toBigDecimal(t.get("total")),
              toBigDecimal(t.get("balance_due")),
              t.get("currency", String.class),
              toLocalDate(t.get("due_date")),
              toInt(t.get("days_overdue"))));
    }
    return rows;
  }

  private ActivityResponse toActivity(CollectionActivity a) {
    return new ActivityResponse(
        a.getId(),
        a.getInvoiceId(),
        a.getStage().name(),
        a.getStatus().name(),
        a.getReason(),
        a.getGateId(),
        a.getDaysOverdueAtAction(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }

  private static LastActivity toLastActivity(Tuple t) {
    String stage = t.get("last_stage", String.class);
    String status = t.get("last_status", String.class);
    Instant at = toInstant(t.get("last_at"));
    if (stage == null && status == null && at == null) {
      return null;
    }
    return new LastActivity(stage, status, at);
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    return new BigDecimal(value.toString());
  }

  private static BigDecimal money(Object value) {
    return toBigDecimal(value).setScale(2, RoundingMode.HALF_UP);
  }

  private static int toInt(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private static LocalDate toLocalDate(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate ld) {
      return ld;
    }
    if (value instanceof Date d) {
      return d.toLocalDate();
    }
    return LocalDate.parse(value.toString());
  }

  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof OffsetDateTime odt) {
      return odt.toInstant();
    }
    if (value instanceof Timestamp ts) {
      return ts.toInstant();
    }
    return Instant.parse(value.toString());
  }

  // ── DTOs (§4.1 field spellings are contractual — 591B binds to these) ────────

  public record DebtorResponse(
      UUID clientId,
      String clientName,
      BigDecimal outstandingTotal,
      String currency,
      int invoiceCount,
      int oldestDaysOverdue,
      Buckets buckets,
      List<String> signals,
      Map<String, String> signalDetails,
      boolean collectionsExempt,
      LastActivity lastActivity) {}

  public record Buckets(BigDecimal current, BigDecimal d30, BigDecimal d60, BigDecimal d90plus) {}

  public record LastActivity(String stage, String status, Instant at) {}

  public record DebtorDetailResponse(
      UUID clientId,
      String clientName,
      boolean collectionsExempt,
      List<OutstandingInvoice> outstandingInvoices,
      Page<ActivityResponse> activities) {}

  /**
   * One outstanding fee note: {@code total} is the gross; {@code balanceDue} what is still owed
   * ({@code total - amountPaid}, Phase 88 622D).
   */
  public record OutstandingInvoice(
      UUID invoiceId,
      String invoiceNumber,
      BigDecimal total,
      BigDecimal balanceDue,
      String currency,
      LocalDate dueDate,
      int daysOverdue) {}

  public record ActivityResponse(
      UUID id,
      UUID invoiceId,
      String stage,
      String status,
      String reason,
      UUID gateId,
      int daysOverdueAtAction,
      Instant createdAt,
      Instant updatedAt) {}
}
