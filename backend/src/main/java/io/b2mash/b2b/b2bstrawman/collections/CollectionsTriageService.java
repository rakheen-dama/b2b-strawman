package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic debtor-triage engine (Phase 83, §3.4, ADR-327). Computes per-customer collections
 * signals from data already on hand — <strong>no AI, no persistence</strong>. Signals are
 * arithmetic (unit-testable, free, available with AI disabled); the AI digest and reminder drafter
 * may <em>narrate</em> them but never invent them.
 *
 * <p>Signal derivations (§3.4):
 *
 * <ul>
 *   <li>{@code DRIFTING} — the oldest currently-overdue invoice exceeds the customer's median
 *       days-to-pay by &gt; 14 days.
 *   <li>{@code SERIAL_LATE} — median days-to-pay &gt; 30 (a reliable-if-slow payer); the
 *       median-relative {@code DRIFTING} threshold is what suppresses nagging such a customer.
 *   <li>{@code GONE_QUIET} — an outstanding invoice has ≥ 2 {@code SENT} reminders with no payment
 *       since the earliest of them.
 *   <li>{@code ESCALATED} — a {@code FLAGGED} {@code ESCALATION} activity exists on an outstanding
 *       invoice.
 *   <li>advisor-contributed signals (e.g. {@code TRUST_FUNDS_AVAILABLE}) merged from every {@link
 *       CollectionsAdvisor} bean (§6.5).
 * </ul>
 *
 * <p>The median definition is kept IDENTICAL to {@code
 * CollectionReminderSkill.appendBillingHistory} (upper median of {@code paid_at - due_date} in UTC
 * days over PAID invoices) so the prompt's "Median days-to-pay" and the {@code DRIFTING} signal
 * never disagree. No PAID history → median undefined → neither {@code DRIFTING} nor {@code
 * SERIAL_LATE}.
 *
 * <p>Pure reads over per-tenant tables via {@code search_path} — no new isolation surface.
 */
@Service
public class CollectionsTriageService {

  private static final Logger log = LoggerFactory.getLogger(CollectionsTriageService.class);

  private static final int DRIFTING_MARGIN_DAYS = 14;
  private static final long SERIAL_LATE_THRESHOLD_DAYS = 30;

  private final InvoiceRepository invoiceRepository;
  private final CollectionActivityRepository activityRepository;
  private final List<CollectionsAdvisor> advisors;

  public CollectionsTriageService(
      InvoiceRepository invoiceRepository,
      CollectionActivityRepository activityRepository,
      List<CollectionsAdvisor> advisors) {
    this.invoiceRepository = invoiceRepository;
    this.activityRepository = activityRepository;
    this.advisors = List.copyOf(advisors);
  }

  /**
   * Full triage result for one customer (592B): the §3.4 signal names (deterministic four in a
   * stable order followed by advisor-contributed signal names, de-duplicated) plus the
   * advisor-contributed detail strings keyed by signal (e.g. {@code {"TRUST_FUNDS_AVAILABLE": "R 84
   * 200,00 held in trust"}}). One computation per customer — the debtors page memoises this per
   * distinct customer per page so signals and details are derived from a SINGLE advisor pass.
   */
  public record TriageResult(List<String> signals, Map<String, String> signalDetails) {}

  /**
   * Full triage result for the debtors API: the deterministic four ({@code DRIFTING}, {@code
   * SERIAL_LATE}, {@code GONE_QUIET}, {@code ESCALATED}) followed by advisor-contributed signal
   * names (de-duplicated) plus the advisor detail strings keyed by signal. Consumed per debtor-book
   * row.
   */
  @Transactional(readOnly = true)
  public TriageResult triageFor(UUID customerId) {
    List<Invoice> invoices = invoiceRepository.findByCustomerId(customerId);
    // UTC to match medianDaysToPay, which normalizes paid_at to UTC — otherwise, around midnight,
    // the JVM default zone could disagree with the median and flip DRIFTING across hosts.
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    Long median = medianDaysToPay(invoices);
    long oldestCurrentDaysOverdue = oldestCurrentDaysOverdue(invoices, today);
    List<Invoice> outstanding =
        invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.SENT).toList();

