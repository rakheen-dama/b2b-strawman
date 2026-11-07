package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fee-note basis of a matter's billed figures (Phase 89 G89-F16, founder decision D9): what the
 * matter's <em>issued</em> fee notes billed and what is still owing on them. The Statement of
 * Account summary and the closure letter both read it, so the two documents can never disagree on
 * what was billed or owed.
 *
 * <p>A fee note counts when its status is SENT, PARTIALLY_PAID or PAID ({@link #ISSUED_STATUSES});
 * DRAFT, APPROVED and VOID notes never count. A note is "on the matter" when any of its lines
 * carries the matter ({@link InvoiceRepository#findByProjectId}); all its lines count.
 */
@Component
public class MatterFeeNoteTotals {

  /** Issued fee notes — exactly SENT, PARTIALLY_PAID and PAID (Phase 88 622D status set). */
  public static final Set<InvoiceStatus> ISSUED_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final PaymentEventRepository paymentEventRepository;

  public MatterFeeNoteTotals(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      PaymentEventRepository paymentEventRepository) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.paymentEventRepository = paymentEventRepository;
  }

  /**
   * Loads the matter's issued fee notes once — their line split and their COMPLETED payments — so a
   * caller can ask several period questions ({@link Ledger#issuedBetween}) without re-querying.
   */
  @Transactional(readOnly = true)
  public Ledger load(UUID projectId) {
    List<Invoice> issued =
        invoiceRepository.findByProjectId(projectId).stream()
            .filter(note -> ISSUED_STATUSES.contains(note.getStatus()))
            .toList();
    if (issued.isEmpty()) {
      return new Ledger(List.of());
    }
    Map<UUID, List<PaymentEvent>> paymentsByNote =
        paymentEventRepository
            .findByInvoiceIdInOrderByCreatedAtDesc(issued.stream().map(Invoice::getId).toList())
            .stream()
            .filter(p -> p.getStatus() == PaymentEventStatus.COMPLETED)
            .collect(Collectors.groupingBy(PaymentEvent::getInvoiceId));
    var notes = new ArrayList<NoteFigures>(issued.size());
    for (Invoice note : issued) {
      notes.add(
          figures(
              note,
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(note.getId()),
              paymentsByNote.getOrDefault(note.getId(), List.of())));
    }
    return new Ledger(List.copyOf(notes));
  }

  /** All the matter's issued fee notes, paid as of now — outstanding = Σ balance due. */
  @Transactional(readOnly = true)
  public Totals current(UUID projectId) {
    return load(projectId).issuedBetween(null, null);
  }

  /**
   * Splits one note: fees and disbursements excl. VAT from its lines, VAT once from the note
   * (covers legacy notes with a manual note-level tax and no per-line tax). Tax-inclusive per-line
   * notes carry VAT inside each line amount ({@link Invoice#recalculateTotals} sets total ==
   * subtotal only for them; the note row has no tax-inclusive flag).
   */
  private static NoteFigures figures(
      Invoice note, List<InvoiceLine> lines, List<PaymentEvent> completedPayments) {
    BigDecimal noteVat = nz(note.getTaxAmount());
    boolean inclusive =
        noteVat.signum() > 0 && nz(note.getTotal()).compareTo(nz(note.getSubtotal())) == 0;
    BigDecimal fees = BigDecimal.ZERO;
    BigDecimal disbursements = BigDecimal.ZERO;
    BigDecimal disbursementsInclVat = BigDecimal.ZERO;
    for (InvoiceLine line : lines) {
      BigDecimal lineTax = nz(line.getTaxAmount());
      BigDecimal exclVat = nz(line.getAmount()).subtract(inclusive ? lineTax : BigDecimal.ZERO);
      if (line.getLineType() == InvoiceLineType.DISBURSEMENT) {
        disbursements = disbursements.add(exclVat);
        disbursementsInclVat = disbursementsInclVat.add(exclVat).add(lineTax);
      } else {
        fees = fees.add(exclVat); // every other line type (TIME, TARIFF, FIXED_FEE, …) is a fee
      }
    }
    var payments =
        completedPayments.stream()
            .map(p -> new Payment(p.getCreatedAt(), nz(p.getAmount())))
            .toList();
    return new NoteFigures(
        note.getIssueDate(),
        nz(note.getTotal()),
        fees,
        noteVat,
        disbursements,
        disbursementsInclVat,
        nz(note.getAmountPaid()),
        note.getPaidAt(),
        payments);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  /**
   * Billed figures for a set of issued fee notes. {@code feesExclVat + vat + disbursementsExclVat =
   * total}; {@code feesInclVat() + disbursementsInclVat = total}; {@code total − paid =
   * outstanding}.
   */
  public record Totals(
      BigDecimal feesExclVat,
      BigDecimal vat,
      BigDecimal disbursementsExclVat,
      BigDecimal disbursementsInclVat,
      BigDecimal total,
      BigDecimal paid,
      BigDecimal outstanding) {

    static final Totals ZERO =
        new Totals(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    /** Fees incl. VAT: the notes' totals less their disbursement lines incl. VAT. */
    public BigDecimal feesInclVat() {
      return total.subtract(disbursementsInclVat);
    }
  }

  /** One COMPLETED payment event: when it was recorded and its real amount. */
  record Payment(Instant at, BigDecimal amount) {}

  /** One issued fee note's figures. */
  record NoteFigures(
      LocalDate issueDate,
      BigDecimal total,
      BigDecimal feesExclVat,
      BigDecimal vat,
      BigDecimal disbursementsExclVat,
      BigDecimal disbursementsInclVat,
      BigDecimal amountPaid,
      Instant paidAt,
      List<Payment> payments) {

    /**
     * Paid on this note by the end of {@code asOf} (SA business day, ADR-358): Σ COMPLETED payments
     * recorded before the next day starts, capped at the note total (an external overpayment is
     * recorded at its real amount while {@code amount_paid} is capped — ADR-347). {@code asOf} null
     * means now: the note's {@code amount_paid}, so outstanding equals its balance due. A note
     * settled without payment events (demo seeders / fixtures — {@code Invoice.markPaid}) counts
     * its {@code amount_paid} from {@code paidAt}.
     */
    BigDecimal paidAsOf(LocalDate asOf) {
      if (asOf == null) {
        return amountPaid;
      }
      Instant cutoff = asOf.plusDays(1).atStartOfDay(KaziTime.ZONE).toInstant();
      if (payments.isEmpty()) {
        return paidAt != null && paidAt.isBefore(cutoff) ? amountPaid : BigDecimal.ZERO;
      }
      return payments.stream()
          .filter(p -> p.at() != null && p.at().isBefore(cutoff))
          .map(Payment::amount)
          .reduce(BigDecimal.ZERO, BigDecimal::add)
          .min(total);
    }

    boolean issuedBetween(LocalDate from, LocalDate to) {
      if (issueDate == null) {
        return from == null && to == null;
      }
      return (from == null || !issueDate.isBefore(from)) && (to == null || !issueDate.isAfter(to));
    }
  }

  /** A matter's issued fee notes, loaded once by {@link #load}. */
  public record Ledger(List<NoteFigures> notes) {

    /**
     * Totals of the notes issued in {@code [from, to]} (either bound null = open), paid as of
     * {@code to} (null = now).
     */
    public Totals issuedBetween(LocalDate from, LocalDate to) {
      var totals = Totals.ZERO;
      for (NoteFigures note : notes) {
        if (!note.issuedBetween(from, to)) {
          continue;
        }
        BigDecimal paid = note.paidAsOf(to);
        totals =
            new Totals(
                totals.feesExclVat().add(note.feesExclVat()),
                totals.vat().add(note.vat()),
                totals.disbursementsExclVat().add(note.disbursementsExclVat()),
                totals.disbursementsInclVat().add(note.disbursementsInclVat()),
                totals.total().add(note.total()),
                totals.paid().add(paid),
                totals.outstanding().add(note.total().subtract(paid)));
      }
      return totals;
    }

    /** Still owing at the end of {@code asOf} on every note issued by then. */
    public BigDecimal outstandingAsOf(LocalDate asOf) {
      return issuedBetween(null, asOf).outstanding();
    }
  }
}
