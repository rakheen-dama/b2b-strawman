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
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.FeeLineDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementResponse;
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
    var trustAccount = trustAccountWithId(trustAccountId);
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.of(trustAccount));
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

    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    assertThat(context)
        .containsKeys(
            "statement", "matter", "customer", "fees", "disbursements", "trust", "summary", "org");

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    List<FeeLineDto> entries = (List<FeeLineDto>) fees.get("entries");
    assertThat(entries).hasSize(2);
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
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.empty());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

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
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.empty());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> fees = (Map<String, Object>) context.get("fees");
    @SuppressWarnings("unchecked")
    List<FeeLineDto> entries = (List<FeeLineDto>) fees.get("entries");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 15));
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
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.empty());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> disb = (Map<String, Object>) context.get("disbursements");
    @SuppressWarnings("unchecked")
    List<DisbursementStatementDto> entries = (List<DisbursementStatementDto>) disb.get("entries");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).incurredDate()).isEqualTo(LocalDate.of(2026, 4, 20));
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

    var trustAccount = trustAccountWithId(trustAccountId);
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.of(trustAccount));
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
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

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
    when(trustAccountRepository.findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL))
        .thenReturn(Optional.empty());

    // Previous balance: an old SENT invoice (issueDate before periodStart) with R500 outstanding.
    var oldInvoice =
        invoiceWithStatusTotalAndIssueDate(
            InvoiceStatus.SENT, new BigDecimal("500.00"), periodStart.minusDays(10));
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of(oldInvoice));
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
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

    var context = builder.build(projectId, periodStart, periodEnd);

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    assertThat(((List<?>) trust.get("deposits"))).isEmpty();
    assertThat(((List<?>) trust.get("payments"))).isEmpty();
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) trust.get("closing_balance")).isEqualByComparingTo(BigDecimal.ZERO);

    // Crucially, the ClientLedgerService is never hit — avoids ModuleNotEnabledException (403).
    verifyNoInteractions(clientLedgerService);
    // TrustAccount lookup is also skipped (short-circuit is before that).
    verify(trustAccountRepository, never()).findByAccountTypeAndPrimaryTrue(any());
  }

  // ---------- helpers ----------

  private Project projectWithCustomer(String name, UUID customerId) {
    var p = new Project(name, "test matter", memberId);
    p.setCustomerId(customerId);
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