    List<String> signals = new ArrayList<>();
    if (median != null && oldestCurrentDaysOverdue > median + DRIFTING_MARGIN_DAYS) {
      signals.add("DRIFTING");
    }
    if (median != null && median > SERIAL_LATE_THRESHOLD_DAYS) {
      signals.add("SERIAL_LATE");
    }
    if (isGoneQuiet(invoices, outstanding)) {
      signals.add("GONE_QUIET");
    }
    if (isEscalated(outstanding)) {
      signals.add("ESCALATED");
    }
    Map<String, String> details = new LinkedHashMap<>();
    for (CollectionsAdvisor.CollectionsAdvice advice : adviceFor(customerId)) {
      if (!signals.contains(advice.signal())) {
        signals.add(advice.signal());
      }
      if (advice.detail() != null && !advice.detail().isBlank()) {
        details.putIfAbsent(advice.signal(), advice.detail());
      }
    }
    return new TriageResult(List.copyOf(signals), Map.copyOf(details));
  }

  /**
   * The signal names for the debtors API: the deterministic four (in a stable order) followed by
   * advisor-contributed signal names, de-duplicated. Delegates to {@link #triageFor(UUID)} (592B)
   * and returns just the signal list — kept for callers that don't need advisor detail strings.
   */
  @Transactional(readOnly = true)
  public List<String> signalsFor(UUID customerId) {
    return triageFor(customerId).signals();
  }

  /**
   * Merged {@link CollectionsAdvisor} advice (signal + detail) across every advisor bean — for
   * drafting context (and the 593B cash digest later). Each advisor is responsible for its own
   * fail-open behaviour (ADR-329), but as a hard boundary this method also isolates each advisor
   * call: a thrown advisor is logged and skipped, never allowed to escape.
   *
   * <p>This isolation matters because {@code adviceFor} is called from {@code
   * AiReminderComposer.compose} inside the collections scan's per-candidate {@code REQUIRES_NEW}
   * transaction. This method's {@code @Transactional} joins that transaction
   * (PROPAGATION_REQUIRED), so if an advisor exception escaped {@code adviceFor} the transaction
   * interceptor would mark the shared candidate transaction rollback-only — producing an {@code
   * UnexpectedRollbackException} at commit even though the composer's own catch degrades to no
   * annotations. Catching per-advisor here keeps a broken advisor from ever turning a draftable
   * reminder into {@code SKIPPED(draft_failed)}.
   */
  @Transactional(readOnly = true)
  public List<CollectionsAdvisor.CollectionsAdvice> adviceFor(UUID customerId) {
    List<CollectionsAdvisor.CollectionsAdvice> advice = new ArrayList<>();
    for (CollectionsAdvisor advisor : advisors) {
      try {
        advice.addAll(advisor.adviseFor(customerId));
      } catch (RuntimeException e) {
        log.debug(
            "Advisor {} failed for customer {}: {}",
            advisor.getClass().getSimpleName(),
            customerId,
            e.getMessage());
      }
    }
    return List.copyOf(advice);
  }

  /**
   * Upper median of {@code paid_at - due_date} (UTC days) over PAID invoices with both fields
   * present, or {@code null} when there is no such history. Identical to the reminder skill's
   * median.
   */
  private static Long medianDaysToPay(List<Invoice> invoices) {
    List<Long> daysToPay =
        invoices.stream()
            .filter(i -> i.getStatus() == InvoiceStatus.PAID)
            .filter(i -> i.getPaidAt() != null && i.getDueDate() != null)
            .map(
                i ->
                    ChronoUnit.DAYS.between(
                        i.getDueDate(), i.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate()))
            .sorted()
            .toList();
    if (daysToPay.isEmpty()) {
      return null;
    }
    return daysToPay.get(daysToPay.size() / 2);
  }

  /** Max {@code today - due_date} over outstanding (SENT), past-due invoices; 0 when none. */
  private static long oldestCurrentDaysOverdue(List<Invoice> invoices, LocalDate today) {
    return invoices.stream()
        .filter(i -> i.getStatus() == InvoiceStatus.SENT)
        .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today))
        .mapToLong(i -> ChronoUnit.DAYS.between(i.getDueDate(), today))
        .max()
        .orElse(0);
  }

  /**
   * True when some outstanding invoice has ≥ 2 {@code SENT} reminders and the customer has made no
   * payment since the earliest of those reminder sends ("gone quiet after being chased").
   */
  private boolean isGoneQuiet(List<Invoice> invoices, List<Invoice> outstanding) {
    for (Invoice invoice : outstanding) {
      List<CollectionActivity> sentReminders =
          activityRepository.findByInvoiceIdAndStatus(
              invoice.getId(), CollectionActivityStatus.SENT);
      if (sentReminders.size() < 2) {
        continue;
      }
      Instant earliestSend =
          sentReminders.stream()
              .map(CollectionActivity::getCreatedAt)
              .min(Instant::compareTo)
              .orElse(null);
      if (earliestSend == null) {
        continue;
      }
      boolean paidSince =
          invoices.stream()
              .filter(i -> i.getStatus() == InvoiceStatus.PAID)
              .filter(i -> i.getPaidAt() != null)
              .anyMatch(i -> i.getPaidAt().isAfter(earliestSend));
      if (!paidSince) {
        return true;
      }
    }
    return false;
  }

  /** True when an outstanding invoice carries a {@code FLAGGED} {@code ESCALATION} activity. */
  private boolean isEscalated(List<Invoice> outstanding) {
    for (Invoice invoice : outstanding) {
      boolean flagged =
          activityRepository
              .findByInvoiceIdAndStage(invoice.getId(), CollectionStage.ESCALATION)
              .filter(a -> a.getStatus() == CollectionActivityStatus.FLAGGED)
              .isPresent();
      if (flagged) {
        return true;
      }
    }
    return false;
  }
}
