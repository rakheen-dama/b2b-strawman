package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.FeeLineDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.TrustTxDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Period-bound context builder for the Statement of Account template (Phase 67, Epic 491A,
 * architecture §67.6.1 / ADR-250). Aggregates fees (billable time entries), disbursements, trust
 * activity, payments, and a six-row numeric summary into a flat-dotted variable bundle for the
 * Tiptap variable resolver.
 *
 * <p>Unlike {@link io.b2mash.b2b.b2bstrawman.template.TemplateContextBuilder} implementations this
 * builder is <em>not</em> registered as one — its inputs are period-bound (start/end dates) which
 * the {@code TemplateContextBuilder.buildContext(entityId, memberId)} interface cannot express.
 * {@code StatementService} invokes it directly.
 */
@Component
public class StatementOfAccountContextBuilder {

  private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
  private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final DisbursementService disbursementService;
  private final ClientLedgerService clientLedgerService;
  private final TrustAccountRepository trustAccountRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final MemberNameResolver memberNameResolver;
  private final TemplateContextHelper templateContextHelper;

  public StatementOfAccountContextBuilder(
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      TimeEntryRepository timeEntryRepository,
      DisbursementService disbursementService,
      ClientLedgerService clientLedgerService,
      TrustAccountRepository trustAccountRepository,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      MemberNameResolver memberNameResolver,
      TemplateContextHelper templateContextHelper) {
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.disbursementService = disbursementService;
    this.clientLedgerService = clientLedgerService;
    this.trustAccountRepository = trustAccountRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.memberNameResolver = memberNameResolver;
    this.templateContextHelper = templateContextHelper;
  }

  /**
   * Builds the Statement of Account context map. Returns a flat {@code Map<String, Object>} with
   * top-level namespaces {@code statement, matter, customer, fees, disbursements, trust, summary,
   * org}. Empty sub-queries return empty lists / zero so the Tiptap {% if %} conditionals can hide
   * sections cleanly.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> build(UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    Customer customer =
        project.getCustomerId() != null
            ? customerRepository.findById(project.getCustomerId()).orElse(null)
            : null;

    Map<String, Object> context = new HashMap<>();

    // statement.*
    context.put("statement", buildStatementMap(projectId, periodStart, periodEnd));

    // matter.*
    context.put("matter", buildMatterMap(project));

    // customer.*
    context.put("customer", buildCustomerMap(customer));

    // fees.*
    var feeLines = loadFeeLines(projectId, periodStart, periodEnd);
    BigDecimal totalHours = sumHours(feeLines);
    BigDecimal totalFeesExcl = sumAmounts(feeLines);
    BigDecimal vatAmount = BigDecimal.ZERO; // line-level VAT not modelled on TimeEntry yet
    BigDecimal totalFeesIncl = totalFeesExcl.add(vatAmount);
    context.put(
        "fees",
        Map.of(
            "entries", feeLines,
            "total_hours", totalHours,
            "total_amount_excl_vat", totalFeesExcl,
            "vat_amount", vatAmount,
            "total_amount_incl_vat", totalFeesIncl));

    // disbursements.*
    var disbursementLines = disbursementService.listForStatement(projectId, periodStart, periodEnd);
    BigDecimal disbursementsTotal = sumDisbursements(disbursementLines);
    context.put("disbursements", Map.of("entries", disbursementLines, "total", disbursementsTotal));

    // trust.*
    TrustBlock trust = buildTrustBlock(customer, periodStart, periodEnd);
    context.put(
        "trust",
        Map.of(
            "opening_balance", trust.openingBalance,
            "deposits", trust.deposits,
            "payments", trust.payments,
            "closing_balance", trust.closingBalance));

    // summary.*
    BigDecimal previousBalance = previousBalanceOwing(project, periodStart);
    BigDecimal paymentsReceived = paymentsReceivedInPeriod(project, periodStart, periodEnd);
    BigDecimal newCharges = totalFeesIncl.add(disbursementsTotal);
    BigDecimal closingBalance = previousBalance.add(newCharges).subtract(paymentsReceived);
    StatementSummary summary =
        new StatementSummary(
            totalFeesIncl,
            disbursementsTotal,
            previousBalance,
            paymentsReceived,
            closingBalance,
            trust.closingBalance);
    context.put("summary", toSummaryMap(summary));

    // org.* — reuse the shared org helper, add bankingDetails placeholder for the SoA template.
    var orgMap = new LinkedHashMap<String, Object>(templateContextHelper.buildOrgContext());
    orgMap.putIfAbsent("bankingDetails", "");
    context.put("org", orgMap);

    return context;
  }

  /** Computes the numeric summary without rebuilding the full context. Used by {@code POST /}. */
  @Transactional(readOnly = true)
  public StatementSummary computeSummary(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    Customer customer =
        project.getCustomerId() != null
            ? customerRepository.findById(project.getCustomerId()).orElse(null)
            : null;

    var feeLines = loadFeeLines(projectId, periodStart, periodEnd);
    BigDecimal totalFeesExcl = sumAmounts(feeLines);
    BigDecimal totalFeesIncl = totalFeesExcl;
    var disbursementLines = disbursementService.listForStatement(projectId, periodStart, periodEnd);
    BigDecimal disbursementsTotal = sumDisbursements(disbursementLines);
    TrustBlock trust = buildTrustBlock(customer, periodStart, periodEnd);
    BigDecimal previousBalance = previousBalanceOwing(project, periodStart);
    BigDecimal paymentsReceived = paymentsReceivedInPeriod(project, periodStart, periodEnd);
    BigDecimal closingBalance =
        previousBalance.add(totalFeesIncl).add(disbursementsTotal).subtract(paymentsReceived);

    return new StatementSummary(
        totalFeesIncl,
        disbursementsTotal,
        previousBalance,
        paymentsReceived,
        closingBalance,
        trust.closingBalance);
  }

