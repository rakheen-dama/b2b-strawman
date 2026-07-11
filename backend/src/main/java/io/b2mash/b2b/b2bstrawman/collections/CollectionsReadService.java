package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
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
 * {@code tenant_id} predicate. The debtor book aggregates outstanding ({@code status = 'SENT'})
 * invoices per {@code (customer, currency)} pair — invoice currency is per-invoice and a customer
 * with mixed-currency invoices surfaces as one row per currency, each carrying a single {@code
 * currency} — using the 4-bucket §4.1 aging split ({@code current} ≤ 0 days, {@code d30} = 1–30,
 * {@code d60} = 31–60, {@code d90plus} = 61+). Unlike the collections SCAN, the debtor book
 * INCLUDES {@code collections_exempt} customers (surfacing the flag) and INCLUDES not-yet-overdue
 * SENT invoices (they land in the {@code current} bucket).
 *
 * <p>The bucket CASE is kept self-contained here; 593A extracts a shared {@code AgingBuckets}
 * helper. {@code signals} is populated by {@link CollectionsTriageService} (wired 592A.4) — one
 * deterministic-signal computation per debtor-book row.
 */
@Service
public class CollectionsReadService {

  private static final String DEBTOR_BOOK_SQL =
      """
      SELECT
          i.customer_id AS customer_id,
          MAX(c.name) AS customer_name,
          bool_or(c.collections_exempt) AS collections_exempt,
          SUM(i.total) AS outstanding_total,
          i.currency AS currency,
          COUNT(*) AS invoice_count,
          MAX(CURRENT_DATE - i.due_date) AS oldest_days_overdue,
          COALESCE(SUM(i.total) FILTER (WHERE CURRENT_DATE - i.due_date <= 0), 0) AS bucket_current,
          COALESCE(SUM(i.total) FILTER (WHERE CURRENT_DATE - i.due_date BETWEEN 1 AND 30), 0)
              AS bucket_d30,
          COALESCE(SUM(i.total) FILTER (WHERE CURRENT_DATE - i.due_date BETWEEN 31 AND 60), 0)
              AS bucket_d60,
          COALESCE(SUM(i.total) FILTER (WHERE CURRENT_DATE - i.due_date >= 61), 0)
              AS bucket_d90plus,
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
      WHERE i.status = 'SENT'
        AND i.due_date IS NOT NULL
      GROUP BY i.customer_id, i.currency
      ORDER BY outstanding_total DESC, i.customer_id, i.currency
      LIMIT :limit OFFSET :offset
      """;

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
          WHERE i.status = 'SENT'
            AND i.due_date IS NOT NULL
          GROUP BY i.customer_id, i.currency
      ) grouped
      """;

  private static final String OUTSTANDING_INVOICES_SQL =
      """
      SELECT
          i.id AS invoice_id,
          i.invoice_number AS invoice_number,
          i.total AS total,
          i.currency AS currency,
          i.due_date AS due_date,
          (CURRENT_DATE - i.due_date) AS days_overdue
      FROM invoices i
      WHERE i.customer_id = CAST(:customerId AS UUID)
        AND i.status = 'SENT'
        AND i.due_date IS NOT NULL
      ORDER BY i.due_date
      """;

  private final EntityManager entityManager;
  private final CustomerRepository customerRepository;
  private final CollectionActivityRepository activityRepository;
  private final InvoiceRepository invoiceRepository;
  private final CollectionsTriageService triageService;

  public CollectionsReadService(
      EntityManager entityManager,
      CustomerRepository customerRepository,
      CollectionActivityRepository activityRepository,
      InvoiceRepository invoiceRepository,
      CollectionsTriageService triageService) {
    this.entityManager = entityManager;
    this.customerRepository = customerRepository;
    this.activityRepository = activityRepository;
    this.invoiceRepository = invoiceRepository;
    this.triageService = triageService;
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
    // currency) row. Bounded by page size; 593A extracts a set-based batch API for the seam.
    Map<UUID, List<String>> signalsByCustomer = new HashMap<>();
    for (Tuple t : tuples) {
      UUID customerId = t.get("customer_id", UUID.class);
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
              signalsByCustomer.computeIfAbsent(customerId, this::signalsFor),
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

  /**
   * Triage signals for a customer (§3.4) — the deterministic four ({@code DRIFTING}, {@code
   * SERIAL_LATE}, {@code GONE_QUIET}, {@code ESCALATED}) plus any advisor-contributed signals.
   * Delegated to {@link CollectionsTriageService}, which owns the signal engine (592A.4). Invoked
   * once per DISTINCT customer on the page (memoised across a customer's per-currency rows; bounded
   * by page size). 593A extracts a set-based batch API for this seam.
   */
  private List<String> signalsFor(UUID customerId) {
    return triageService.signalsFor(customerId);
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
      UUID customerId,
      String customerName,
      BigDecimal outstandingTotal,
      String currency,
      int invoiceCount,
      int oldestDaysOverdue,
      Buckets buckets,
      List<String> signals,
      boolean collectionsExempt,
      LastActivity lastActivity) {}

  public record Buckets(BigDecimal current, BigDecimal d30, BigDecimal d60, BigDecimal d90plus) {}

  public record LastActivity(String stage, String status, Instant at) {}

  public record DebtorDetailResponse(
      UUID customerId,
      String customerName,
      boolean collectionsExempt,
      List<OutstandingInvoice> outstandingInvoices,
      Page<ActivityResponse> activities) {}

  public record OutstandingInvoice(
      UUID invoiceId,
      String invoiceNumber,
      BigDecimal total,
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
