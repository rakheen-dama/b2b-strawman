package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates.TrustBalanceZeroGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateLpffRateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestAllocationLineResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestAllocationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-366 (G89-F22, founder D5–D8): a client's interest share is allocated to the matters it held
 * money in, pro-rata by balance-days, to the cent by largest remainder; posting writes one
 * INTEREST_CREDIT per matter line. Client-level totals (average daily balance, gross, LPFF, client
 * share) are unchanged by the split.
 *
 * <p>Each test uses its own trust account (so runs never overlap and the calculation sees only that
 * test's clients) and a period in the past (G89-F11 guard). Rate 7.5 %, 75 % to the LPFF unless a
 * test says otherwise. Rows go through the services; approvals by a second member.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestMatterAllocationIntegrationTest {

  private static final String ORG_ID = "org_interest_matter_allocation";
  private static final String OWNER_SUB = "user_ima_owner";
  private static final LocalDate MAR_1 = LocalDate.of(2025, 3, 1);
  private static final LocalDate MAR_31 = LocalDate.of(2025, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InterestService interestService;
  @Autowired private InterestAllocationLineRepository lineRepository;
  @Autowired private TrustBalanceZeroGate trustBalanceZeroGate;

  private String tenantSchema;
  private UUID ownerId;
  private UUID approverId;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Interest Matter Allocation Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "ima_owner@test.com", "IMA Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_ima_approver",
                "ima_approver@test.com",
                "IMA Approver",
                "owner"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  // --- 636B end to end ---

  @Test
  void
      phase89_636B_interestAllocatedToMatter_refundOfMatterPlusInterestApproved_matterAndClientZero_gatePasses()
          throws Exception {
    UUID account = newAccount();
    UUID client = newClient("636B Client");
    UUID matter = newMatter("636B Matter");
    deposit(account, client, matter, "5000.00", LocalDate.of(2025, 2, 15));
    approve(payment(account, client, matter, "1250.00", LocalDate.of(2025, 2, 20)));

    UUID runId = postedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);
    var interest = allocation.clientShare();
    assertThat(interest).isPositive();

    // The whole client share sits on the matter: card == matter == 3 750 + interest
    var expected = new BigDecimal("3750.00").add(interest);
    assertThat(matterBalance(matter)).isEqualByComparingTo(expected);
    assertThat(cardBalance(account, client)).isEqualByComparingTo(expected);

    // One cent more is refused (card and matter hold the same money here, so the card check is the
    // first to refuse; the matter guard alone is covered by TrustMatterDebitGuardIntegrationTest)
    // ...
    UUID overRefund = refund(account, client, matter, expected.add(new BigDecimal("0.01")));
    assertThatThrownBy(() -> approve(overRefund))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> assertThat(e.getBody().getTitle()).isEqualTo("Insufficient trust balance"));
    assertThat(matterBalance(matter)).isEqualByComparingTo(expected);

    // ... the refund of matter + interest is approved and both ledgers reach zero
    approve(refund(account, client, matter, expected));
    assertThat(matterBalance(matter)).isEqualByComparingTo("0.00");
    assertThat(cardBalance(account, client)).isEqualByComparingTo("0.00");
    assertThat(gatePasses(matter)).isTrue();
  }

  @Test
  void singleMatter_creditCarriesProjectId() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Single Matter Client");
    UUID matter = newMatter("Single Matter");
    deposit(account, client, matter, "100000.00", LocalDate.of(2025, 2, 1));

    UUID runId = postedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);

    assertThat(allocation.lines()).hasSize(1);
    var line = allocation.lines().getFirst();
    assertThat(line.projectId()).isEqualTo(matter);
    assertThat(line.projectName()).isEqualTo("Single Matter");
    assertThat(line.clientShare()).isEqualByComparingTo(allocation.clientShare());
    assertThat(line.trustTransactionId()).isNotNull();
    // Exactly one line: the allocation points at the same credit
    assertThat(allocation.trustTransactionId()).isEqualTo(line.trustTransactionId());

    var credit = transaction(line.trustTransactionId());
    assertThat(credit.getTransactionType()).isEqualTo("INTEREST_CREDIT");
    assertThat(credit.getProjectId()).isEqualTo(matter);
    assertThat(credit.getCustomerId()).isEqualTo(client);
    assertThat(credit.getAmount()).isEqualByComparingTo(allocation.clientShare());
    assertThat(credit.getTransactionDate()).isEqualTo(MAR_31);
    assertThat(credit.getReference()).isEqualTo("INT-CREDIT-" + runId + "-" + matter);
  }

  @Test
  void twoMattersPlusUnmattered_proRataByBalanceDays_linesSumExactlyToClientShare()
      throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Two Matters Client");
    UUID matter1 = newMatter("Two Matters M1");
    UUID matter2 = newMatter("Two Matters M2");
    deposit(account, client, matter1, "10000.00", LocalDate.of(2025, 2, 20));
    deposit(account, client, null, "5000.00", LocalDate.of(2025, 2, 20));
    deposit(account, client, matter2, "20000.00", LocalDate.of(2025, 3, 16));

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);

    // M1 10 000 × 31, M2 20 000 × 16 (16–31 March), unmattered 5 000 × 31
    var m1 = lineFor(allocation, matter1);
    var m2 = lineFor(allocation, matter2);
    var unmattered = lineFor(allocation, null);
    assertThat(m1.balanceDays()).isEqualByComparingTo("310000.00");
    assertThat(m2.balanceDays()).isEqualByComparingTo("320000.00");
    assertThat(unmattered.balanceDays()).isEqualByComparingTo("155000.00");
    assertThat(allocation.lines()).hasSize(3);

    // Client-level average daily balance is the plain total: 785 000 / 31
    assertThat(allocation.averageDailyBalance()).isEqualByComparingTo("25322.58");

    var clientShare = allocation.clientShare();
    assertThat(sumOf(allocation.lines())).isEqualByComparingTo(clientShare);
    var totalDays = new BigDecimal("785000");
    for (var line : allocation.lines()) {
      var exact = clientShare.multiply(line.balanceDays()).divide(totalDays, 6, RoundingMode.DOWN);
      assertThat(line.clientShare().subtract(exact).abs())
          .as("line %s within a cent of its pro-rata share", line.projectId())
          .isLessThan(new BigDecimal("0.01"));
    }

    // Posting: one credit per line, each on its own matter; the card moves by the client share
    approveAndPost(runId);
    var posted = singleAllocation(runId);
    assertThat(posted.trustTransactionId()).as("several lines: no single credit").isNull();
    for (var line : posted.lines()) {
      var credit = transaction(line.trustTransactionId());
      assertThat(credit.getProjectId()).isEqualTo(line.projectId());
      assertThat(credit.getAmount()).isEqualByComparingTo(line.clientShare());
    }
    assertThat(cardBalance(account, client))
        .isEqualByComparingTo(new BigDecimal("35000.00").add(clientShare));
    assertThat(matterBalance(matter1))
        .isEqualByComparingTo(new BigDecimal("10000.00").add(m1.clientShare()));
    assertThat(matterBalance(matter2))
        .isEqualByComparingTo(new BigDecimal("20000.00").add(m2.clientShare()));
  }

  @Test
  void largestRemainder_threeEqualMatters_oddCent_deterministic() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Three Equal Client");
    var matters =
        List.of(newMatter("Equal A"), newMatter("Equal B"), newMatter("Equal C")).stream()
            .sorted()
            .toList();
    for (var matter : matters) {
      deposit(account, client, matter, "10000.00", LocalDate.of(2025, 2, 15));
    }

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);
    var clientShare = allocation.clientShare();
    long cents = clientShare.movePointRight(2).longValueExact();
    // 30 000 × 7.5 % × 31/365 = 191.10 gross, 143.33 LPFF, 47.77 client: one cent left over
    assumeThat(cents % 3).as("client share must not split evenly in three").isEqualTo(1L);

    var floor = clientShare.divide(new BigDecimal("3"), 2, RoundingMode.DOWN);
    var byMatter =
        allocation.lines().stream()
            .sorted(Comparator.comparing(InterestAllocationLineResponse::projectId))
            .toList();
    assertThat(byMatter).extracting(InterestAllocationLineResponse::projectId).isEqualTo(matters);
    // Equal remainders and weights: the leftover cent goes to the lowest matter id
    assertThat(byMatter.get(0).clientShare())
        .isEqualByComparingTo(floor.add(new BigDecimal("0.01")));
    assertThat(byMatter.get(1).clientShare()).isEqualByComparingTo(floor);
    assertThat(byMatter.get(2).clientShare()).isEqualByComparingTo(floor);
    assertThat(sumOf(byMatter)).isEqualByComparingTo(clientShare);

    // Deterministic: recalculating gives the same split
    as(ownerId, () -> interestService.calculateInterest(runId));
    var again = singleAllocation(runId);
    for (var line : again.lines()) {
      assertThat(line.clientShare())
          .isEqualByComparingTo(lineFor(allocation, line.projectId()).clientShare());
    }
  }

  @Test
  void negativeOrZeroCell_getsNoLine() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Zero Negative Client");
    UUID funded = newMatter("Funded Matter");
    UUID emptied = newMatter("Emptied Matter");
    UUID overdrawn = newMatter("Overdrawn Matter");
    deposit(account, client, funded, "10000.00", LocalDate.of(2025, 2, 10));
    deposit(account, client, emptied, "1000.00", LocalDate.of(2025, 2, 10));
    approve(payment(account, client, emptied, "1000.00", LocalDate.of(2025, 2, 12)));
    deposit(account, client, null, "500.00", LocalDate.of(2025, 2, 10));
    // A pre-guard matter overdraft (G89-F12 now refuses it): a debit with no money on the matter
    legacyApprovedPayment(account, client, overdrawn, "500.00", LocalDate.of(2025, 2, 12));

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);

    assertThat(allocation.lines())
        .extracting(InterestAllocationLineResponse::projectId)
        .containsExactlyInAnyOrder(funded, null);
    assertThat(allocation.lines()).allSatisfy(line -> assertThat(line.clientShare()).isPositive());
    assertThat(sumOf(allocation.lines())).isEqualByComparingTo(allocation.clientShare());
  }

  @Test
  void coClientMatter_eachClientCreditedOnOwnCell() throws Exception {
    UUID account = newAccount();
    UUID clientA = newClient("Co-client Alpha");
    UUID clientB = newClient("Co-client Beta");
    UUID matter = newMatter("Shared Matter");
    deposit(account, clientA, matter, "10000.00", LocalDate.of(2025, 2, 10));
    deposit(account, clientB, matter, "5000.00", LocalDate.of(2025, 2, 10));

    UUID runId = postedRun(account, MAR_1, MAR_31);
    var allocations = allocations(runId);
    assertThat(allocations).hasSize(2);

    for (var allocation : allocations) {
      assertThat(allocation.lines()).hasSize(1);
      var line = allocation.lines().getFirst();
      assertThat(line.projectId()).isEqualTo(matter);
      assertThat(line.clientShare()).isEqualByComparingTo(allocation.clientShare());
      var credit = transaction(line.trustTransactionId());
      assertThat(credit.getCustomerId()).isEqualTo(allocation.clientId());
      assertThat(credit.getProjectId()).isEqualTo(matter);
    }

    var shareA = allocationOf(allocations, clientA).clientShare();
    var shareB = allocationOf(allocations, clientB).clientShare();
    assertThat(cell(account, clientA, matter))
        .isEqualByComparingTo(new BigDecimal("10000.00").add(shareA));
    assertThat(cell(account, clientB, matter))
        .isEqualByComparingTo(new BigDecimal("5000.00").add(shareB));
  }

  @Test
  void midPeriodRateChange_clientTotalsUnchanged() throws Exception {
    UUID account = newAccount();
    as(
        ownerId,
        () ->
            trustAccountService.addLpffRate(
                account,
                new CreateLpffRateRequest(
                    LocalDate.of(2025, 5, 16),
                    new BigDecimal("0.0900"),
                    new BigDecimal("0.7000"),
                    "Mid-May change")));

    // X spreads 10 000 over two matters; Y holds the same 10 000 client-level. Both pay R1 000 on
    // 20 May (X from M1). Their daily totals are identical, so must be every client-level figure.
    UUID clientX = newClient("Split Client X");
    UUID clientY = newClient("Flat Client Y");
    UUID m1 = newMatter("Split M1");
    UUID m2 = newMatter("Split M2");
    deposit(account, clientX, m1, "6000.00", LocalDate.of(2025, 4, 20));
    deposit(account, clientX, m2, "4000.00", LocalDate.of(2025, 4, 20));
    approve(payment(account, clientX, m1, "1000.00", LocalDate.of(2025, 5, 20)));
    deposit(account, clientY, null, "10000.00", LocalDate.of(2025, 4, 20));
    approve(payment(account, clientY, null, "1000.00", LocalDate.of(2025, 5, 20)));

    UUID runId = calculatedRun(account, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
    var allocations = allocations(runId);
    var x = allocationOf(allocations, clientX);
    var y = allocationOf(allocations, clientY);

    assertThat(x.averageDailyBalance()).isEqualByComparingTo(y.averageDailyBalance());
    assertThat(x.grossInterest()).isEqualByComparingTo(y.grossInterest());
    assertThat(x.lpffShare()).isEqualByComparingTo(y.lpffShare());
    assertThat(x.clientShare()).isEqualByComparingTo(y.clientShare());

    assertThat(x.lines())
        .extracting(InterestAllocationLineResponse::projectId)
        .containsExactlyInAnyOrder(m1, m2);
    assertThat(sumOf(x.lines())).isEqualByComparingTo(x.clientShare());
    assertThat(y.lines()).hasSize(1);
    assertThat(y.lines().getFirst().projectId()).isNull();
    assertThat(y.lines().getFirst().clientShare()).isEqualByComparingTo(y.clientShare());
  }

  @Test
  void recalculateAndDeleteDraft_removeLines() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Recalculate Client");
    UUID matter = newMatter("Recalculate Matter");
    deposit(account, client, matter, "20000.00", LocalDate.of(2025, 2, 10));

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var first = singleAllocation(runId);
    var firstLineIds = first.lines().stream().map(InterestAllocationLineResponse::id).toList();
    assertThat(firstLineIds).hasSize(1);

    // Recalculate: the old allocation's lines go, the new allocation has its own
    as(ownerId, () -> interestService.calculateInterest(runId));
    var second = singleAllocation(runId);
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(linesOfAllocations(List.of(first.id()))).isEmpty();
    assertThat(linesOfAllocations(List.of(second.id()))).hasSize(1);

    // Delete the draft: no line survives
    as(
        ownerId,
        () -> {
          interestService.deleteInterestRun(runId);
          return null;
        });
    assertThat(linesOfAllocations(List.of(first.id(), second.id()))).isEmpty();
  }

  @Test
  void postWithoutLines_fallsBackToSingleUnmatteredCredit() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Legacy Run Client");
    UUID matter = newMatter("Legacy Run Matter");
    deposit(account, client, matter, "40000.00", LocalDate.of(2025, 2, 10));

    // A run calculated before V12 has no lines (D6: no backfill)
    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx -> lineRepository.deleteByInterestAllocationIdIn(List.of(allocation.id()))));

    approveAndPost(runId);
    var posted = singleAllocation(runId);
    assertThat(posted.lines()).isEmpty();
    assertThat(posted.trustTransactionId()).isNotNull();

    var credit = transaction(posted.trustTransactionId());
    assertThat(credit.getProjectId()).isNull();
    assertThat(credit.getCustomerId()).isEqualTo(client);
    assertThat(credit.getAmount()).isEqualByComparingTo(posted.clientShare());
    assertThat(credit.getReference()).isEqualTo("INT-CREDIT-" + runId + "-" + client);
    assertThat(cardBalance(account, client))
        .isEqualByComparingTo(new BigDecimal("40000.00").add(posted.clientShare()));
    assertThat(matterBalance(matter)).isEqualByComparingTo("40000.00");
  }

  // --- Helpers ---

  private UUID newAccount() {
    int n = ++counter;
    return as(
        ownerId,
        () -> {
          var accountId =
              trustAccountService
                  .createTrustAccount(
                      new CreateTrustAccountRequest(
                          "IMA Account " + n,
                          "First National Bank",
                          "250655",
                          "629100" + String.format("%04d", n),
                          "GENERAL",
                          false,
                          false,
                          null,
                          LocalDate.of(2025, 1, 1),
                          "ADR-366 allocation test account"))
                  .id();
          trustAccountService.addLpffRate(
              accountId,
              new CreateLpffRateRequest(
                  LocalDate.of(2025, 1, 1),
                  new BigDecimal("0.0750"),
                  new BigDecimal("0.7500"),
                  "IMA base rate"));
          return accountId;
        });
  }

  private UUID newClient(String name) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                name, "ima_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private UUID newMatter(String name) throws Exception {
    return UUID.fromString(
        TestEntityHelper.createProject(mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB), name));
  }

  private void deposit(UUID account, UUID client, UUID matter, String amount, LocalDate date) {
    int n = ++counter;
    as(
        ownerId,
        () ->
            transactionService.recordDeposit(
                account,
                new RecordDepositRequest(
                    client, matter, new BigDecimal(amount), "IMA-DEP-" + n, "Deposit", date)));
  }

  private UUID payment(UUID account, UUID client, UUID matter, String amount, LocalDate date) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionService
                .recordPayment(
                    account,
                    new RecordPaymentRequest(
                        client, matter, new BigDecimal(amount), "IMA-PAY-" + n, "Payment", date))
                .id());
  }

  private UUID refund(UUID account, UUID client, UUID matter, BigDecimal amount) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionService
                .recordRefund(
                    account,
                    new RecordRefundRequest(
                        client,
                        matter,
                        amount,
                        "IMA-REF-" + n,
                        "Refund of matter funds and interest",
                        LocalDate.of(2025, 4, 15)))
                .id());
  }

  /** A debit as written before the G89-F12 guard: APPROVED straight into the ledger. */
  private void legacyApprovedPayment(
      UUID account, UUID client, UUID matter, String amount, LocalDate date) {
    int n = ++counter;
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    transactionRepository.save(
                        new TrustTransaction(
                            account,
                            "PAYMENT",
                            new BigDecimal(amount),
                            client,
                            matter,
                            null,
                            "IMA-LEGACY-PAY-" + n,
                            "Pre-guard matter overdraft",
                            date,
                            "APPROVED",
                            ownerId))));
  }

  /** Single-approval mode refuses self-approval — approve as the second member. */
  private void approve(UUID transactionId) {
    as(approverId, () -> transactionService.approveTransaction(transactionId, approverId));
  }

  private UUID calculatedRun(UUID account, LocalDate start, LocalDate end) {
    return as(
        ownerId,
        () -> {
          var runId = interestService.createInterestRun(account, start, end).id();
          interestService.calculateInterest(runId);
          return runId;
        });
  }

  private void approveAndPost(UUID runId) {
    as(approverId, () -> interestService.approveInterestRun(runId));
    as(ownerId, () -> interestService.postInterestRun(runId));
  }

  private UUID postedRun(UUID account, LocalDate start, LocalDate end) {
    var runId = calculatedRun(account, start, end);
    approveAndPost(runId);
    return runId;
  }

  private List<InterestAllocationResponse> allocations(UUID runId) {
    return as(ownerId, () -> interestService.getInterestRunDetail(runId).allocations());
  }

  private InterestAllocationResponse singleAllocation(UUID runId) {
    var allocations = allocations(runId);
    assertThat(allocations).hasSize(1);
    return allocations.getFirst();
  }

  private static InterestAllocationResponse allocationOf(
      List<InterestAllocationResponse> allocations, UUID clientId) {
    return allocations.stream()
        .filter(a -> a.clientId().equals(clientId))
        .findFirst()
        .orElseThrow();
  }

  private static InterestAllocationLineResponse lineFor(
      InterestAllocationResponse allocation, UUID projectId) {
    return allocation.lines().stream()
        .filter(l -> java.util.Objects.equals(l.projectId(), projectId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no line for matter " + projectId));
  }

  private static BigDecimal sumOf(List<InterestAllocationLineResponse> lines) {
    return lines.stream()
        .map(InterestAllocationLineResponse::clientShare)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<InterestAllocationLine> linesOfAllocations(List<UUID> allocationIds) {
    return as(ownerId, () -> lineRepository.findByInterestAllocationIdIn(allocationIds));
  }

  private TrustTransaction transaction(UUID id) {
    return as(ownerId, () -> transactionRepository.findById(id).orElseThrow());
  }

  private BigDecimal matterBalance(UUID matter) {
    return as(ownerId, () -> transactionRepository.calculateBalanceByProjectId(matter));
  }

  private BigDecimal cell(UUID account, UUID client, UUID matter) {
    return as(
        ownerId, () -> transactionRepository.calculateMatterClientBalance(account, client, matter));
  }

  private BigDecimal cardBalance(UUID account, UUID client) {
    return as(
        ownerId,
        () ->
            ledgerCardRepository
                .findByTrustAccountIdAndCustomerId(account, client)
                .orElseThrow()
                .getBalance());
  }

  private boolean gatePasses(UUID matter) {
    return as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    trustBalanceZeroGate
                        .evaluate(projectRepository.findById(matter).orElseThrow())
                        .passed()));
  }

  private <T> T as(UUID memberId, Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .call(action::get);
  }
}
