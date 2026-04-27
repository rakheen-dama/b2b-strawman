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
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.FeeLineDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.TrustTxDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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

  private static final Logger log = LoggerFactory.getLogger(StatementOfAccountContextBuilder.class);

  private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
  private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";

  // Mirror the canonical credit/debit classifications used by ClientLedgerService and
  // TrustReconciliationService so every ledger type produced by the trust subsystem renders
  // in either the deposits or payments section (otherwise the activity list stops reconciling
  // with the opening/closing balances). DISBURSEMENT_PAYMENT is included on the debit side
  // because the disbursement subsystem emits it via FEE_TRANSFER + payment recording.
  private static final Set<String> TRUST_CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");
  private static final Set<String> TRUST_DEBIT_TYPES =
      Set.of(
          "PAYMENT",
          "DISBURSEMENT_PAYMENT",
          "TRANSFER_OUT",
          "FEE_TRANSFER",
          "REFUND",
          "INTEREST_LPFF");

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
  private final VerticalModuleGuard moduleGuard;
  private final ObjectMapper objectMapper;
  private final TaxRateRepository taxRateRepository;
  private final TrustTransactionRepository trustTransactionRepository;

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
      TemplateContextHelper templateContextHelper,
      VerticalModuleGuard moduleGuard,
      ObjectMapper objectMapper,
      TaxRateRepository taxRateRepository,
      TrustTransactionRepository trustTransactionRepository) {
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
    this.moduleGuard = moduleGuard;
    this.objectMapper = objectMapper;
    this.taxRateRepository = taxRateRepository;
    this.trustTransactionRepository = trustTransactionRepository;
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

    // fees, disbursements, trust, summary — all share the same queries + math, so compute
    // them once and reuse for both the rendered context (build) and the persisted snapshot
    // (computeSummary). Keeps the rendered totals from drifting from the snapshot on changes.
    Aggregates agg = aggregate(project, customer, projectId, periodStart, periodEnd);

    context.put(
        "fees",
        Map.of(
            "entries", toMapList(agg.feeLines()),
            "total_hours", agg.totalHours(),
            "total_amount_excl_vat", agg.totalFeesExcl(),
            "vat_amount", agg.vatAmount(),
            "total_amount_incl_vat", agg.totalFeesIncl()));

    context.put(
        "disbursements",
        Map.of("entries", toMapList(agg.disbursementLines()), "total", agg.disbursementsTotal()));

    context.put(
        "trust",
        Map.of(
            "opening_balance",
            agg.trust().openingBalance,
            "deposits",
            toMapList(agg.trust().deposits),
            "payments",
            toMapList(agg.trust().payments),
            "closing_balance",
            agg.trust().closingBalance));

    context.put("summary", toSummaryMap(agg.summary()));

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

    return aggregate(project, customer, projectId, periodStart, periodEnd).summary();
  }

  // ---------- helpers ----------

  /**
   * Converts a list of typed DTOs (records) to the {@code List<Map<String,Object>>} shape required
   * by {@code TiptapRenderer.renderLoopTable}. The renderer assumes JSONB-like map shape per row;
   * SoA's typed-record DTOs ({@code FeeLineDto}, {@code DisbursementStatementDto}, {@code
   * TrustTxDto}) would otherwise blow up with {@code ClassCastException} at {@code
   * TiptapRenderer:309} on the first iteration (see GAP-L-71).
   *
   * <p>Uses the autowired Spring {@link ObjectMapper} so any tenant-wide serialization
   * customisations (date format, BigDecimal encoding, naming) apply consistently with the rest of
   * the JSON layer.
   */
  private List<Map<String, Object>> toMapList(List<?> typedRows) {
    if (typedRows == null || typedRows.isEmpty()) return List.of();
    return objectMapper.convertValue(typedRows, new TypeReference<List<Map<String, Object>>>() {});
  }

  /**
   * Pre-computed bundle returned by {@link #aggregate}. Holds every artifact needed by {@link
   * #build} (full context map) and {@link #computeSummary} (snapshot only) so the rendered template
   * context and the persisted summary can never drift.
   */
  private record Aggregates(
      List<FeeLineDto> feeLines,
      BigDecimal totalHours,
      BigDecimal totalFeesExcl,
      BigDecimal vatAmount,
      BigDecimal totalFeesIncl,
      List<DisbursementStatementDto> disbursementLines,
      BigDecimal disbursementsTotal,
      TrustBlock trust,
      StatementSummary summary) {}

  private Aggregates aggregate(
      Project project,
      Customer customer,
      UUID projectId,
      LocalDate periodStart,
      LocalDate periodEnd) {
    var feeLines = loadFeeLines(projectId, periodStart, periodEnd);
    BigDecimal totalHours = sumHours(feeLines);
    BigDecimal totalFeesExcl = sumAmounts(feeLines);
    // GAP-L-95: apply the org's default tax rate to fees aggregate. Mirrors the invoice path
    // (InvoiceTaxService) which derives VAT from the same default rate. Time entries do not
    // carry per-line tax (unlike invoice lines), so the SoA aggregates at the total level —
    // valid because every fee line attracts the same rate (no zero-rated/exempt time entries).
    // Falls back to ZERO when no default rate is configured, the rate is exempt/inactive,
    // or rate value is null/zero — so non-VAT-registered tenants render unchanged.
    BigDecimal vatAmount = computeFeesVat(totalFeesExcl);
    BigDecimal totalFeesIncl = totalFeesExcl.add(vatAmount);

    var disbursementLines = disbursementService.listForStatement(projectId, periodStart, periodEnd);
    BigDecimal disbursementsTotal = sumDisbursements(disbursementLines);

    TrustBlock trust = buildTrustBlock(customer, periodStart, periodEnd);

    BigDecimal previousBalance = previousBalanceOwing(project, periodStart);
    BigDecimal paymentsReceived = paymentsReceivedInPeriod(project, periodStart, periodEnd);
    BigDecimal closingBalance =
        previousBalance.add(totalFeesIncl).add(disbursementsTotal).subtract(paymentsReceived);

    StatementSummary summary =
        new StatementSummary(
            totalFeesIncl,
            disbursementsTotal,
            previousBalance,
            paymentsReceived,
            closingBalance,
            trust.closingBalance);

    return new Aggregates(
        feeLines,
        totalHours,
        totalFeesExcl,
        vatAmount,
        totalFeesIncl,
        disbursementLines,
        disbursementsTotal,
        trust,
        summary);
  }

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
      BigDecimal minutes = BigDecimal.valueOf(te.getDurationMinutes());
      // Compute amount from raw minutes at higher precision, then round once. Using the
      // 2-decimal display hours for both display AND amount calculation drops fractional
      // minute precision (e.g., a 7-minute entry rounds to 0.12h, so 0.12h × R1000 bills
      // R120.00 instead of the correct R116.67).
      BigDecimal hours = minutes.divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
      BigDecimal rate =
          te.getBillingRateSnapshot() != null ? te.getBillingRateSnapshot() : BigDecimal.ZERO;
      BigDecimal amount =
          minutes
              .divide(MINUTES_PER_HOUR, 10, RoundingMode.HALF_UP)
              .multiply(rate)
              .setScale(2, RoundingMode.HALF_UP);
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

  /**
   * Computes VAT on the fees aggregate using the tenant's default tax rate. Returns ZERO when no
   * default rate exists, the rate is inactive/exempt, or its value is null/zero — preserving
   * non-VAT-registered tenant behaviour. Mirrors the invoice path's derivation of VAT from {@link
   * io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository#findByIsDefaultTrue()}.
   */
  private BigDecimal computeFeesVat(BigDecimal totalFeesExcl) {
    if (totalFeesExcl == null || totalFeesExcl.signum() == 0) {
      return BigDecimal.ZERO;
    }
    var defaultRate = taxRateRepository.findByIsDefaultTrue();
    if (defaultRate.isEmpty()) {
      return BigDecimal.ZERO;
    }
    TaxRate rate = defaultRate.get();
    if (!rate.isActive() || rate.isExempt()) {
      return BigDecimal.ZERO;
    }
    BigDecimal ratePercent = rate.getRate();
    if (ratePercent == null || ratePercent.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return totalFeesExcl
        .multiply(ratePercent)
        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
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
    // Statement endpoint is gated on the `disbursements` module only. If the tenant has not also
    // enabled `trust_accounting`, ClientLedgerService throws ModuleNotEnabledException (403).
    // Short-
    // circuit to an empty trust block so the statement still renders without the trust section.
    if (!moduleGuard.isModuleEnabled(TRUST_ACCOUNTING_MODULE)) {
      return TrustBlock.empty();
    }
    // GAP-L-94: prefer the trust account(s) where THIS customer actually has activity. The
    // previous resolver (`findByAccountTypeAndPrimaryTrue(GENERAL)`) returned a tenant-wide
    // primary account and silently short-circuited the SoA when the customer's deposits
    // happened to live on a different account (multi-account tenants, or a tenant where the
    // primary GENERAL flag was not set). Section 86 / LPC compliance breaks if the SoA's
    // Trust Activity section is empty for a customer with active funds.
    List<UUID> customerTrustAccountIds =
        trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customer.getId());
    UUID trustAccountId;
    if (customerTrustAccountIds.isEmpty()) {
      return TrustBlock.empty();
    } else if (customerTrustAccountIds.size() == 1) {
      trustAccountId = customerTrustAccountIds.get(0);
    } else {
      // Multi-account customer (unusual for ZA Section 86 firms — usually GENERAL only). Prefer
      // the primary GENERAL trust account when the customer has activity on it; otherwise fall
      // back to the first account id returned. Logging is intentional: a tenant with multiple
      // active trust accounts per customer is an unusual operational shape worth surfacing.
      UUID preferred =
          trustAccountRepository
              .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
              .map(TrustAccount::getId)
              .filter(customerTrustAccountIds::contains)
              .orElse(customerTrustAccountIds.get(0));
      log.warn(
          "Customer {} has activity on {} trust accounts; using {} for SoA Trust Activity section",
          customer.getId(),
          customerTrustAccountIds.size(),
          preferred);
      trustAccountId = preferred;
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
      String type = line.transactionType();
      if (TRUST_CREDIT_TYPES.contains(type)) {
        deposits.add(dto);
      } else if (TRUST_DEBIT_TYPES.contains(type)) {
        payments.add(dto);
      }
      // Unknown ledger types are ignored — TRUST_CREDIT_TYPES/TRUST_DEBIT_TYPES mirror the
      // canonical sets in ClientLedgerService, so an unknown type here means a new type was
      // added without updating both classifiers.
    }
    return new TrustBlock(
        opening != null ? opening : BigDecimal.ZERO,
        deposits,
        payments,
        closing != null ? closing : BigDecimal.ZERO);
  }

  private BigDecimal previousBalanceOwing(Project project, LocalDate periodStart) {
    if (project.getId() == null) {
      return BigDecimal.ZERO;
    }
    // Statement of Account is a per-MATTER document, so the opening balance must reflect
    // invoices linked to THIS project only. Customer-scoping would conflate balances across
    // every matter the customer has — a client with three active matters would see Matter A's
    // statement showing the combined arrears of all three.
    //
    // Any invoice issued before periodStart contributes to the opening balance regardless of
    // its CURRENT status (an invoice issued in March and paid in May still owed money on
    // April 1). Filtering by SENT-only would silently drop invoices later marked PAID, while
    // their post-periodStart payments would also be excluded — netting to a zero opening
    // contribution and understating the previous balance owing. VOID is excluded because a
    // cancelled invoice never owed anything; DRAFT/APPROVED are excluded because they have
    // no issueDate set (issueDate is populated on send).
    List<Invoice> issued =
        invoiceRepository.findByProjectId(project.getId()).stream()
            .filter(
                inv ->
                    inv.getStatus() != InvoiceStatus.VOID
                        && inv.getStatus() != InvoiceStatus.DRAFT
                        && inv.getStatus() != InvoiceStatus.APPROVED
                        && inv.getIssueDate() != null
                        && inv.getIssueDate().isBefore(periodStart))
            .toList();
    if (issued.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal grossOutstanding =
        issued.stream()
            .map(Invoice::getTotal)
            .filter(t -> t != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // One query for all invoices, then group in memory (avoids N+1).
    List<UUID> issuedIds = issued.stream().map(Invoice::getId).toList();
    var startInstant = periodStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    BigDecimal paymentsBefore =
        paymentEventRepository.findByInvoiceIdInOrderByCreatedAtDesc(issuedIds).stream()
            .filter(p -> p.getStatus() == PaymentEventStatus.COMPLETED)
            .filter(p -> p.getCreatedAt() != null)
            .filter(p -> p.getCreatedAt().isBefore(startInstant))
            .map(PaymentEvent::getAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return grossOutstanding.subtract(paymentsBefore).max(BigDecimal.ZERO);
  }

  private BigDecimal paymentsReceivedInPeriod(
      Project project, LocalDate periodStart, LocalDate periodEnd) {
    if (project.getId() == null) {
      return BigDecimal.ZERO;
    }
    // Per-matter scope — see previousBalanceOwing for the rationale.
    List<Invoice> invoices = invoiceRepository.findByProjectId(project.getId());
    if (invoices.isEmpty()) {
      return BigDecimal.ZERO;
    }
    List<UUID> invoiceIds = invoices.stream().map(Invoice::getId).toList();
    var startInstant = periodStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    var endInstant = periodEnd.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    // Single batched query, filtered in memory (avoids N+1 from per-invoice calls).
    return paymentEventRepository.findByInvoiceIdInOrderByCreatedAtDesc(invoiceIds).stream()
        .filter(p -> p.getStatus() == PaymentEventStatus.COMPLETED)
        .filter(p -> p.getCreatedAt() != null)
        .filter(p -> !p.getCreatedAt().isBefore(startInstant))
        .filter(p -> p.getCreatedAt().isBefore(endInstant))
        .map(PaymentEvent::getAmount)
        .filter(a -> a != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
