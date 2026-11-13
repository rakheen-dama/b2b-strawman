package io.b2mash.b2b.b2bstrawman.report;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.report.ReportController.BillingTrendPoint;
import io.b2mash.b2b.b2bstrawman.report.ReportController.BillingTrendResponse;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fees billed vs collected per South African calendar month (Phase 90 643B, ADR-372, arch §15.3.8),
 * org default currency only, oldest first, zero-filled.
 *
 * <ul>
 *   <li><b>Billed</b> = Σ {@code invoices.total} (VAT inclusive) by the month of {@code
 *       issue_date}, for APPROVED, SENT, PARTIALLY_PAID and PAID fee notes. APPROVED-unsent counts
 *       (R3) — unlike the weekly cash digest, which counts from SENT ({@code CashDigestService} is
 *       not changed; ADR-372 records the difference).
 *   <li><b>Collected</b> = Σ COMPLETED {@code payment_events.amount} by the SA month of {@code
 *       created_at} — part payments and trust fee transfers included; a reversal deletes its event.
 *       A payment captured late lands in its capture month (OQ #9).
 * </ul>
 *
 * Other currencies are excluded, never converted. Not cached: no invalidation hook exists on
 * fee-note or payment writes (as {@code outstanding-summary}). Read-only — no write, no migration.
 */
@Service
public class BillingTrendService {

  /** Bounds of the {@code months} window (400 outside — not clamped). */
  static final int MIN_MONTHS = 1;

  static final int MAX_MONTHS = 24;

  /** §15.3.8 verbatim. Statuses = {@code InvoiceStatus} minus DRAFT and VOID. */
  static final String BILLED_SQL =
      """
      SELECT CAST(date_trunc('month', i.issue_date) AS date) AS month_start,
             COALESCE(SUM(i.total), 0) AS billed, COUNT(*) AS billed_count
      FROM invoices i
      WHERE i.currency = :currency
        AND i.status IN ('APPROVED', 'SENT', 'PARTIALLY_PAID', 'PAID')
        AND i.issue_date >= :fromDate AND i.issue_date <= :today
      GROUP BY 1
      """;

  /** §15.3.8 verbatim; {@code 'Africa/Johannesburg'} = {@link KaziTime#ZONE_ID}. */
  static final String COLLECTED_SQL =
      """
      SELECT CAST(date_trunc('month', pe.created_at AT TIME ZONE 'Africa/Johannesburg') AS date) AS month_start,
             COALESCE(SUM(pe.amount), 0) AS collected, COUNT(*) AS collected_count
      FROM payment_events pe
      WHERE pe.status = 'COMPLETED'
        AND pe.currency = :currency
        AND pe.created_at >= :fromInstant AND pe.created_at < :toInstantExclusive
      GROUP BY 1
      """;

  private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final Clock clock;
  private final EntityManager entityManager;
  private final OrgSettingsRepository orgSettingsRepository;

  public BillingTrendService(
      Clock clock, EntityManager entityManager, OrgSettingsRepository orgSettingsRepository) {
    this.clock = clock;
    this.entityManager = entityManager;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional(readOnly = true)
  public BillingTrendResponse getBillingTrend(int months) {
    if (months < MIN_MONTHS || months > MAX_MONTHS) {
      throw new InvalidStateException("Invalid months", "'months' must be between 1 and 24");
    }
    LocalDate today = LocalDate.now(clock);
    YearMonth current = YearMonth.from(today);
    YearMonth first = current.minusMonths(months - 1L);
    LocalDate fromDate = first.atDay(1);
    Instant fromInstant = fromDate.atStartOfDay(KaziTime.ZONE).toInstant();
    Instant toInstantExclusive = today.plusDays(1).atStartOfDay(KaziTime.ZONE).toInstant();
    String currency =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getDefaultCurrency)
            .orElse(OrgSettingsService.DEFAULT_CURRENCY);

    Query billedQuery = entityManager.createNativeQuery(BILLED_SQL, Tuple.class);
    billedQuery.setParameter("currency", currency);
    billedQuery.setParameter("fromDate", fromDate);
    billedQuery.setParameter("today", today);
    Map<YearMonth, Tuple> billed = byMonth(billedQuery);

    Query collectedQuery = entityManager.createNativeQuery(COLLECTED_SQL, Tuple.class);
    collectedQuery.setParameter("currency", currency);
    collectedQuery.setParameter("fromInstant", fromInstant);
    collectedQuery.setParameter("toInstantExclusive", toInstantExclusive);
    Map<YearMonth, Tuple> collected = byMonth(collectedQuery);

    List<BillingTrendPoint> points = new ArrayList<>(months);
    for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
      Tuple b = billed.get(month);
      Tuple c = collected.get(month);
      points.add(
          new BillingTrendPoint(
              month.toString(),
              b == null ? ZERO_MONEY : money(b.get("billed")),
              b == null ? 0 : toInt(b.get("billed_count")),
              c == null ? ZERO_MONEY : money(c.get("collected")),
              c == null ? 0 : toInt(c.get("collected_count")),
              month.equals(current)));
    }
    return new BillingTrendResponse(currency, first.toString(), current.toString(), today, points);
  }

  private static Map<YearMonth, Tuple> byMonth(Query query) {
    @SuppressWarnings("unchecked")
    List<Tuple> rows = query.getResultList();
    Map<YearMonth, Tuple> byMonth = new HashMap<>();
    for (Tuple row : rows) {
      byMonth.put(YearMonth.from(toLocalDate(row.get("month_start"))), row);
    }
    return byMonth;
  }

  private static BigDecimal money(Object value) {
    BigDecimal amount =
        switch (value) {
          case null -> BigDecimal.ZERO;
          case BigDecimal bd -> bd;
          default -> new BigDecimal(value.toString());
        };
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private static int toInt(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof LocalDate ld) {
      return ld;
    }
    if (value instanceof Date d) {
      return d.toLocalDate();
    }
    return LocalDate.parse(value.toString());
  }
}
