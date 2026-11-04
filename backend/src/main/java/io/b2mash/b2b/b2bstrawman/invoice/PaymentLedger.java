package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single source of truth for a fee note's payment ledger (Phase 89 632B, ADR-364): the order of
 * its payments and each payment's running {@code balanceAfter}. The fee-note template context
 * ({@code invoice.payments[]}, {@code InvoiceContextBuilder}), the receipt's own {@code payment.*}
 * row ({@link PaymentReceiptService}) and the portal payments list all read it, so the receipt's
 * payment line can never disagree with the payments table printed beside it.
 */
public final class PaymentLedger {

  /**
   * Ledger order: {@code created_at} ascending, tie-break by id (deterministic for same-instant
   * events); nulls first so an unsaved event never throws.
   */
  public static final Comparator<PaymentEvent> ORDER =
      Comparator.comparing(
              PaymentEvent::getCreatedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
          .thenComparing(PaymentEvent::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

  private PaymentLedger() {}

  /**
   * One template-context row per COMPLETED event, in {@link #ORDER}: {@code {id, date, methodLabel,
   * amount, reference, balanceAfter}}. {@code date} is the SA business date (ADR-358); {@code
   * balanceAfter} = {@code max(0, total − Σ amounts up to and including this event)} — an external
   * overpayment is recorded at its real amount while {@code amount_paid} is capped (ADR-347, D15).
   * Non-COMPLETED events are ignored.
   */
  public static List<Map<String, Object>> rows(
      Collection<PaymentEvent> events, BigDecimal invoiceTotal) {
    BigDecimal total = invoiceTotal != null ? invoiceTotal : BigDecimal.ZERO;
    BigDecimal paidSoFar = BigDecimal.ZERO;
    var rows = new ArrayList<Map<String, Object>>();
    for (var event :
        events.stream()
            .filter(e -> e.getStatus() == PaymentEventStatus.COMPLETED)
            .sorted(ORDER)
            .toList()) {
      paidSoFar = paidSoFar.add(event.getAmount());
      var row = new LinkedHashMap<String, Object>();
      row.put("id", event.getId());
      row.put(
          "date",
          event.getCreatedAt() != null
              ? LocalDate.ofInstant(event.getCreatedAt(), KaziTime.ZONE).toString()
              : null);
      row.put("methodLabel", PaymentMethod.labelOf(event.getPaymentMethod()));
      row.put("amount", event.getAmount());
      row.put("reference", event.getPaymentReference());
      row.put("balanceAfter", total.subtract(paidSoFar).max(BigDecimal.ZERO));
      rows.add(row);
    }
    return rows;
  }
}
