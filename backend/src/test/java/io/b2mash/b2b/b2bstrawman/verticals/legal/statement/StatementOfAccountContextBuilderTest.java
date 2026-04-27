package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link StatementOfAccountContextBuilder} (Phase 67, Epic 491A, task 491.5). Mocks
 * all collaborator repositories/services and verifies the four sub-queries (fees, disbursements,
 * trust, summary) populate as expected for both the populated and empty-period cases. Pattern
 * mirrors {@code ProjectContextBuilderTest}.
 */
@ExtendWith(MockitoExtension.class)
class StatementOfAccountContextBuilderTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private DisbursementService disbursementService;
  @Mock private ClientLedgerService clientLedgerService;
  @Mock private TrustAccountRepository trustAccountRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private PaymentEventRepository paymentEventRepository;
  @Mock private MemberNameResolver memberNameResolver;
  @Mock private TemplateContextHelper templateContextHelper;
  @Mock private VerticalModuleGuard moduleGuard;
  @Mock private TaxRateRepository taxRateRepository;
  @Mock private TrustTransactionRepository trustTransactionRepository;

  // Real ObjectMapper (Jackson 3 — `tools.jackson.databind`, the same artifact Spring Boot 4
  // autoconfigures and injects in production). Needed because the GAP-L-71 fix uses
  // ObjectMapper.convertValue(...) to coerce typed DTO lists to List<Map<String,Object>>.
  // Jackson 3 includes JSR-310 (java.time) handlers natively, so a vanilla `new ObjectMapper()`
  // serialises LocalDate / UUID correctly without extra module registration. Mocking
  // ObjectMapper would force per-call stubs; @Spy on the real instance avoids that boilerplate
  // while still letting Mockito inject it via @InjectMocks.
  @org.mockito.Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private StatementOfAccountContextBuilder builder;

  private final UUID projectId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final UUID trustAccountId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();

  private final LocalDate periodStart = LocalDate.of(2026, 4, 1);
  private final LocalDate periodEnd = LocalDate.of(2026, 4, 30);

  @BeforeEach
  void setUp() {
    lenient().when(templateContextHelper.buildOrgContext()).thenReturn(Map.of("name", "Acme Law"));
    lenient().when(memberNameResolver.resolveName(any())).thenReturn("Jane Attorney");
    // Default: trust_accounting module is enabled (matches a fully legal tenant). Individual
    // tests override this when they need to assert the disabled-module behaviour.
    lenient().when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    // Default: no default tax rate configured → VAT computes to ZERO. Individual VAT-aware
    // tests override this to assert the populated-rate path.
    lenient().when(taxRateRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
    // GAP-L-94: SoA now resolves the trust account from the customer's actual activity.
    // Default empty list mirrors a customer with no trust activity; tests that exercise the
    // trust block override this with the trust-account id(s) the test scenario expects.
    lenient()
        .when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(any()))
        .thenReturn(List.of());
  }

  @Test
  void populatedPeriod_subQueriesPopulated_withExpectedTotals() throws Exception {
    var project = projectWithCustomer("Smith v Jones", customerId);
    var customer = customer("Acme Client");
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    // Two billable time entries: 60min @ R1500 + 90min @ R2000.
    var te1 = timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1500.00"), "Drafting");
    var te2 = timeEntry(LocalDate.of(2026, 4, 10), 90, true, new BigDecimal("2000.00"), "Hearing");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te1, te2));

    // Two disbursements, each (amount + vat) → one R230, one R57.50.
    var d1 =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 6),
            DisbursementCategory.SHERIFF_FEES,
            "Sheriff service",
            new BigDecimal("200.00"),
            new BigDecimal("30.00"),
            "Acme Sheriff",
            "REF-1");
    var d2 =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 12),
            DisbursementCategory.COURT_FEES,
            "Filing fee",
            new BigDecimal("50.00"),
            new BigDecimal("7.50"),
            "Court Registry",
            "REF-2");
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of(d1, d2));

    // Trust: opening 100, two deposits totalling 500, one payment of 200, closing 400.
    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(new BigDecimal("100.00"));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(new BigDecimal("400.00"));
    var dep1 = ledgerLine("DEPOSIT", new BigDecimal("300.00"));
    var dep2 = ledgerLine("DEPOSIT", new BigDecimal("200.00"));
    var pay1 = ledgerLine("PAYMENT", new BigDecimal("200.00"));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                new BigDecimal("100.00"), new BigDecimal("400.00"), List.of(dep1, dep2, pay1)));

    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    assertThat(context)
        .containsKeys(
            "statement", "matter", "customer", "fees", "disbursements", "trust", "summary", "org");

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    // GAP-L-71: entries are List<Map<String,Object>> after the DTO→Map adapter,
    // not List<FeeLineDto>. Each row is a Jackson-serialised view of FeeLineDto.
    List<Map<String, Object>> entries = (List<Map<String, Object>>) fees.get("entries");
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0)).isInstanceOf(Map.class);
    // 60min = 1.00h * 1500 = 1500; 90min = 1.50h * 2000 = 3000; total = 4500
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(new BigDecimal("4500.00"));
    assertThat((BigDecimal) fees.get("total_hours")).isEqualByComparingTo(new BigDecimal("2.50"));

    @SuppressWarnings("unchecked")
    Map<String, Object> disb = (Map<String, Object>) context.get("disbursements");
    assertThat((BigDecimal) disb.get("total")).isEqualByComparingTo(new BigDecimal("287.50"));

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat((BigDecimal) trust.get("opening_balance"))
        .isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat((BigDecimal) trust.get("closing_balance"))
        .isEqualByComparingTo(new BigDecimal("400.00"));

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    // closing = 0 + 4500 + 287.50 - 0
    assertThat((BigDecimal) summary.get("closing_balance_owing"))
        .isEqualByComparingTo(new BigDecimal("4787.50"));
  }

  @Test
  void emptyPeriod_everyListEmpty_everyAggregateZero() {
    var project = projectWithCustomer("Empty Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Quiet Client")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    assertThat(((List<?>) fees.get("entries"))).isEmpty();
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) fees.get("total_hours")).isEqualByComparingTo(BigDecimal.ZERO);

    @SuppressWarnings("unchecked")
    Map<String, Object> disb = (Map<String, Object>) context.get("disbursements");
    assertThat(((List<?>) disb.get("entries"))).isEmpty();
    assertThat((BigDecimal) disb.get("total")).isEqualByComparingTo(BigDecimal.ZERO);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat(((List<?>) trust.get("deposits"))).isEmpty();
    assertThat(((List<?>) trust.get("payments"))).isEmpty();
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) trust.get("closing_balance")).isEqualByComparingTo(BigDecimal.ZERO);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    assertThat((BigDecimal) summary.get("total_fees")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("total_disbursements"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("previous_balance_owing"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("payments_received")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("closing_balance_owing"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("trust_balance_held"))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void trustBlock_classifiesEveryCanonicalLedgerType() {
    // Every ledger type produced by the trust subsystem must land in either deposits or
    // payments. If a type is dropped here the rendered statement stops reconciling with the
    // opening/closing balances. The credit/debit sets mirror ClientLedgerService and
    // TrustReconciliationService.
    var project = projectWithCustomer("Coverage Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(BigDecimal.ZERO);

    var lines =
        List.of(
            ledgerLine("DEPOSIT", new BigDecimal("100.00")),
            ledgerLine("TRANSFER_IN", new BigDecimal("200.00")),
            ledgerLine("INTEREST_CREDIT", new BigDecimal("5.00")),
            ledgerLine("PAYMENT", new BigDecimal("50.00")),
            ledgerLine("DISBURSEMENT_PAYMENT", new BigDecimal("60.00")),
            ledgerLine("TRANSFER_OUT", new BigDecimal("70.00")),
            ledgerLine("FEE_TRANSFER", new BigDecimal("80.00")),
            ledgerLine("REFUND", new BigDecimal("90.00")),
            ledgerLine("INTEREST_LPFF", new BigDecimal("3.00")),
            ledgerLine("UNKNOWN_TYPE_X", new BigDecimal("1.00")));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(new LedgerStatementResponse(BigDecimal.ZERO, BigDecimal.ZERO, lines));

    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    @SuppressWarnings("unchecked")
    List<?> deposits = (List<?>) trust.get("deposits");
    @SuppressWarnings("unchecked")
    List<?> payments = (List<?>) trust.get("payments");

    // Credits: DEPOSIT, TRANSFER_IN, INTEREST_CREDIT (3 lines).
    assertThat(deposits).hasSize(3);
    // Debits: PAYMENT, DISBURSEMENT_PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_LPFF
    // (6 lines). UNKNOWN_TYPE_X is intentionally dropped — the assertion guards against the
    // classifier silently absorbing types it doesn't recognise.
    assertThat(payments).hasSize(6);
  }

  @Test
  void periodFilter_excludesOutOfRangeFees() {
    var project = projectWithCustomer("Filtered Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    // Repository returns ONLY the in-range entries because findByFilters honours the date bounds.
    var inRange = timeEntry(LocalDate.of(2026, 4, 15), 120, true, new BigDecimal("1000.00"), "In");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(inRange));
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    // GAP-L-71: entries are List<Map<String,Object>> after DTO→Map adapter.
    List<Map<String, Object>> entries = (List<Map<String, Object>>) fees.get("entries");
    assertThat(entries).hasSize(1);
    // Description and date both round-trip through the DTO→Map adapter; date is serialised as
    // an ISO string by Jackson 3's built-in JSR-310 support.
    assertThat(entries.get(0).get("description")).isEqualTo("In");
    assertThat(entries.get(0).get("date").toString()).isEqualTo("2026-04-15");
    // 120min = 2h * 1000 = 2000
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(new BigDecimal("2000.00"));
  }

  @Test
  void periodFilter_excludesOutOfRangeDisbursements() {
    var project = projectWithCustomer("Disb Filter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    var inRange =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 20),
            DisbursementCategory.COUNSEL_FEES,
            "Counsel",
            new BigDecimal("300.00"),
            new BigDecimal("45.00"),
            "Adv Smith",
            "REF-CNSL");
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of(inRange));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> disb = (Map<String, Object>) context.get("disbursements");
    @SuppressWarnings("unchecked")
    // GAP-L-71: entries are List<Map<String,Object>> after DTO→Map adapter.
    List<Map<String, Object>> entries = (List<Map<String, Object>>) disb.get("entries");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("description")).isEqualTo("Counsel");
    assertThat(entries.get(0).get("incurredDate").toString()).isEqualTo("2026-04-20");
    assertThat((BigDecimal) disb.get("total")).isEqualByComparingTo(new BigDecimal("345.00"));
  }

  @Test
  void trustOpeningClosing_computedAtExactPeriodBounds() throws Exception {
    var project = projectWithCustomer("Trust Bounds", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    // Opening read at periodStart, closing at periodEnd.
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(new BigDecimal("750.00"));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(new BigDecimal("1250.00"));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                new BigDecimal("750.00"), new BigDecimal("1250.00"), List.of()));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat((BigDecimal) trust.get("opening_balance"))
        .isEqualByComparingTo(new BigDecimal("750.00"));
    assertThat((BigDecimal) trust.get("closing_balance"))
        .isEqualByComparingTo(new BigDecimal("1250.00"));

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    assertThat((BigDecimal) summary.get("trust_balance_held"))
        .isEqualByComparingTo(new BigDecimal("1250.00"));
  }

  @Test
  void summaryClosing_equalsPreviousPlusFeesPlusDisbursementsMinusPayments() {
    var project = projectWithCustomer("Summary Math", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var te = timeEntry(LocalDate.of(2026, 4, 8), 60, true, new BigDecimal("1000.00"), "Work");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te));
    var disb =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 9),
            DisbursementCategory.COURT_FEES,
            "Court",
            new BigDecimal("200.00"),
            new BigDecimal("30.00"),
            "Court Registry",
            "REF-CRT");
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of(disb));

    // Previous balance: an old SENT invoice (issueDate before periodStart) with R500 outstanding.
    var oldInvoice =
        invoiceWithStatusTotalAndIssueDate(
            InvoiceStatus.SENT, new BigDecimal("500.00"), periodStart.minusDays(10));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of(oldInvoice));
    when(paymentEventRepository.findByInvoiceIdInOrderByCreatedAtDesc(anyCollection()))
        .thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    // fees = 1h * 1000 = 1000; disbursements = 230; previous = 500; payments = 0
    // closing = 500 + 1000 + 230 - 0 = 1730
    assertThat((BigDecimal) summary.get("total_fees"))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat((BigDecimal) summary.get("total_disbursements"))
        .isEqualByComparingTo(new BigDecimal("230.00"));
    assertThat((BigDecimal) summary.get("previous_balance_owing"))
        .isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat((BigDecimal) summary.get("payments_received")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) summary.get("closing_balance_owing"))
        .isEqualByComparingTo(new BigDecimal("1730.00"));
  }

  @Test
  void previousBalance_includesInvoicesIssuedBeforePeriodEvenIfLaterMarkedPaid() {
    // An invoice issued in March (R800) for which payment arrives in April. At the start of
    // the April statement period the customer DID owe R800 — the opening balance must reflect
    // that, with the April payment then showing in payments_received. Filtering by SENT-only
    // would silently drop the PAID-now invoice, understating the previous balance to R0 and
    // turning the statement into a misleading "closing-only" view.
    var project = projectWithCustomer("Carryover Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());

    var carryover =
        invoiceWithStatusTotalAndIssueDate(
            InvoiceStatus.PAID, new BigDecimal("800.00"), periodStart.minusDays(20));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of(carryover));
    // No payments BEFORE periodStart — the payment lands inside the period and is captured by
    // paymentsReceivedInPeriod, not paymentsBefore.
    when(paymentEventRepository.findByInvoiceIdInOrderByCreatedAtDesc(anyCollection()))
        .thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    assertThat((BigDecimal) summary.get("previous_balance_owing"))
        .isEqualByComparingTo(new BigDecimal("800.00"));
  }

  @Test
  void previousBalance_excludesVoidedInvoices() {
    var project = projectWithCustomer("Voided Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());

    var voided =
        invoiceWithStatusTotalAndIssueDate(
            InvoiceStatus.VOID, new BigDecimal("999.00"), periodStart.minusDays(5));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of(voided));

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    assertThat((BigDecimal) summary.get("previous_balance_owing"))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void feeAmount_computedFromRawMinutesPreservesPrecision() {
    // 7-minute entry at R1000/h. Display hours are rounded to 2dp (0.12h), but the amount
    // must come from the raw minutes: 7/60 * 1000 = 116.6667 → R116.67. Computing amount
    // from the rounded display hours (0.12 * 1000 = 120.00) overcharges by ~3%.
    var project = projectWithCustomer("Precise Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var te = timeEntry(LocalDate.of(2026, 4, 10), 7, true, new BigDecimal("1000.00"), "Quick call");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te));
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    // GAP-L-71: entries are List<Map<String,Object>> after DTO→Map adapter.
    List<Map<String, Object>> entries = (List<Map<String, Object>>) fees.get("entries");
    assertThat(entries).hasSize(1);
    // Display hours: 7/60 = 0.1166... → 0.12 at 2dp HALF_UP.
    assertThat(new BigDecimal(entries.get(0).get("hours").toString()))
        .isEqualByComparingTo(new BigDecimal("0.12"));
    // Amount from raw minutes: 7/60 * 1000 = 116.666... → 116.67. NOT 0.12 * 1000 = 120.00.
    assertThat(new BigDecimal(entries.get(0).get("amount").toString()))
        .isEqualByComparingTo(new BigDecimal("116.67"));
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(new BigDecimal("116.67"));
  }

  @Test
  void trustAccountingDisabled_trustBlockEmpty_ledgerNeverQueried() {
    // Override default: simulate a tenant with disbursements enabled but trust_accounting DISABLED.
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);

    var project = projectWithCustomer("No Trust Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat(((List<?>) trust.get("deposits"))).isEmpty();
    assertThat(((List<?>) trust.get("payments"))).isEmpty();
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) trust.get("closing_balance")).isEqualByComparingTo(BigDecimal.ZERO);

    // Crucially, the ClientLedgerService is never hit — avoids ModuleNotEnabledException (403).
    verifyNoInteractions(clientLedgerService);
    // TrustTransaction + TrustAccount lookups are also skipped (the module-guard short-circuit
    // sits before the customer-activity lookup introduced in GAP-L-94).
    verifyNoInteractions(trustTransactionRepository);
    verify(trustAccountRepository, never()).findByAccountTypeAndPrimaryTrue(any());
  }

  // ---------- GAP-L-71 regression: typed DTO lists must be converted to List<Map> ----------

  /**
   * Direct shape assertion that the DTO→Map adapter on the loop-table data sources is in place. The
   * renderer's {@code renderLoopTable} blows up with {@code ClassCastException} at line 309 if any
   * row is not a {@code Map} — the assertion here catches the type drift at the model-build site
   * before it ever reaches the renderer.
   */
  @Test
  void loopTableSources_areListOfMap_notTypedDtos() {
    var project = projectWithCustomer("DTO→Map Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var fee = timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1500.00"), "Drafting");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(fee));

    var disb =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 6),
            DisbursementCategory.SHERIFF_FEES,
            "Sheriff Fees",
            new BigDecimal("1250.00"),
            new BigDecimal("0.00"),
            "Sheriff",
            "REF-1");
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of(disb));

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                    ledgerLine("DEPOSIT", new BigDecimal("100.00")),
                    ledgerLine("PAYMENT", new BigDecimal("50.00")))));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    var fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    var feeEntries = (List<Object>) fees.get("entries");
    assertThat(feeEntries).hasSize(1);
    assertThat(feeEntries.get(0))
        .as(
            "fees.entries rows MUST be Map (GAP-L-71) — TiptapRenderer.renderLoopTable casts each row")
        .isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    var disbMap = (Map<String, Object>) context.get("disbursements");
    @SuppressWarnings("unchecked")
    var disbEntries = (List<Object>) disbMap.get("entries");
    assertThat(disbEntries).hasSize(1);
    assertThat(disbEntries.get(0))
        .as(
            "disbursements.entries rows MUST be Map (GAP-L-71) — this is the exact path that blew"
                + " up in QA Day 60 cycle 1 with ClassCastException on DisbursementStatementDto")
        .isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    var trustMap = (Map<String, Object>) context.get("trust");
    @SuppressWarnings("unchecked")
    var deposits = (List<Object>) trustMap.get("deposits");
    @SuppressWarnings("unchecked")
    var payments = (List<Object>) trustMap.get("payments");
    assertThat(deposits).isNotEmpty();
    assertThat(deposits.get(0))
        .as("trust.deposits rows MUST be Map (GAP-L-71)")
        .isInstanceOf(Map.class);
    assertThat(payments).isNotEmpty();
    assertThat(payments.get(0))
        .as("trust.payments rows MUST be Map (GAP-L-71)")
        .isInstanceOf(Map.class);
  }

  /**
   * Render-roundtrip: build context with non-empty typed-DTO lists for all three loop sources, then
   * render the actual installed SoA template through {@link TiptapRenderer}. Before GAP-L-71's fix
   * this would throw {@code ClassCastException} at {@code TiptapRenderer:309} on the first
   * iteration of the disbursements loop. The assertion that the rendered HTML contains the
   * disbursement description ("Sheriff Fees") + the fee narrative ("Drafting motion") + trust
   * transaction reference ("DEP-001") catches any future template that adds another loop-table on a
   * typed-DTO list without re-applying the adapter.
   */
  @Test
  void renderRoundtrip_realSoATemplate_doesNotThrowAndContainsAllLoopRows() throws Exception {
    var project = projectWithCustomer("Roundtrip Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var fee =
        timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1500.00"), "Drafting motion");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(fee));

    var disb =
        new DisbursementStatementDto(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 6),
            DisbursementCategory.SHERIFF_FEES,
            "Sheriff Fees",
            new BigDecimal("1250.00"),
            new BigDecimal("0.00"),
            "Sheriff",
            "REF-1");
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of(disb));

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(BigDecimal.ZERO);
    var dep =
        new ClientLedgerService.LedgerStatementLine(
            UUID.randomUUID(),
            "DEPOSIT",
            new BigDecimal("500.00"),
            "DEP-001",
            "Initial deposit",
            LocalDate.of(2026, 4, 7),
            "POSTED",
            BigDecimal.ZERO);
    var pay =
        new ClientLedgerService.LedgerStatementLine(
            UUID.randomUUID(),
            "PAYMENT",
            new BigDecimal("200.00"),
            "PAY-001",
            "Outgoing payment",
            LocalDate.of(2026, 4, 9),
            "POSTED",
            BigDecimal.ZERO);
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(BigDecimal.ZERO, BigDecimal.ZERO, List.of(dep, pay)));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    // Load the actual installed SoA template content from the legal-za pack so the test exercises
    // the same node tree that production renders. Any future template change that introduces
    // another loop-table on a typed-DTO source will fail this round-trip.
    var templateBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("template-packs/legal-za/statement-of-account.json")
            .readAllBytes();
    @SuppressWarnings("unchecked")
    Map<String, Object> templateNode = objectMapper.readValue(templateBytes, Map.class);

    // Use the production constructor (package-private overload is not visible from this package).
    // ByteArrayResource lets us inject the CSS string without a real classpath lookup.
    var renderer =
        new io.b2mash.b2b.b2bstrawman.template.TiptapRenderer(
            new org.springframework.core.io.ByteArrayResource(
                "body { font-size: 11pt; }".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    // Pre-fix this throws java.lang.ClassCastException: DisbursementStatementDto cannot be cast
    // to java.util.Map at TiptapRenderer.renderLoopTable line 309.
    String html = renderer.render(templateNode, context, Map.of(), null, Map.of());

    assertThat(html)
        .as("Render must include the fee narrative from the fees.entries loop")
        .contains("Drafting motion")
        .as("Render must include the disbursement description from the disbursements.entries loop")
        .contains("Sheriff Fees")
        .as("Render must include the trust deposit reference from the trust.deposits loop")
        .contains("DEP-001")
        .as("Render must include the trust payment reference from the trust.payments loop")
        .contains("PAY-001")
        // GAP-OBS-Day60-SoA-Fees/Trust-Empty: the deposit / payment loop columns iterate the
        // template keys `transactionDate` and `transactionType`. The previous TrustTxDto used
        // `date` / `type`, so each row's date and type cells rendered as empty <td></td> blocks
        // even when the data was populated. Asserting the rendered date strings (as formatted
        // by VariableFormatter in the legal-za locale: "d MMMM yyyy") and the literal PAYMENT
        // type pin the column-key alignment so the regression cannot return silently.
        .as("Render must include the trust deposit transactionDate (column key alignment)")
        .contains("7 April 2026")
        .as("Render must include the trust payment transactionDate (column key alignment)")
        .contains("9 April 2026")
        .as("Render must include the trust payment transactionType (column key alignment)")
        .contains("PAYMENT");
  }

  /**
   * Direct shape assertion that the trust loop-table rows expose {@code transactionDate} and {@code
   * transactionType} keys (matching the SoA template's column attributes). Pins the model→ template
   * contract at the builder boundary so a future TrustTxDto rename caught here, not in
   * silently-blank rendered output.
   */
  @Test
  void trustLoopRows_exposeTransactionDateAndTransactionType_matchingTemplateColumnKeys()
      throws Exception {
    var project = projectWithCustomer("Key Alignment Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                    ledgerLine("DEPOSIT", new BigDecimal("100.00")),
                    ledgerLine("PAYMENT", new BigDecimal("50.00")))));
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    var trust = (Map<String, Object>) context.get("trust");
    @SuppressWarnings("unchecked")
    var deposits = (List<Map<String, Object>>) trust.get("deposits");
    @SuppressWarnings("unchecked")
    var payments = (List<Map<String, Object>>) trust.get("payments");

    assertThat(deposits).hasSize(1);
    assertThat(deposits.get(0))
        .as(
            "Deposit row must expose transactionDate (template column key) — not `date`."
                + " GAP-OBS-Day60-SoA-Fees/Trust-Empty regression guard.")
        .containsKey("transactionDate");
    assertThat(deposits.get(0))
        .as(
            "Deposit row must expose transactionType (template column key) — not `type`."
                + " GAP-OBS-Day60-SoA-Fees/Trust-Empty regression guard.")
        .containsKey("transactionType");
    assertThat(deposits.get(0).get("transactionType")).isEqualTo("DEPOSIT");

    assertThat(payments).hasSize(1);
    assertThat(payments.get(0))
        .as("Payment row must expose transactionDate (template column key) — not `date`.")
        .containsKey("transactionDate");
    assertThat(payments.get(0))
        .as("Payment row must expose transactionType (template column key) — not `type`.")
        .containsKey("transactionType");
    assertThat(payments.get(0).get("transactionType")).isEqualTo("PAYMENT");
  }

  // ---------- GAP-L-95: VAT applied on fees aggregate ----------

  @Test
  void aggregate_appliesVatWhenDefaultTaxRateConfigured() {
    var project = projectWithCustomer("VAT Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    // 60min @ R1500 + 90min @ R2000 → R1500 + R3000 = R3000 + R1500 = R4500 excl.
    // Wait: 60min/60 = 1.0h, 1.0 * 1500 = R1500. 90min/60 = 1.5h, 1.5 * 2000 = R3000.
    // Excl total = R4500. With 15% VAT: R675. Incl = R5175.
    var te1 = timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1500.00"), "Drafting");
    var te2 = timeEntry(LocalDate.of(2026, 4, 10), 90, true, new BigDecimal("2000.00"), "Hearing");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te1, te2));
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    var rate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
    when(taxRateRepository.findByIsDefaultTrue()).thenReturn(Optional.of(rate));

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(new BigDecimal("4500.00"));
    assertThat((BigDecimal) fees.get("vat_amount")).isEqualByComparingTo(new BigDecimal("675.00"));
    assertThat((BigDecimal) fees.get("total_amount_incl_vat"))
        .isEqualByComparingTo(new BigDecimal("5175.00"));
  }

  @Test
  void aggregate_skipsVatWhenNoDefaultRateConfigured() {
    var project = projectWithCustomer("No VAT Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var te = timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1000.00"), "Work");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te));
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());
    // Default-rate lookup returns empty (lenient setUp default — explicit here for clarity).
    when(taxRateRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    assertThat((BigDecimal) fees.get("total_amount_excl_vat"))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat((BigDecimal) fees.get("vat_amount")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) fees.get("total_amount_incl_vat"))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
  }

  @Test
  void aggregate_skipsVatWhenDefaultRateIsExempt() {
    var project = projectWithCustomer("Exempt Matter", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    var te = timeEntry(LocalDate.of(2026, 4, 5), 60, true, new BigDecimal("1000.00"), "Work");
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of(te));
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());
    // Exempt rate (e.g., service for an export client). Even with isDefault=true, VAT must be 0.
    var exempt = new TaxRate("Exempt", new BigDecimal("0.00"), true, true, 0);
    when(taxRateRepository.findByIsDefaultTrue()).thenReturn(Optional.of(exempt));

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    assertThat((BigDecimal) fees.get("vat_amount")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) fees.get("total_amount_incl_vat"))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
  }

  // ---------- GAP-L-94: trust block resolves customer-active trust account ----------

  @Test
  void buildTrustBlock_resolvesAccountFromCustomerActivity_returnsAllRecordedDeposits() {
    // Customer has activity on a single trust account that is NOT necessarily the primary
    // GENERAL — the SoA must still surface it. Pre-fix `findByAccountTypeAndPrimaryTrue` would
    // either return null or a different id, hiding the trust activity.
    var project = projectWithCustomer("Sipho RAF", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Sipho")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(trustAccountId));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(new BigDecimal("70100.00"));
    var dep1 = ledgerLine("DEPOSIT", new BigDecimal("50000.00"));
    var dep2 = ledgerLine("DEPOSIT", new BigDecimal("100.00"));
    var dep3 = ledgerLine("DEPOSIT", new BigDecimal("20000.00"));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                BigDecimal.ZERO, new BigDecimal("70100.00"), List.of(dep1, dep2, dep3)));

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    @SuppressWarnings("unchecked")
    List<?> deposits = (List<?>) trust.get("deposits");
    assertThat(deposits).hasSize(3);
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) trust.get("closing_balance"))
        .isEqualByComparingTo(new BigDecimal("70100.00"));
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) context.get("summary");
    assertThat((BigDecimal) summary.get("trust_balance_held"))
        .isEqualByComparingTo(new BigDecimal("70100.00"));
  }

  @Test
  void buildTrustBlock_returnsEmpty_whenCustomerHasNoTrustActivity() {
    var project = projectWithCustomer("No Trust Customer", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));

    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());
    // Default lenient stub returns empty list — explicit here for clarity.
    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat(((List<?>) trust.get("deposits"))).isEmpty();
    assertThat(((List<?>) trust.get("payments"))).isEmpty();
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) trust.get("closing_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    // The ledger service must not be queried for a customer with no activity (avoids redundant
    // db traffic and stops a tenant-wide audit trail of empty-result trust queries).
    verifyNoInteractions(clientLedgerService);
  }

  @Test
  void buildTrustBlock_multiAccountCustomer_prefersPrimaryGeneralWhenItHasActivity() {
    // Customer holds funds on two accounts — primary GENERAL and a secondary. The SoA must pick
    // the primary GENERAL when the customer has activity on it (matches the operational shape
    // ZA Section 86 firms expect: a primary trust account holds the bulk of client funds).
    UUID secondaryAccountId = UUID.randomUUID();
    var project = projectWithCustomer("Multi-Account", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(secondaryAccountId, trustAccountId));
    var primaryGeneral = trustAccountWithId(trustAccountId);
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.of(primaryGeneral));
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, trustAccountId, periodEnd))
        .thenReturn(new BigDecimal("500.00"));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, trustAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                BigDecimal.ZERO,
                new BigDecimal("500.00"),
                List.of(ledgerLine("DEPOSIT", new BigDecimal("500.00")))));

    var context = builder.build(projectId, periodStart, periodEnd);

    // Primary GENERAL was preferred → balance pulled from THAT account, not the secondary.
    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat((BigDecimal) trust.get("closing_balance"))
        .isEqualByComparingTo(new BigDecimal("500.00"));
    // Verify the ledger query targeted the primary GENERAL id, not the secondary.
    verify(clientLedgerService).getClientBalanceAsOfDate(customerId, trustAccountId, periodStart);
  }

  @Test
  void buildTrustBlock_multiAccountCustomer_fallsBackToFirstWhenNoPrimaryGeneral() {
    // No primary GENERAL exists (or it is not in the customer's activity set). Fall back to
    // the first account id returned. Section 86 compliance still surfaces the activity.
    UUID firstAccountId = UUID.randomUUID();
    UUID secondAccountId = UUID.randomUUID();
    var project = projectWithCustomer("Multi-Account-NoGeneral", customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer("Client")));
    when(timeEntryRepository.findByFilters(eq(projectId), eq(null), eq(periodStart), eq(periodEnd)))
        .thenReturn(List.of());
    when(disbursementService.listForStatement(projectId, periodStart, periodEnd))
        .thenReturn(List.of());
    when(invoiceRepository.findByProjectId(projectId)).thenReturn(List.of());

    when(trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId))
        .thenReturn(List.of(firstAccountId, secondAccountId));
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.empty());
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, firstAccountId, periodStart))
        .thenReturn(BigDecimal.ZERO);
    when(clientLedgerService.getClientBalanceAsOfDate(customerId, firstAccountId, periodEnd))
        .thenReturn(new BigDecimal("250.00"));
    when(clientLedgerService.getClientLedgerStatement(
            customerId, firstAccountId, periodStart, periodEnd))
        .thenReturn(
            new LedgerStatementResponse(
                BigDecimal.ZERO,
                new BigDecimal("250.00"),
                List.of(ledgerLine("DEPOSIT", new BigDecimal("250.00")))));

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat((BigDecimal) trust.get("closing_balance"))
        .isEqualByComparingTo(new BigDecimal("250.00"));
    verify(clientLedgerService).getClientBalanceAsOfDate(customerId, firstAccountId, periodStart);
  }

  // ---------- helpers ----------

  private Project projectWithCustomer(String name, UUID customerId) {
    var p = new Project(name, "test matter", memberId);
    p.setCustomerId(customerId);
    TestIds.withId(p, projectId);
    return p;
  }

  private Customer customer(String name) {
    var c = new Customer(name, "client@test.com", null, null, null, memberId);
    TestIds.withId(c, customerId);
    return c;
  }

  private TimeEntry timeEntry(
      LocalDate date, int minutes, boolean billable, BigDecimal rate, String description) {
    var te = new TimeEntry(taskId, memberId, date, minutes, billable, null, description);
    te.snapshotBillingRate(rate, "ZAR");
    return te;
  }

  private TrustAccount trustAccountWithId(UUID id) {
    var ta =
        new TrustAccount(
            "General Trust",
            "Bank",
            "001",
            "1234567",
            TrustAccountType.GENERAL,
            true,
            false,
            null,
            LocalDate.of(2024, 1, 1),
            null);
    TestIds.withId(ta, id);
    return ta;
  }

  private LedgerStatementLine ledgerLine(String type, BigDecimal amount) {
    return new LedgerStatementLine(
        UUID.randomUUID(),
        type,
        amount,
        "REF",
        "desc",
        LocalDate.of(2026, 4, 15),
        "POSTED",
        BigDecimal.ZERO);
  }

  private Invoice invoiceWithStatusTotalAndIssueDate(
      InvoiceStatus status, BigDecimal total, LocalDate issueDate) {
    var inv = new Invoice(customerId, "ZAR", "Client", "c@t.com", "addr", "Org", memberId);
    TestIds.withId(inv, UUID.randomUUID());
    TestIds.withField(inv, "status", status);
    TestIds.withField(inv, "total", total);
    TestIds.withField(inv, "issueDate", issueDate);
    return inv;
  }
}