  // ---------- helpers ----------

  private Map<String, Object> buildStatementMap(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    var map = new LinkedHashMap<String, Object>();
    map.put("period_start", periodStart);
    map.put("period_end", periodEnd);
    LocalDate today = LocalDate.now();
    map.put("generation_date", today);
    map.put("reference", buildStatementReference(projectId, today));
    return map;
  }

  static String buildStatementReference(UUID projectId, LocalDate generationDate) {
    String shortId = projectId.toString().substring(0, 8);
    return "SOA-" + shortId + "-" + generationDate.format(REFERENCE_DATE);
  }

  private Map<String, Object> buildMatterMap(Project project) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", project.getId());
    map.put("name", project.getName());
    map.put(
        "file_reference", project.getReferenceNumber() != null ? project.getReferenceNumber() : "");
    map.put(
        "opened_date",
        project.getCreatedAt() != null
            ? project.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            : null);
    return map;
  }

  private Map<String, Object> buildCustomerMap(Customer customer) {
    var map = new LinkedHashMap<String, Object>();
    if (customer == null) {
      map.put("name", "");
      map.put("address", "");
      return map;
    }
    map.put("id", customer.getId());
    map.put("name", customer.getName() != null ? customer.getName() : "");
    map.put("email", customer.getEmail() != null ? customer.getEmail() : "");
    map.put("address", composeAddress(customer));
    return map;
  }

  private String composeAddress(Customer customer) {
    var parts = new ArrayList<String>();
    if (customer.getAddressLine1() != null && !customer.getAddressLine1().isBlank()) {
      parts.add(customer.getAddressLine1());
    }
    if (customer.getAddressLine2() != null && !customer.getAddressLine2().isBlank()) {
      parts.add(customer.getAddressLine2());
    }
    if (customer.getCity() != null && !customer.getCity().isBlank()) {
      parts.add(customer.getCity());
    }
    if (customer.getStateProvince() != null && !customer.getStateProvince().isBlank()) {
      parts.add(customer.getStateProvince());
    }
    if (customer.getPostalCode() != null && !customer.getPostalCode().isBlank()) {
      parts.add(customer.getPostalCode());
    }
    if (customer.getCountry() != null && !customer.getCountry().isBlank()) {
      parts.add(customer.getCountry());
    }
    return String.join("\n", parts);
  }

  private List<FeeLineDto> loadFeeLines(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    List<TimeEntry> billable =
        timeEntryRepository.findByFilters(projectId, null, periodStart, periodEnd).stream()
            .filter(TimeEntry::isBillable)
            .toList();
    var lines = new ArrayList<FeeLineDto>(billable.size());
    for (TimeEntry te : billable) {
      BigDecimal hours =
          BigDecimal.valueOf(te.getDurationMinutes())
              .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
      BigDecimal rate =
          te.getBillingRateSnapshot() != null ? te.getBillingRateSnapshot() : BigDecimal.ZERO;
      BigDecimal amount = hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
      String memberName = memberNameResolver.resolveName(te.getMemberId());
      lines.add(
          new FeeLineDto(
              te.getId(),
              te.getDate(),
              te.getMemberId(),
              memberName,
              te.getDescription(),
              hours,
              rate,
              amount));
    }
    return lines;
  }

  private BigDecimal sumHours(List<FeeLineDto> lines) {
    return lines.stream().map(FeeLineDto::hours).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumAmounts(List<FeeLineDto> lines) {
    return lines.stream().map(FeeLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumDisbursements(List<DisbursementStatementDto> lines) {
    return lines.stream()
        .map(
            d -> {
              BigDecimal amt = d.amount() != null ? d.amount() : BigDecimal.ZERO;
              BigDecimal vat = d.vatAmount() != null ? d.vatAmount() : BigDecimal.ZERO;
              return amt.add(vat);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private TrustBlock buildTrustBlock(
      Customer customer, LocalDate periodStart, LocalDate periodEnd) {
    if (customer == null) {
      return TrustBlock.empty();
    }
    UUID trustAccountId =
        trustAccountRepository
            .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
            .map(TrustAccount::getId)
            .orElse(null);
    if (trustAccountId == null) {
      return TrustBlock.empty();
    }
    BigDecimal opening =
        clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodStart);
    BigDecimal closing =
        clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodEnd);
    var statement =
        clientLedgerService.getClientLedgerStatement(
            customer.getId(), trustAccountId, periodStart, periodEnd);
    var deposits = new ArrayList<TrustTxDto>();
    var payments = new ArrayList<TrustTxDto>();
    for (var line : statement.transactions()) {
      var dto =
          new TrustTxDto(
              line.transactionId(),
              line.transactionDate(),
              line.transactionType(),
              line.amount(),
              line.reference(),
              line.description());
      if ("DEPOSIT".equals(line.transactionType())) {
        deposits.add(dto);
      } else if ("PAYMENT".equals(line.transactionType())
          || "DISBURSEMENT_PAYMENT".equals(line.transactionType())
          || "FEE_TRANSFER".equals(line.transactionType())) {
        payments.add(dto);
      }
    }
    return new TrustBlock(
        opening != null ? opening : BigDecimal.ZERO,
        deposits,
        payments,
        closing != null ? closing : BigDecimal.ZERO);
  }

  private BigDecimal previousBalanceOwing(Project project, LocalDate periodStart) {
    UUID customerId = project.getCustomerId();
    if (customerId == null) {
      return BigDecimal.ZERO;
    }
    List<Invoice> issued =
        invoiceRepository.findByCustomerId(customerId).stream()
            .filter(
                inv ->
                    inv.getStatus() == InvoiceStatus.SENT
                        && inv.getIssueDate() != null
                        && inv.getIssueDate().isBefore(periodStart))
            .toList();
    BigDecimal grossOutstanding =
        issued.stream()
            .map(Invoice::getTotal)
            .filter(t -> t != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal paymentsBefore = BigDecimal.ZERO;
    for (Invoice inv : issued) {
      paymentsBefore =
          paymentsBefore.add(
              paymentsForInvoice(inv.getId()).stream()
                  .filter(p -> p.getCreatedAt() != null)
                  .filter(
                      p ->
                          p.getCreatedAt()
                              .isBefore(
                                  periodStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))
                  .map(PaymentEvent::getAmount)
                  .filter(a -> a != null)
                  .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    return grossOutstanding.subtract(paymentsBefore).max(BigDecimal.ZERO);
  }

  private BigDecimal paymentsReceivedInPeriod(
      Project project, LocalDate periodStart, LocalDate periodEnd) {
    UUID customerId = project.getCustomerId();
    if (customerId == null) {
      return BigDecimal.ZERO;
    }
    var startInstant = periodStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    var endInstant = periodEnd.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    BigDecimal sum = BigDecimal.ZERO;
    for (Invoice inv : invoiceRepository.findByCustomerId(customerId)) {
      for (PaymentEvent p : paymentsForInvoice(inv.getId())) {
        if (p.getStatus() != PaymentEventStatus.COMPLETED) continue;
        if (p.getCreatedAt() == null) continue;
        if (p.getCreatedAt().isBefore(startInstant)) continue;
        if (!p.getCreatedAt().isBefore(endInstant)) continue;
        if (p.getAmount() != null) {
          sum = sum.add(p.getAmount());
        }
      }
    }
    return sum;
  }

  private List<PaymentEvent> paymentsForInvoice(UUID invoiceId) {
    return paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
  }

  private Map<String, Object> toSummaryMap(StatementSummary s) {
    var map = new LinkedHashMap<String, Object>();
    map.put("total_fees", s.totalFees());
    map.put("total_disbursements", s.totalDisbursements());
    map.put("previous_balance_owing", s.previousBalanceOwing());
    map.put("payments_received", s.paymentsReceived());
    map.put("closing_balance_owing", s.closingBalanceOwing());
    map.put("trust_balance_held", s.trustBalanceHeld());
    return map;
  }

  private record TrustBlock(
      BigDecimal openingBalance,
      List<TrustTxDto> deposits,
      List<TrustTxDto> payments,
      BigDecimal closingBalance) {

    static TrustBlock empty() {
      return new TrustBlock(BigDecimal.ZERO, List.of(), List.of(), BigDecimal.ZERO);
    }
  }
}
