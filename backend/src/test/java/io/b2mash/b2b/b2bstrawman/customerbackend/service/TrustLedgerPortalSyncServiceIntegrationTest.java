package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link TrustLedgerPortalSyncService}. Exercises the full event-listener +
 * read-model stack on embedded Postgres (no mocks).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustLedgerPortalSyncServiceIntegrationTest {

  private static final String ORG_ID = "org_portal_trust_sync_test";
  private static final String ORG_NAME = "Portal Trust Sync Test Org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private TrustPostingService trustPostingService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PortalTrustReadModelRepository portalTrustRepo;
  @Autowired private TrustLedgerPortalSyncService syncService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustLedgerQueryRepository ledgerQueryRepository;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String tenantSchema;
  private UUID customerId;
  private UUID matterId;
  private UUID trustAccountId;
  private UUID memberId;
  private TransactionTemplate tenantTxTemplate;
  private UUID approverId;
  private TrustBooksFixture.Tenant books;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_trust_sync"));
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    tenantTxTemplate = new TransactionTemplate(transactionManager);
    // 648D: the investment cases run through the services (TrustBooksFixture); a placement is
    // approved by someone other than its maker.
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_portal_trust_sync_approver",
                "portal_trust_sync_approver@test.com",
                "Portal Sync Approver",
                "admin"));
    books = fixture.tenant(tenantSchema, ORG_ID, memberId);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var customer =
                          customerService.createCustomer(
                              "Trust Sync Customer",
                              "trust-sync@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerId = customer.getId();

                      var project = new Project("Trust Sync Matter", "Test matter", memberId);
                      project.setCustomerId(customerId);
                      project = projectRepository.saveAndFlush(project);
                      matterId = project.getId();

                      var trustAccount =
                          new TrustAccount(
                              "Portal Sync Trust",
                              "Test Bank",
                              "250655",
                              "6200-000-SYNC",
                              TrustAccountType.GENERAL,
                              true,
                              false,
                              null,
                              LocalDate.of(2026, 1, 1),
                              "Seed trust account for portal sync tests");
                      trustAccount = trustAccountRepository.saveAndFlush(trustAccount);
                      trustAccountId = trustAccount.getId();
                    }));
  }

  @BeforeEach
  void clearPortalState() {
    // Portal read-model is shared across test methods (PER_CLASS lifecycle); wipe between each
    // test so event-listener assertions see only the rows produced by the method under test.
    // Also wipe firm-side trust_transactions so each test starts from a clean ledger (running
    // balance assertions depend on prior non-backfill rows not leaking in).
    if (customerId != null) {
      portalTrustRepo.deleteTransactionsByCustomer(customerId);
      portalTrustRepo.deleteBalancesByCustomer(customerId);
    }
    // A reversal links rows to each other (reversal_of / reversed_by_id self-FKs, no cascade):
    // unlink them first so deleteAll can remove the rows in any order (649B).
    jdbcTemplate.update(
        "UPDATE \"%s\".trust_transactions SET reversal_of = NULL, reversed_by_id = NULL"
            .formatted(tenantSchema));
    // 648A: an investment's rows name it (investment_id) and it names its placement row
    // (deposit_transaction_id) — delete its rows first, then the investments. 648D: interest
    // entries name the investment and their rows; an investment placed through the services names
    // its placement and withdrawal rows.
    jdbcTemplate.update("DELETE FROM \"%s\".trust_investment_interest".formatted(tenantSchema));
    jdbcTemplate.update(
        ("UPDATE \"%s\".trust_investments SET deposit_transaction_id = NULL,"
                + " withdrawal_transaction_id = NULL")
            .formatted(tenantSchema));
    jdbcTemplate.update(
        "DELETE FROM \"%s\".trust_transactions WHERE investment_id IS NOT NULL"
            .formatted(tenantSchema));
    jdbcTemplate.update("DELETE FROM \"%s\".trust_investments".formatted(tenantSchema));
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> trustTransactionRepository.deleteAll()));
  }

  // ==========================================================================
  // Event listener: trust_transaction.approved
  // ==========================================================================

  @Test
  void approvedTransactionEventUpsertsPortalTransactionAndBalance() {
    UUID txnId =
        recordApprovedTransaction(
            "DEPOSIT", new BigDecimal("1500.00"), "DEP-SYNC-1", "Initial deposit");

    publishApprovedEvent(txnId, "DEPOSIT", new BigDecimal("1500.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("1500.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("1500.00");
    assertThat(transactions.getFirst().description()).isEqualTo("Initial deposit");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("1500.00");
    assertThat(balance.lastTransactionAt()).isNotNull();
  }

  // ==========================================================================
  // Event listener: trust_transaction.recorded (GAP-L-52 regression guard)
  // — direct-RECORDED deposits bypass the awaiting_approval/approved lifecycle,
  // so the portal sync must fire off a separate RECORDED event. Before the fix
  // the portal /trust view stayed empty because the listener only filtered on
  // "trust_transaction.approved".
  // ==========================================================================

  @Test
  void recordedTransactionEventUpsertsPortalTransactionAndBalance() {
    UUID txnId =
        recordRecordedTransaction(
            "DEPOSIT", new BigDecimal("2500.00"), "DEP-REC-1", "Direct-recorded deposit");

    publishRecordedEvent(txnId, "DEPOSIT", new BigDecimal("2500.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("2500.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("2500.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("2500.00");
    assertThat(balance.lastTransactionAt()).isNotNull();
  }

  // ==========================================================================
  // End-to-end: calling TrustTransactionService.recordDeposit triggers the
  // full publish → AFTER_COMMIT listener → portal upsert chain. Before the
  // fix recordDeposit never published, so the portal rows stayed empty.
  // ==========================================================================

  @Test
  void recordDepositEndToEndPopulatesPortalReadModel() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        trustTransactionService.recordDeposit(
                            trustAccountId,
                            new RecordDepositRequest(
                                customerId,
                                matterId,
                                new BigDecimal("4200.00"),
                                "DEP-E2E-1",
                                "End-to-end deposit",
                                LocalDate.of(2026, 4, 5)))));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions)
        .as("Portal trust transaction must be populated for direct-RECORDED deposits")
        .hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("4200.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("4200.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("4200.00");
  }

  // ==========================================================================
  // Phase 92 648D (§16.6.3): rows split by ledger. CLIENT rows drive the general
  // running balance as before; every CLIENT_INVESTMENT row drives the invested
  // balance, and of those only INV_INTEREST is a visible row — INV_RECEIVE moves
  // the invested balance without a line. INV_PLACE / INV_RETURN are CLIENT-ledger
  // rows and are projected as ordinary rows.
  // ==========================================================================

  @Test
  void investmentInterestIsAVisibleRowAndTheInvestedLedgerStaysOffTheGeneralBalance() {
    UUID deposit =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("1000.00"),
            "INV-SYNC-DEP",
            "Deposit before the investment",
            LocalDate.of(2026, 2, 1));
    var investmentAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_accounts (id, account_name, bank_name, branch_code, account_number,
            account_type, is_primary, require_dual_approval, status, opened_date, investment_basis)
        VALUES (?, 'Portal Sync s86(4)', 'Investec', '580105', ?, 'INVESTMENT', false, false,
            'ACTIVE', DATE '2026-01-01', 'CLIENT_INSTRUCTION')
        """
            .formatted(tenantSchema),
        investmentAccountId,
        "INV-" + investmentAccountId.toString().substring(0, 8));
    var investmentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_investments (id, trust_account_id, investment_account_id,
            customer_id, project_id, institution, account_number, principal, interest_rate,
            deposit_date, investment_basis)
        VALUES (?, ?, ?, ?, ?, 'Investec', 'CALL-1', 200.00, 0.0500, DATE '2026-02-02',
            'CLIENT_INSTRUCTION')
        """
            .formatted(tenantSchema),
        investmentId,
        trustAccountId,
        investmentAccountId,
        customerId,
        matterId);
    // The placement (CLIENT ledger, client-visible) on the funding account; the capital received
    // and the interest (CLIENT_INVESTMENT ledger) on the investment account
    var placement =
        investmentRow(
            trustAccountId, "INV_PLACE", "200.00", investmentId, LocalDate.of(2026, 2, 2));
    investmentRow(
        investmentAccountId, "INV_RECEIVE", "200.00", investmentId, LocalDate.of(2026, 2, 2));
    var interest =
        investmentRow(
            investmentAccountId, "INV_INTEREST", "9.50", investmentId, LocalDate.of(2026, 2, 3));
    assertThat(TrustTransactionType.INV_INTEREST.clientVisible()).isTrue();

    publishRecordedEvent(deposit, "DEPOSIT", new BigDecimal("1000.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    // INV_RECEIVE is never a visible row; the interest credited to the client is
    assertThat(transactions)
        .extracting(t -> t.transactionType())
        .containsExactlyInAnyOrder("DEPOSIT", "INV_PLACE", "INV_INTEREST");
    assertThat(transactions)
        .filteredOn(t -> t.id().equals(placement))
        .singleElement()
        .satisfies(t -> assertThat(t.runningBalance()).isEqualByComparingTo("800.00"));
    assertThat(transactions)
        .filteredOn(t -> t.id().equals(placement))
        .singleElement()
        .satisfies(t -> assertThat(t.ledger()).isEqualTo("CLIENT"));
    // OQ-1 (a): the interest line carries the invested running balance (200 + 9,50)
    assertThat(transactions)
        .filteredOn(t -> t.id().equals(interest))
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.ledger()).isEqualTo("CLIENT_INVESTMENT");
              assertThat(t.runningBalance()).isEqualByComparingTo("209.50");
            });
    // The general balance is untouched by the investment ledger; the invested balance is it
    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("800.00");
    assertThat(balance.investedBalance()).isEqualByComparingTo("209.50");

    // Both projection paths apply the same split
    assertBackfillReproduces(customerId, matterId);
  }

  /** A posted investment row of {@code type} for the client and matter, written to the table. */
  private UUID investmentRow(
      UUID account, String type, String amount, UUID investmentId, LocalDate date) {
    var id = UUID.randomUUID();
    var effect = TrustTransactionType.of(type).effectFor(new BigDecimal(amount));
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, investment_id, reference, description, transaction_date,
            status, recorded_by, ledger, cashbook_effect, ledger_effect, posted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Investment row', ?, 'RECORDED', ?, ?, ?, ?, now())
        """
            .formatted(tenantSchema),
        id,
        account,
        type,
        new BigDecimal(amount),
        customerId,
        matterId,
        investmentId,
        "INV-SYNC-" + type,
        date,
        memberId,
        effect.ledger().name(),
        effect.cashbookEffect(),
        effect.ledgerEffect());
    return id;
  }

  // ==========================================================================
  // Phase 92 648D through the services (§16.6.3, R-11): a section 86(4)
  // placement, its interest, its reversal and its withdrawal reach the portal
  // through their own events. Only INV_INTEREST (and its contra, OQ-4) is a
  // visible investment-ledger row; non-client and inter-account rows never
  // reach the portal.
  // ==========================================================================

  @Test
  void investedBalanceFollowsASection864InvestmentThroughTheServices() {
    var held = s86_4Investment("10000.00", "7500.00");
    var client = held.client();
    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            netInterest(books.today().minusDays(10), "100.00"));

    var rows = portalTrustRepo.findTransactions(client.id(), client.matter(), null, null, 50, 0);
    // INV_RECEIVE moves the invested balance without a line; the client's 95% is a line
    assertThat(rows)
        .extracting(t -> t.transactionType())
        .containsExactlyInAnyOrder("DEPOSIT", "INV_PLACE", "INV_INTEREST");
    var interestRow =
        rows.stream()
            .filter(t -> t.id().equals(entry.netTransactionId()))
            .findFirst()
            .orElseThrow();
    assertThat(interestRow.transactionType()).isEqualTo("INV_INTEREST");
    assertThat(interestRow.ledger()).isEqualTo("CLIENT_INVESTMENT");
    assertThat(interestRow.amount()).isEqualByComparingTo("95.00");
    assertThat(interestRow.runningBalance()).isEqualByComparingTo("7595.00");
    assertThat(rows)
        .filteredOn(t -> !"INV_INTEREST".equals(t.transactionType()))
        .extracting(t -> t.ledger())
        .containsOnly("CLIENT");
    var balance = portalTrustRepo.findBalance(client.id(), client.matter()).orElseThrow();
    // A placement moves money between the two balances; the client's total is unchanged
    assertThat(balance.currentBalance()).isEqualByComparingTo("2500.00");
    assertThat(balance.investedBalance()).isEqualByComparingTo("7595.00");
    // The portal's invested balance is the firm's (648E investedBalancesByClient)
    assertThat(balance.investedBalance()).isEqualByComparingTo(firmInvested(held));

    // The withdrawal returns principal + net interest to the general ledger (INV_RETURN, a line);
    // INV_WITHDRAW empties the invested balance without a line
    books.withdraw(
        held.funding(),
        Expected.cashbookAndClient("7595.00"),
        held.investmentAccount(),
        Expected.cashbookAndInvestment("-7595.00"),
        held.investmentId(),
        books.today());

    var afterWithdrawal =
        portalTrustRepo.findTransactions(client.id(), client.matter(), null, null, 50, 0);
    assertThat(afterWithdrawal)
        .extracting(t -> t.transactionType())
        .containsExactlyInAnyOrder("DEPOSIT", "INV_PLACE", "INV_INTEREST", "INV_RETURN");
    var withdrawn = portalTrustRepo.findBalance(client.id(), client.matter()).orElseThrow();
    assertThat(withdrawn.currentBalance()).isEqualByComparingTo("10095.00");
    assertThat(withdrawn.investedBalance()).isEqualByComparingTo("0");
    assertThat(withdrawn.investedBalance()).isEqualByComparingTo(firmInvested(held));

    assertBackfillReproduces(client.id(), client.matter());
  }

  @Test
  void aReversedInterestShowsBothMovements() {
    var held = s86_4Investment("2000.00", "2000.00");
    var client = held.client();
    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            netInterest(books.today().minusDays(10), "100.00"));
    books.reverseInterest(
        held.investmentAccount(), Expected.cashbookAndInvestment("-95.00"), entry.id());
    var interestReference =
        books.read(
            () ->
                trustTransactionRepository
                    .findById(entry.netTransactionId())
                    .orElseThrow()
                    .getReference());

    // OQ-4 (a): the reversed interest and its contra are both lines, as on the general ledger
    var rows = portalTrustRepo.findTransactions(client.id(), client.matter(), null, null, 50, 0);
    assertThat(rows)
        .extracting(t -> t.transactionType())
        .containsExactlyInAnyOrder("DEPOSIT", "INV_PLACE", "INV_INTEREST", "REVERSAL");
    var contra =
        rows.stream().filter(t -> "REVERSAL".equals(t.transactionType())).findFirst().orElseThrow();
    assertThat(contra.amount()).isEqualByComparingTo("95.00");
    assertThat(contra.ledger()).isEqualTo("CLIENT_INVESTMENT");
    assertThat(contra.runningBalance()).isEqualByComparingTo("2000.00");
    assertThat(rows)
        .filteredOn(t -> "INV_INTEREST".equals(t.transactionType()))
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.ledger()).isEqualTo("CLIENT_INVESTMENT");
              assertThat(t.runningBalance()).isEqualByComparingTo("2095.00");
            });
    // 649B privacy rule: never the firm's reversal reason on a client surface
    assertThat(contra.description())
        .doesNotContain("interest entry reversed")
        .isEqualTo("Reversal — REV-" + interestReference);
    var balance = portalTrustRepo.findBalance(client.id(), client.matter()).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("0.00");
    // The invested balance is back to the principal, and still the firm's
    assertThat(balance.investedBalance()).isEqualByComparingTo("2000.00");
    assertThat(balance.investedBalance()).isEqualByComparingTo(firmInvested(held));

    assertBackfillReproduces(client.id(), client.matter());
  }

  @Test
  void nonClientAndInterAccountRowsNeverReachThePortal() {
    var funding = books.freshAccount(false);
    var client = books.newClient("portal non-client pin");
    books.deposit(funding, client, "5000.00");
    // The Fund's interest on the account (NON_CLIENT) and a section 86(3) placement
    // (INTER_ACCOUNT on both accounts) — none names a client or a matter
    // (chk_trust_txn_ledger_target)
    var fundInterest =
        books.postNonClientDirect(
            funding, "FUND_INTEREST", "40.00", TrustBooksFixture.POSTING_DATE);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var placed = books.requestPlacement(funding, firmAccount, null, "1500.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-1500.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("1500.00"),
        placed.depositTransactionId(),
        approverId);

    // Both accounts fan out (every matter of the account re-projects)
    publishReconciliationCompletedEvent(funding);
    publishReconciliationCompletedEvent(firmAccount);

    var rows = portalTrustRepo.findTransactions(client.id(), client.matter(), null, null, 50, 0);
    assertThat(rows).extracting(t -> t.transactionType()).containsExactly("DEPOSIT");
    var balance = portalTrustRepo.findBalance(client.id(), client.matter()).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("5000.00");
    assertThat(balance.investedBalance()).isEqualByComparingTo("0");
    var nonClientRowIds =
        books.read(
            () ->
                trustTransactionRepository.findAll().stream()
                    .filter(
                        t ->
                            t.getTrustAccountId().equals(funding)
                                || t.getTrustAccountId().equals(firmAccount))
                    .filter(t -> !"DEPOSIT".equals(t.getTransactionType()))
                    .map(TrustTransaction::getId)
                    .toList());
    assertThat(nonClientRowIds).contains(fundInterest).hasSize(3);
    assertThat(
            portalJdbc
                .sql("SELECT count(*) FROM portal.portal_trust_transaction WHERE id = ANY (?)")
                .params((Object) nonClientRowIds.toArray(new UUID[0]))
                .query(Long.class)
                .single())
        .as("no NON_CLIENT / INTER_ACCOUNT row reaches the portal")
        .isZero();
  }

  /**
   * Two rows on the same date recorded at the same instant are ordered by id alone. The backfill's
   * in-memory sort must break that tie in PostgreSQL's (unsigned byte) UUID order — the order of
   * {@code findByProjectIdOrderByTransactionDateAsc} and of the portal's read — or the two rows
   * carry each other's running balance (PR #1802 review). The ids differ in the top bit, where a
   * signed {@code UUID.compareTo} and PostgreSQL disagree.
   */
  @Test
  void backfillBreaksASameInstantTieInPostgresUuidOrder() {
    var funding = books.freshAccount(false);
    var client = books.newClient("portal uuid tie-break");
    var seed = UUID.randomUUID();
    long lowBits = seed.getMostSignificantBits() & 0x0FFFFFFFFFFFFFFFL;
    var first = new UUID(lowBits | 0x1000000000000000L, seed.getLeastSignificantBits());
    var second = new UUID(lowBits | 0x9000000000000000L, seed.getLeastSignificantBits());
    assertThat(second.compareTo(first)).as("signed compare puts 9… first").isNegative();
    sameInstantDeposit(funding, client, first, "100.00");
    sameInstantDeposit(funding, client, second, "250.00");

    publishReconciliationCompletedEvent(funding);

    var rows = portalTrustRepo.findTransactions(client.id(), client.matter(), null, null, 50, 0);
    // PostgreSQL order: 1… then 9… — the latest row (shown first) carries the balance after both
    assertThat(rows).extracting(t -> t.id()).containsExactly(second, first);
    assertThat(rows.get(0).runningBalance()).isEqualByComparingTo("350.00");
    assertThat(rows.get(1).runningBalance()).isEqualByComparingTo("100.00");

    assertBackfillReproduces(client.id(), client.matter());
  }

  /** A posted DEPOSIT row with an explicit id, all on one date and one recorded instant. */
  private void sameInstantDeposit(
      UUID account, TrustBooksFixture.Client client, UUID id, String amount) {
    var effect = TrustTransactionType.of("DEPOSIT").effectFor(new BigDecimal(amount));
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, description, transaction_date, status,
            recorded_by, created_at, ledger, cashbook_effect, ledger_effect, posted_at)
        VALUES (?, ?, 'DEPOSIT', ?, ?, ?, ?, 'Same-instant deposit', DATE '2026-03-02',
            'RECORDED', ?, TIMESTAMPTZ '2026-03-02 09:00:00+00', ?, ?, ?, now())
        """
            .formatted(tenantSchema),
        id,
        account,
        new BigDecimal(amount),
        client.id(),
        client.matter(),
        "TIE-" + id.toString().substring(0, 8),
        memberId,
        effect.ledger().name(),
        effect.cashbookEffect(),
        effect.ledgerEffect());
  }

  /** A section 86(4) placement of {@code principal} for a fresh client, approved. */
  private Held s86_4Investment(String deposit, String principal) {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("portal s86(4)");
    books.deposit(funding, client, deposit);
    var placed = books.requestPlacement(funding, investmentAccount, client, principal);
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-" + principal),
        investmentAccount,
        Expected.cashbookAndInvestment(principal),
        placed.depositTransactionId(),
        approverId);
    return new Held(funding, investmentAccount, client, placed.id());
  }

  private record Held(
      UUID funding, UUID investmentAccount, TrustBooksFixture.Client client, UUID investmentId) {}

  /** A bank-advice interest entry: NET only, the 5% computed (648B, OQ-4). */
  private static RecordInvestmentInterestRequest netInterest(LocalDate date, String gross) {
    return new RecordInvestmentInterestRequest(
        date, new BigDecimal(gross), null, InterestPresentation.NET, null);
  }

  /** The firm's invested balance for the held client (648E), from its one funding account. */
  private BigDecimal firmInvested(Held held) {
    return books.read(
        () ->
            ledgerQueryRepository
                .investedBalancesByClient(held.funding(), List.of(held.client().id()))
                .stream()
                .map(ClientBalanceRow::getBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO));
  }

  /**
   * The event-driven projection of the matter equals the backfill's (648D: both paths apply the
   * same split): wipes the client's portal rows, backfills the tenant and compares every row and
   * the balance snapshot.
   */
  private void assertBackfillReproduces(UUID client, UUID matter) {
    var eventDriven = portalSnapshot(client, matter);
    portalTrustRepo.deleteTransactionsByCustomer(client);
    portalTrustRepo.deleteBalancesByCustomer(client);
    ScopedValue.where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> syncService.backfillForTenant(ORG_ID));
    assertThat(portalSnapshot(client, matter)).containsExactlyElementsOf(eventDriven);
  }

  private List<String> portalSnapshot(UUID client, UUID matter) {
    var snapshot =
        portalTrustRepo.findTransactions(client, matter, null, null, 200, 0).stream()
            .map(
                t ->
                    String.join(
                        "|",
                        t.id().toString(),
                        t.transactionType(),
                        t.ledger(),
                        t.amount().toPlainString(),
                        t.runningBalance().toPlainString(),
                        t.description()))
            .collect(Collectors.toCollection(ArrayList::new));
    var balance = portalTrustRepo.findBalance(client, matter).orElseThrow();
    snapshot.add(
        String.join(
            "|",
            "balance",
            balance.currentBalance().toPlainString(),
            balance.investedBalance().toPlainString(),
            String.valueOf(balance.lastTransactionAt())));
    return snapshot;
  }

  // ==========================================================================
  // Phase 92 649B: a reversal re-projects the portal at once (§16.3.11). The
  // contra posts with its own recorded event; the replay shows the reversed
  // original and its "Reversal" row, each with its own effect.
  // ==========================================================================

  @Test
  void reversalReprojectsThePortalAtOnce() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        trustTransactionService.recordDeposit(
                            trustAccountId,
                            new RecordDepositRequest(
                                customerId,
                                matterId,
                                new BigDecimal("3000.00"),
                                "DEP-REV-SYNC-1",
                                "Deposit before a reversed payment",
                                LocalDate.of(2026, 4, 5)))));
    // Approved without an event: the portal has not seen the payment yet.
    UUID paymentId =
        recordApprovedTransactionOnDate(
            "PAYMENT",
            new BigDecimal("1000.00"),
            "PAY-REV-SYNC-1",
            "Payment made in error",
            LocalDate.of(2026, 4, 6));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        trustTransactionService.reverseTransaction(
                            paymentId, new ReverseRequest("Paid in error", null))));

    // No further event: the contra's own recorded event re-projected the matter.
    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions)
        .extracting(t -> t.transactionType())
        .containsExactlyInAnyOrder("DEPOSIT", "PAYMENT", "REVERSAL");
    var payment =
        transactions.stream()
            .filter(t -> "PAYMENT".equals(t.transactionType()))
            .findFirst()
            .orElseThrow();
    assertThat(payment.runningBalance()).isEqualByComparingTo("2000.00");
    var reversal =
        transactions.stream()
            .filter(t -> "REVERSAL".equals(t.transactionType()))
            .findFirst()
            .orElseThrow();
    assertThat(reversal.amount()).isEqualByComparingTo("1000.00");
    assertThat(reversal.runningBalance()).isEqualByComparingTo("3000.00");
    // PR #1789 review: the contra's description is the firm user's free-text reversal reason —
    // never projected to the client. The portal shows a generated "<client label> — <reference>".
    assertReversalDescriptionIsGenerated(reversal.description());

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("3000.00");

    // The backfill projection path generates the same description.
    ScopedValue.where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> syncService.backfillForTenant(ORG_ID));
    var backfilledReversal =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .filter(t -> "REVERSAL".equals(t.transactionType()))
            .findFirst()
            .orElseThrow();
    assertReversalDescriptionIsGenerated(backfilledReversal.description());
  }

  private static void assertReversalDescriptionIsGenerated(String description) {
    assertThat(description)
        .doesNotContain("Paid in error")
        .doesNotContain("REVERSAL")
        .isEqualTo("Reversal \u2014 REV-PAY-REV-SYNC-1");
  }

  // ==========================================================================
  // Running balance: each historical row gets its own progressive sum, not the
  // latest aggregate (regression guard for PR #1084 review comment C1).
  // ==========================================================================

  @Test
  void runningBalanceIsProgressiveAcrossHistoricalRows() {
    // Seed three approved deposits on distinct transaction dates so the ASC walk produces
    // 100, 300, 600 — NOT 600/600/600 (the pre-fix bug).
    UUID txn1 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("100.00"),
            "PROG-1",
            "First deposit",
            LocalDate.of(2026, 2, 1));
    UUID txn2 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("200.00"),
            "PROG-2",
            "Second deposit",
            LocalDate.of(2026, 2, 2));
    UUID txn3 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("300.00"),
            "PROG-3",
            "Third deposit",
            LocalDate.of(2026, 2, 3));

    // Publishing any one approval recomputes the entire matter history, so a single event
    // is enough to populate all three rows with their progressive balances.
    publishApprovedEvent(txn3, "DEPOSIT", new BigDecimal("300.00"));

    var byId =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustTransactionView::id,
                    t -> t));

    assertThat(byId.get(txn1).runningBalance()).isEqualByComparingTo("100.00");
    assertThat(byId.get(txn2).runningBalance()).isEqualByComparingTo("300.00");
    assertThat(byId.get(txn3).runningBalance()).isEqualByComparingTo("600.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("600.00");
  }

  // ==========================================================================
  // Sanitiser: internal-tagged description falls back to synthesised value
  // ==========================================================================

  @Test
  void internalTaggedDescriptionIsReplacedWithSynthesisedFallback() {
    UUID txnId =
        recordApprovedTransaction(
            "PAYMENT", new BigDecimal("250.00"), "TX-INT-42", "[internal] do not share");

    publishApprovedEvent(txnId, "PAYMENT", new BigDecimal("250.00"));

    var match =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .filter(t -> t.id().equals(txnId))
            .findFirst()
            .orElseThrow();
    assertThat(match.description()).doesNotContain("do not share");
    // 648D (OQ-5): the fallback names the type by its client label, never the raw code
    assertThat(match.description()).isEqualTo("Payment \u2014 TX-INT-42");
  }

  // ==========================================================================
  // Sanitiser: long descriptions truncate at 140 with ellipsis
  // ==========================================================================

  @Test
  void longDescriptionIsTruncatedAt140Characters() {
    String longDesc = "a".repeat(200);
    UUID txnId =
        recordApprovedTransaction("DEPOSIT", new BigDecimal("10.00"), "DEP-LONG-1", longDesc);

    publishApprovedEvent(txnId, "DEPOSIT", new BigDecimal("10.00"));

    var match =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .filter(t -> t.id().equals(txnId))
            .findFirst()
            .orElseThrow();
    assertThat(match.description()).hasSize(140);
    assertThat(match.description()).endsWith("\u2026");
  }

  // ==========================================================================
  // Event listener: reconciliation completed (fan-out)
  // ==========================================================================

  @Test
  void reconciliationCompletedEventRecomputesMatterBalance() {
    // Seed a pair of deposits — a pre-fan-out approval recompute would otherwise leave portal
    // rows empty, so we force-sync via the event handler under test.
    recordApprovedTransactionOnDate(
        "DEPOSIT",
        new BigDecimal("500.00"),
        "REC-1",
        "Pre-reconciliation deposit 1",
        LocalDate.of(2026, 3, 10));
    recordApprovedTransactionOnDate(
        "DEPOSIT",
        new BigDecimal("250.00"),
        "REC-2",
        "Pre-reconciliation deposit 2",
        LocalDate.of(2026, 3, 11));

    publishReconciliationCompletedEvent();

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(2);

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("750.00");
  }

  // ==========================================================================
  // Backfill: seeds balance + transactions up to per-matter cap
  // ==========================================================================

  // ==========================================================================
  // Backfill: drops stale portal rows (matters that no longer have firm-side
  // transactions) — drift repair semantics per PR #1084 review.
  // ==========================================================================

  @Test
  void backfillForTenantWipesStaleMatterRows() {
    // Pre-seed the portal read-model with a balance row for a matter that has NO firm-side
    // trust transactions. The tenant-scoped findAll() during backfill will not surface it, so
    // the wipe-and-rewrite pass must drop it.
    portalTrustRepo.deleteTransactionsByCustomer(customerId);
    portalTrustRepo.deleteBalancesByCustomer(customerId);

    UUID staleMatterId = UUID.randomUUID();
    portalTrustRepo.upsertBalance(
        customerId,
        staleMatterId,
        new BigDecimal("777.00"),
        BigDecimal.ZERO,
        java.time.Instant.parse("2026-01-01T00:00:00Z"));
    portalTrustRepo.upsertTransaction(
        UUID.randomUUID(),
        customerId,
        staleMatterId,
        "DEPOSIT",
        "CLIENT",
        new BigDecimal("777.00"),
        new BigDecimal("777.00"),
        java.time.Instant.parse("2026-01-01T00:00:00Z")
            .atZone(io.b2mash.b2b.b2bstrawman.config.KaziTime.ZONE)
            .toLocalDate(),
        java.time.Instant.parse("2026-01-01T00:00:00Z"),
        java.time.Instant.parse("2026-01-01T00:00:00Z"),
        "stale",
        "STALE-REF");

    // Seed one firm-side approved transaction so the backfill has at least one retained matter
    // for this customer and exercises the matter-set delete branch (rather than the
    // no-retained-matters fall-through).
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              "DEPOSIT",
                              new BigDecimal("50.00"),
                              customerId,
                              matterId,
                              null,
                              "RETAIN-1",
                              "Retained deposit",
                              LocalDate.of(2026, 2, 1),
                              "RECORDED",
                              memberId);
                      postApproved(txn);
                    }));

    // The cross-tenant guard added in issue #1267 requires ORG_ID to be bound and to match
    // the argument before the backfill runs. Mirror what an authenticated controller request
    // would do.
    ScopedValue.where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> syncService.backfillForTenant(ORG_ID));

    // The stale matter's balance row must be gone.
    assertThat(portalTrustRepo.findBalance(customerId, staleMatterId)).isEmpty();
    // And no stale transactions for that matter either.
    assertThat(portalTrustRepo.findTransactions(customerId, staleMatterId, null, null, 50, 0))
        .isEmpty();
    // Sanity: the retained matter's balance is present.
    assertThat(portalTrustRepo.findBalance(customerId, matterId)).isPresent();
  }

  @Test
  void backfillForTenantSeedsBalanceAndTransactions() {
    // Isolate from earlier test fixtures by clearing portal-side state for this customer.
    portalTrustRepo.deleteTransactionsByCustomer(customerId);
    portalTrustRepo.deleteBalancesByCustomer(customerId);

    // Seed 3 approved deposits on the matter.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      for (int i = 0; i < 3; i++) {
                        var txn =
                            new TrustTransaction(
                                trustAccountId,
                                "DEPOSIT",
                                new BigDecimal("100.00"),
                                customerId,
                                matterId,
                                null,
                                "BKF-" + i,
                                "Backfill deposit " + i,
                                LocalDate.of(2026, 1, 1 + i),
                                "RECORDED",
                                memberId);
                        postApproved(txn);
                      }
                    }));

    // Bind ORG_ID per the cross-tenant guard (issue #1267).
    var result =
        ScopedValue.where(RequestScopes.ORG_ID, ORG_ID)
            .call(() -> syncService.backfillForTenant(ORG_ID));
    assertThat(result.mattersProjected()).isGreaterThanOrEqualTo(1);
    assertThat(result.transactionsProjected()).isGreaterThanOrEqualTo(3);

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isGreaterThanOrEqualTo(new BigDecimal("300.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 100, 0);
    assertThat(transactions).hasSizeGreaterThanOrEqualTo(3);
  }

  // ==========================================================================
  // Cross-tenant guard (issue #1267 — must run before any future controller
  // exposure).
  // ==========================================================================

  @Test
  void backfillForTenantThrowsWhenOrgIdScopeIsUnbound() {
    // No outer ScopedValue.where(ORG_ID, ...). The first guard branch must fire.
    assertThatThrownBy(() -> syncService.backfillForTenant(ORG_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must run inside an authenticated request scope");
  }

  @Test
  void backfillForTenantRejectsCrossTenantTargetWhenScopedOrgDiffers() {
    // An authenticated caller for org A asks the service to backfill org B.
    // The guard MUST refuse before binding any tenant scope. Without the
    // guard, RequestScopes.runForTenantWithMember(...) would silently rebind
    // to org B's schema and operate there — a cross-tenant escape.
    String otherOrgId = "org_some_other_tenant";
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.ORG_ID, ORG_ID)
                    .run(() -> syncService.backfillForTenant(otherOrgId)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Cross-tenant backfill denied");
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  /**
   * Posts {@code draft} APPROVED with no event: a type that needs approval through {@code
   * recordAwaitingApproval} + {@code postOnApproval}; a type posted when recorded (a deposit)
   * through {@code record}, then hand-set APPROVED on the posted row (Phase 92, 646A: the row CHECK
   * refuses a posted status written without the posting service).
   */
  private TrustTransaction postApproved(TrustTransaction draft) {
    if (TrustTransactionType.of(draft.getTransactionType()).approval()
        == TrustTransactionType.Approval.REQUIRED) {
      draft.setStatus("AWAITING_APPROVAL");
      return trustPostingService.postOnApproval(
          trustPostingService.recordAwaitingApproval(draft), memberId);
    }
    var posted = trustPostingService.record(draft);
    posted.setStatus("APPROVED");
    return trustTransactionRepository.saveAndFlush(posted);
  }

  private UUID recordApprovedTransaction(
      String type, BigDecimal amount, String reference, String description) {
    return recordApprovedTransactionOnDate(
        type, amount, reference, description, LocalDate.of(2026, 2, 1));
  }

  private UUID recordApprovedTransactionOnDate(
      String type, BigDecimal amount, String reference, String description, LocalDate txnDate) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              type,
                              amount,
                              customerId,
                              matterId,
                              null,
                              reference,
                              description,
                              txnDate,
                              "RECORDED",
                              memberId);
                      holder[0] = postApproved(txn).getId();
                    }));
    return holder[0];
  }

  private void publishApprovedEvent(UUID txnId, String type, BigDecimal amount) {
    // Publish inside a bound tenant scope so the surrounding transaction (and any session
    // reused by the AFTER_COMMIT listener's repository call) resolves to the tenant schema.
    // The sync listener runs AFTER_COMMIT on this transaction — AFTER_COMMIT fires because
    // the event is published inside TransactionTemplate's executeWithoutResult.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustTransactionApprovalEvent.approved(
                                txnId,
                                trustAccountId,
                                matterId,
                                type,
                                amount,
                                LocalDate.of(2026, 4, 5),
                                customerId,
                                memberId,
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }

  /**
   * Seeds a {@code RECORDED}-status trust transaction through {@link TrustPostingService} (no event
   * — the posting service publishes none). Used by the GAP-L-52 listener test to exercise the
   * {@link TrustTransactionRecordedEvent} branch in isolation.
   */
  private UUID recordRecordedTransaction(
      String type, BigDecimal amount, String reference, String description) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              type,
                              amount,
                              customerId,
                              matterId,
                              null,
                              reference,
                              description,
                              LocalDate.of(2026, 4, 1),
                              "RECORDED",
                              memberId);
                      holder[0] = trustPostingService.record(txn).getId();
                    }));
    return holder[0];
  }

  private void publishRecordedEvent(UUID txnId, String type, BigDecimal amount) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustTransactionRecordedEvent.recorded(
                                txnId,
                                trustAccountId,
                                matterId,
                                type,
                                amount,
                                LocalDate.of(2026, 4, 5),
                                customerId,
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }

  private void publishReconciliationCompletedEvent() {
    publishReconciliationCompletedEvent(trustAccountId);
  }

  private void publishReconciliationCompletedEvent(UUID accountId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustDomainEvent.ReconciliationCompleted.of(
                                UUID.randomUUID(),
                                accountId,
                                LocalDate.of(2026, 3, 31),
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }
}
