package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalAcceptanceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustTransactionView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Assembles the per-portal-contact activity bundle used by the weekly digest email (Epic 498B,
 * Phase 68). Queries the portal read-model repos exclusively — no firm-side cross-schema JPA — per
 * ADR-253.
 *
 * <p>The assembler returns {@code null} when every section is empty (no invoices, acceptances,
 * requests, trust activity, or upcoming deadline). Callers treat {@code null} as a signal to
 * suppress the digest for that contact.
 *
 * <p>The {@code lookbackDays} parameter is interpreted as "since {@code now - lookbackDays}" for
 * invoice/acceptance/request/trust sections, and as "upcoming in the next {@code lookbackDays}" for
 * the deadline section.
 */
@Component
public class PortalDigestContentAssembler {

  private static final Logger log = LoggerFactory.getLogger(PortalDigestContentAssembler.class);

  /**
   * G89 W2-c2: client-facing fee-note status labels for the digest's "Recent fee notes" rows. Same
   * copy as the portal fee-note badge ({@code portal/components/invoice-status-badge.tsx}
   * INVOICE_STATUS_LABELS) the digest links to, so the client reads one vocabulary.
   */
  private static final Map<String, String> INVOICE_STATUS_LABELS =
      Map.of(
          "DRAFT", "Draft",
          "APPROVED", "Approved",
          "SENT", "Sent",
          "PARTIALLY_PAID", "Partially paid",
          "PAID", "Paid",
          "VOID", "Void");

  /**
   * One "Recent fee notes" row as the digest prints it: the status as its client-facing label and
   * the total in the house money format ({@code "R 5 335,00"}, as the trust-activity email), never
   * the raw enum or {@code "ZAR 5335.00"}.
   */
  public record DigestInvoiceLine(String invoiceNumber, String statusLabel, String totalFormatted) {

    static DigestInvoiceLine of(PortalInvoiceView invoice) {
      return new DigestInvoiceLine(
          invoice.invoiceNumber(),
          invoiceStatusLabel(invoice.status()),
          digestTotal(invoice.total(), invoice.currency()));
    }

    /** The fee-note total as the digest prints it. */
    static String digestTotal(BigDecimal total, String currency) {
      return PortalEmailNotificationChannel.formatAmount(total, currency);
    }
  }

  private final PortalContactRepository portalContactRepository;
  private final PortalReadModelRepository portalReadModelRepository;
  private final PortalTrustReadModelRepository portalTrustReadModelRepository;
  private final PortalDeadlineViewRepository portalDeadlineViewRepository;
  private final Clock clock;

  public PortalDigestContentAssembler(
      PortalContactRepository portalContactRepository,
      PortalReadModelRepository portalReadModelRepository,
      PortalTrustReadModelRepository portalTrustReadModelRepository,
      PortalDeadlineViewRepository portalDeadlineViewRepository,
      Clock clock) {
    this.portalContactRepository = portalContactRepository;
    this.portalReadModelRepository = portalReadModelRepository;
    this.portalTrustReadModelRepository = portalTrustReadModelRepository;
    this.portalDeadlineViewRepository = portalDeadlineViewRepository;
    this.clock = clock;
  }

  /**
   * Assembles the activity bundle for the given portal contact over the preceding {@code
   * lookbackDays}. Returns {@code null} when every queried section is empty.
   */
  public Map<String, Object> assemble(UUID portalContactId, int lookbackDays) {
    if (portalContactId == null) {
      return null;
    }

    var contactOpt = portalContactRepository.findById(portalContactId);
    if (contactOpt.isEmpty()) {
      log.debug("No portal contact found for id={}, skipping digest assembly", portalContactId);
      return null;
    }
    PortalContact contact = contactOpt.get();
    UUID customerId = contact.getCustomerId();
    String orgId = contact.getOrgId();

    Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
    LocalDate today = LocalDate.now(clock);
    LocalDate upcomingTo = today.plusDays(lookbackDays);

    // ── Recent invoices (filter in-memory by issueDate >= since-day) ──
    List<PortalInvoiceView> allInvoices =
        portalReadModelRepository.findInvoicesByCustomer(orgId, customerId);
    LocalDate sinceDay = today.minusDays(lookbackDays);
    List<PortalInvoiceView> recentInvoices =
        allInvoices.stream()
            .filter(inv -> inv.issueDate() != null && !inv.issueDate().isBefore(sinceDay))
            .toList();

    // ── Pending acceptances (by contact id) ──
    List<PortalAcceptanceView> pendingAcceptances =
        portalReadModelRepository.findPendingAcceptancesByContactId(portalContactId);

    // ── Pending information requests (SENT or IN_PROGRESS) ──
    List<PortalRequestView> allRequests =
        portalReadModelRepository.findRequestsByPortalContactId(portalContactId);
    List<PortalRequestView> pendingRequests =
        allRequests.stream()
            .filter(req -> "SENT".equals(req.status()) || "IN_PROGRESS".equals(req.status()))
            .toList();

    // ── Recent trust activity (aggregate across all matters with balance rows) ──
    List<PortalTrustTransactionView> recentTrustTransactions = new ArrayList<>();
    var trustBalances = portalTrustReadModelRepository.findBalancesByCustomer(customerId);
    for (var balance : trustBalances) {
      var txns =
          portalTrustReadModelRepository.findTransactions(
              customerId, balance.matterId(), since, null, 20, 0);
      recentTrustTransactions.addAll(txns);
    }

    // ── Upcoming deadlines (today..today+lookback) ──
    List<PortalDeadlineView> upcomingDeadlines =
        portalDeadlineViewRepository.findByCustomer(customerId, today, upcomingTo, null, today);

    boolean allEmpty =
        recentInvoices.isEmpty()
            && pendingAcceptances.isEmpty()
            && pendingRequests.isEmpty()
            && recentTrustTransactions.isEmpty()
            && upcomingDeadlines.isEmpty();
    if (allEmpty) {
      return null;
    }

    Map<String, Object> bundle = new HashMap<>();
    bundle.put("contactName", contact.getDisplayName());
    bundle.put("lookbackDays", lookbackDays);
    bundle.put("recentInvoices", recentInvoices.stream().map(DigestInvoiceLine::of).toList());
    bundle.put("pendingAcceptances", pendingAcceptances);
    bundle.put("pendingRequests", pendingRequests);
    bundle.put("recentTrustTransactions", recentTrustTransactions);
    bundle.put("upcomingDeadlines", upcomingDeadlines);
    bundle.put("hasInvoices", !recentInvoices.isEmpty());
    bundle.put("hasAcceptances", !pendingAcceptances.isEmpty());
    bundle.put("hasRequests", !pendingRequests.isEmpty());
    bundle.put("hasTrustActivity", !recentTrustTransactions.isEmpty());
    bundle.put("hasDeadlines", !upcomingDeadlines.isEmpty());
    return bundle;
  }

  /**
   * The client-facing label for a fee-note status; an unmapped value falls back to sentence case
   * ({@code "SOME_STATUS"} → {@code "Some status"}), never the raw enum. Null/blank → empty.
   */
  static String invoiceStatusLabel(String status) {
    if (status == null || status.isBlank()) {
      return "";
    }
    String label = INVOICE_STATUS_LABELS.get(status);
    if (label != null) {
      return label;
    }
    String text = status.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
