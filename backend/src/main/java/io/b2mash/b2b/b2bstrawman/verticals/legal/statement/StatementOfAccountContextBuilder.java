package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.MatterFeeNoteTotals;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.FeeLineDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.TrustTxDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustDataVisibility;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientTrustStatementContext;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Format hints for the standalone (non-loop-table) variables this builder produces. The SoA path
   * bypasses {@code PdfRenderingService.buildFormatHints} (its keys come from {@code
   * VariableMetadataRegistry}, which does not know the statement-specific namespaces), so the hints
   * live here next to the context shape they describe. Without them, standalone amounts rendered
   * raw {@code BigDecimal.toString()} ("1250.00") while loop-table columns rendered locale-aware
   * currency ("R50&nbsp;000,00") — mixed number locales in one document (LZKC-017).
   */
  private static final Map<String, String> FORMAT_HINTS =
      Map.ofEntries(
          Map.entry("statement.period_start", "date"),
          Map.entry("statement.period_end", "date"),
          Map.entry("statement.generation_date", "date"),
          Map.entry("matter.opened_date", "date"),
          Map.entry("fees.total_hours", "number"),
          Map.entry("fees.total_amount_excl_vat", "currency"),
          Map.entry("fees.vat_amount", "currency"),
          Map.entry("fees.total_amount_incl_vat", "currency"),
          Map.entry("disbursements.total", "currency"),
          Map.entry("trust.opening_balance", "currency"),
          Map.entry("trust.closing_balance", "currency"),
          // 648F: the "Section 86(4) investment" block (ClientTrustStatementContext.investedBlock)
          Map.entry("trust.invested.opening_balance", "currency"),
          Map.entry("trust.invested.capital_placed", "currency"),
          Map.entry("trust.invested.interest_gross", "currency"),
          Map.entry("trust.invested.interest_fund_share", "currency"),
          Map.entry("trust.invested.interest_net", "currency"),
          Map.entry("trust.invested.returned_to_trust", "currency"),
          Map.entry("trust.invested.closing_balance", "currency"),
          Map.entry("trust.invested.total_held", "currency"),
          Map.entry("summary.total_fees", "currency"),
          Map.entry("summary.total_disbursements", "currency"),
          Map.entry("summary.previous_balance_owing", "currency"),
          Map.entry("summary.payments_received", "currency"),
          Map.entry("summary.closing_balance_owing", "currency"),
          Map.entry("summary.trust_balance_held", "currency"),
          Map.entry("summary.unbilled_wip", "currency"),
          Map.entry("org.logoUrl", "image"));

  /** Format hints matching the context produced by {@link #build}. */
  static Map<String, String> formatHints() {
    return FORMAT_HINTS;
  }

  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final DisbursementService disbursementService;
  private final ClientLedgerService clientLedgerService;
  private final TrustAccountRepository trustAccountRepository;
  private final MatterFeeNoteTotals feeNoteTotals;
  private final MemberNameResolver memberNameResolver;
  private final TemplateContextHelper templateContextHelper;
  private final TrustDataVisibility trustDataVisibility;
  private final ObjectMapper objectMapper;
  private final TaxRateRepository taxRateRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final Clock clock;

  public StatementOfAccountContextBuilder(
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      TimeEntryRepository timeEntryRepository,
      DisbursementService disbursementService,
      ClientLedgerService clientLedgerService,
      TrustAccountRepository trustAccountRepository,
      MatterFeeNoteTotals feeNoteTotals,
      MemberNameResolver memberNameResolver,
      TemplateContextHelper templateContextHelper,
      TrustDataVisibility trustDataVisibility,
      ObjectMapper objectMapper,
      TaxRateRepository taxRateRepository,
      TrustTransactionRepository trustTransactionRepository,
      Clock clock) {
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.disbursementService = disbursementService;
    this.clientLedgerService = clientLedgerService;
    this.trustAccountRepository = trustAccountRepository;
    this.feeNoteTotals = feeNoteTotals;
    this.memberNameResolver = memberNameResolver;
    this.templateContextHelper = templateContextHelper;
    this.trustDataVisibility = trustDataVisibility;
    this.objectMapper = objectMapper;
    this.taxRateRepository = taxRateRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.clock = clock;
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
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

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
    Aggregates agg = aggregate(customer, projectId, periodStart, periodEnd, true);

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

    var trust = new LinkedHashMap<String, Object>();
    trust.put("opening_balance", agg.trust().openingBalance);
    trust.put("deposits", toMapList(agg.trust().deposits));
    trust.put("payments", toMapList(agg.trust().payments));
    trust.put("closing_balance", agg.trust().closingBalance);
    trust.put("status", agg.trust().withheld ? "withheld" : "available");
    // 648F: the section 86(4) investment block, only for a client with something to print
    if (agg.trust().invested != null) {
      trust.put("invested", agg.trust().invested);
    }
    context.put("trust", trust);

    var summary = toSummaryMap(agg.summary());
    // G89-F16 / D9: shown, never counted in closing_balance_owing. Context key only — the template
    // row arrives with the legal-za pack bump (Batch C2).
    summary.put("unbilled_wip", agg.unbilledWip());
    context.put("summary", summary);

    // org.* — the shared org helper supplies org.bankingDetails (a map, or null when the firm has
    // no banking details; Phase 88 626B, ADR-353). No SoA-specific placeholder: the renderer prints
    // "" for the legacy whole-map {{org.bankingDetails}} binding until 626D moves it to the leaves.
    context.put("org", new LinkedHashMap<String, Object>(templateContextHelper.buildOrgContext()));

    // _locale — so standalone currency/number variables format in the tenant's locale, matching
    // the loop-table columns (LZKC-017).
    templateContextHelper.populateLocale(context);

    return context;
  }

  /** Computes the numeric summary without rebuilding the full context. Used by {@code POST /}. */
  @Transactional(readOnly = true)
  public StatementSummary computeSummary(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));
    Customer customer =
        project.getCustomerId() != null
            ? customerRepository.findById(project.getCustomerId()).orElse(null)
            : null;

    // The summary carries the general trust balance only (R-C): no invested read
    return aggregate(customer, projectId, periodStart, periodEnd, false).summary();
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
      StatementSummary summary,
      BigDecimal unbilledWip) {}

  private Aggregates aggregate(
      Customer customer,
      UUID projectId,
      LocalDate periodStart,
      LocalDate periodEnd,
      boolean withInvested) {
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

    TrustBlock trust = buildTrustBlock(customer, periodStart, periodEnd, withInvested);

    // G89-F16 / D9: the summary is on a FEE-NOTE basis — the same issued notes the closure letter
    // reads (MatterFeeNoteTotals). The fee and disbursement tables above stay activity listings;
    // the owing figures come from what was billed and paid:
    //   previous = owing at the end of the day before periodStart on notes issued before it
    //   fees / disbursements = the fee / disbursement lines (incl. VAT) of notes issued in period
    //   closing = owing at the end of periodEnd (= Σ balance due when periodEnd is today)
    //   payments = previous + fees + disbursements − closing, so the printed summary always adds
    //     up; it equals the COMPLETED payments recorded in the period on notes issued by its end.
    var ledger = feeNoteTotals.load(projectId);
    BigDecimal previousBalance = ledger.outstandingAsOf(periodStart.minusDays(1));
    var billedInPeriod = ledger.issuedBetween(periodStart, periodEnd);
    BigDecimal billedFees = billedInPeriod.feesInclVat();
    BigDecimal billedDisbursements = billedInPeriod.disbursementsInclVat();
    BigDecimal closingBalance = ledger.outstandingAsOf(periodEnd);
    BigDecimal paymentsReceived =
        previousBalance.add(billedFees).add(billedDisbursements).subtract(closingBalance);

    StatementSummary summary =
        new StatementSummary(
            billedFees,
            billedDisbursements,
            previousBalance,
            paymentsReceived,
            closingBalance,
            trust.closingBalance);
    if (trust.withheld && trustDataVisibility.withheldFromCurrentMember()) {
      // CodeRabbit #5: the firm keeps trust money but this member may not see it, so nothing was
      // read — record the figure as withheld, not as a zero the Statements table would show
      // everyone as "no trust money held". (Trust accounting off: a genuine zero, as before.)
      summary = summary.withTrustWithheld();
    }

    return new Aggregates(
        feeLines,
        totalHours,
        totalFeesExcl,
        vatAmount,
        totalFeesIncl,
        disbursementLines,
        disbursementsTotal,
        trust,
        summary,
        unbilledWip(projectId, periodEnd));
  }

  /**
   * Unbilled work in progress at the end of {@code periodEnd}, excl. VAT: unbilled billable time
   * (rate snapshot × hours, same precision as the fee lines) plus APPROVED + UNBILLED disbursements
   * incurred by then. Shown on the statement, never counted in the closing balance (D9).
   */
  private BigDecimal unbilledWip(UUID projectId, LocalDate periodEnd) {
    BigDecimal time =
        timeEntryRepository.findUnbilledBillableByProjectIdUpTo(projectId, periodEnd).stream()
            .map(te -> feeAmount(te.getDurationMinutes(), te.getBillingRateSnapshot()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal disbursements =
        disbursementService.listUnbilledForProject(projectId).stream()
            .filter(d -> d.incurredDate() == null || !d.incurredDate().isAfter(periodEnd))
            .map(d -> d.amount() != null ? d.amount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return time.add(disbursements);
  }

  private Map<String, Object> buildStatementMap(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    var map = new LinkedHashMap<String, Object>();
    map.put("period_start", periodStart);
    map.put("period_end", periodEnd);
    LocalDate today = LocalDate.now(clock);
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
            ? project.getCreatedAt().atZone(KaziTime.ZONE).toLocalDate()
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
      BigDecimal amount = feeAmount(te.getDurationMinutes(), rate);
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

  /** Minutes × hourly rate, at full precision, rounded once to cents. */
  private static BigDecimal feeAmount(int durationMinutes, BigDecimal rate) {
    return BigDecimal.valueOf(durationMinutes)
        .divide(MINUTES_PER_HOUR, 10, RoundingMode.HALF_UP)
        .multiply(rate != null ? rate : BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP);
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

  /**
   * The trust section; {@code withInvested} reads the section 86(4) block too — only the rendered
   * context prints it ({@link #computeSummary} carries the general balance alone).
   */
  private TrustBlock buildTrustBlock(
      Customer customer, LocalDate periodStart, LocalDate periodEnd, boolean withInvested) {
    if (customer == null) {
      return TrustBlock.empty();
    }
    // Statement endpoint is gated on the `disbursements` module and GENERATE_STATEMENT_OF_ACCOUNT
    // only. Trust money needs the `trust_accounting` module (else ClientLedgerService throws
    // ModuleNotEnabledException, 403) AND VIEW_TRUST (security review, PR #1749) — the shared
    // TrustDataVisibility rule. Otherwise the block is withheld: no ledger read, no amounts, and
    // the template prints "Trust details are not available." instead of the trust section.
    if (!trustDataVisibility.visibleToCurrentMember()) {
      return TrustBlock.withheldBlock();
    }
    // GAP-L-94: prefer the trust account(s) where THIS customer actually has activity. The
    // previous resolver (`findByAccountTypeAndPrimaryTrue(GENERAL)`) returned a tenant-wide
    // primary account and silently short-circuited the SoA when the customer's deposits
    // happened to live on a different account (multi-account tenants, or a tenant where the
    // primary GENERAL flag was not set). Section 86 / LPC compliance breaks if the SoA's
    // Trust Activity section is empty for a customer with active funds.
    //
    // 652D (D-1): with several such accounts, print the one holding the customer's money as at
    // periodEnd — a closed primary GENERAL holds nothing after its close and must not hide the
    // money on its replacement — tie-broken by the primary GENERAL, else the first id. A sole
    // account is used as-is. The rule lives in ClientTrustStatementContext.statementAccountId,
    // shared with the Client Trust Statement. Logging is intentional: a customer with activity on
    // several trust accounts is an unusual operational shape worth surfacing.
    List<UUID> customerTrustAccountIds =
        trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customer.getId());
    var selected =
        ClientTrustStatementContext.statementAccountId(
            customer.getId(),
            customerTrustAccountIds,
            periodEnd,
            clientLedgerService,
            trustAccountRepository);
    if (selected.isEmpty()) {
      return TrustBlock.empty();
    }
    UUID trustAccountId = selected.get();
    if (customerTrustAccountIds.size() > 1) {
      log.warn(
          "Customer {} has activity on {} trust accounts; using {} for SoA Trust Activity section",
          customer.getId(),
          customerTrustAccountIds.size(),
          trustAccountId);
    }
    // LZKC-030: take BOTH balances from the ledger statement itself — it computes its opening
    // as of periodStart.minusDays(1) and its closing as opening + itemised lines, so
    // opening/deposits/payments/closing are self-reconciling by construction. Recomputing them
    // via getClientBalanceAsOfDate(periodStart) double-counted period-start-day transactions:
    // that query is date-INCLUSIVE (`transactionDate <= :asOfDate`), so a deposit dated ON the
    // period start appeared in the opening balance AND in the itemised Deposits table, breaking
    // the Section 86 statement's self-reconciliation. Both balances are non-null (the underlying
    // query COALESCEs to 0).
    var statement =
        clientLedgerService.getClientLedgerStatement(
            customer.getId(), trustAccountId, periodStart, periodEnd);
    BigDecimal opening = statement.openingBalance();
    BigDecimal closing = statement.closingBalance();
    var deposits = new ArrayList<TrustTxDto>();
    var payments = new ArrayList<TrustTxDto>();
    for (var line : statement.transactions()) {
      var dto =
          new TrustTxDto(
              line.transactionId(),
              line.transactionDate(),
              line.transactionType(),
              TrustTransactionTypeLabels.label(line.transactionType()),
              line.amount(),
              line.reference(),
              // A REVERSAL line prints "Reversal — <reference>", never the firm's reason
              TrustTransactionTypeLabels.clientDescription(
                  line.transactionType(), line.description(), line.reference()));
      // Deposits or payments by the line's own posted effect on the client ledger (Phase 92,
      // ADR-378) — the same figure the opening/closing balances sum, so the itemised tables always
      // reconcile with them. No type-name table to keep in step.
      int sign = line.ledgerEffect() != null ? line.ledgerEffect().signum() : 0;
      if (sign > 0) {
        deposits.add(dto);
      } else if (sign < 0) {
        payments.add(dto);
      }
    }
    // 648F (§16.6.4, R-B): the client's section 86(4) investments placed from this account — the
    // same per-client scope as the trust block above — opening as at periodStart − 1, closing as
    // at periodEnd; interest only from INV_INTEREST
    var invested =
        withInvested
            ? clientLedgerService.getClientInvestedStatement(
                customer.getId(), trustAccountId, periodStart, periodEnd)
            : ClientLedgerService.InvestedStatement.NONE;
    return new TrustBlock(
        opening,
        deposits,
        payments,
        closing,
        false,
        invested.isEmpty() ? null : ClientTrustStatementContext.investedBlock(invested, closing));
  }

  private LinkedHashMap<String, Object> toSummaryMap(StatementSummary s) {
    var map = new LinkedHashMap<String, Object>();
    map.put("total_fees", s.totalFees());
    map.put("total_disbursements", s.totalDisbursements());
    map.put("previous_balance_owing", s.previousBalanceOwing());
    map.put("payments_received", s.paymentsReceived());
    map.put("closing_balance_owing", s.closingBalanceOwing());
    map.put("trust_balance_held", s.trustBalanceHeld());
    map.put("trust_withheld", s.trustWithheld());
    return map;
  }

  /**
   * The trust section; {@code invested} is the "Section 86(4) investment" block (648F), null when
   * there is nothing to print or the trust money is withheld.
   */
  private record TrustBlock(
      BigDecimal openingBalance,
      List<TrustTxDto> deposits,
      List<TrustTxDto> payments,
      BigDecimal closingBalance,
      boolean withheld,
      Map<String, Object> invested) {

    TrustBlock(
        BigDecimal openingBalance,
        List<TrustTxDto> deposits,
        List<TrustTxDto> payments,
        BigDecimal closingBalance) {
      this(openingBalance, deposits, payments, closingBalance, false, null);
    }

    /** No trust activity to show (no client, no account): zero, and the section still prints. */
    static TrustBlock empty() {
      return new TrustBlock(BigDecimal.ZERO, List.of(), List.of(), BigDecimal.ZERO);
    }

    /** The member may not see trust money: no lines, no amounts, the section is not printed. */
    static TrustBlock withheldBlock() {
      return new TrustBlock(BigDecimal.ZERO, List.of(), List.of(), BigDecimal.ZERO, true, null);
    }
  }
}
