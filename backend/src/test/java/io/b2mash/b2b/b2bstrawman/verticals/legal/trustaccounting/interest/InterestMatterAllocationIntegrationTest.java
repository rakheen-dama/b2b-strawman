package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateLpffRateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestAllocationLineResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestAllocationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
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
 * money in, pro-rata by balance-days, to the cent by largest remainder. Client-level totals
 * (average daily balance, gross, LPFF, client share) are unchanged by the split. Posting is refused
 * since Phase 92 645A (section 86(5)); these tests cover the calculation only.
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

  @Autowired
  private io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository customerProjectRepository;

  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InterestService interestService;
  @Autowired private InterestAllocationLineRepository lineRepository;

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

  @Test
  void singleMatter_lineCarriesMatter() throws Exception {
    UUID account = newAccount();
    UUID client = newClient("Single Matter Client");
    UUID matter = newMatter("Single Matter");
    deposit(account, client, matter, "100000.00", LocalDate.of(2025, 2, 1));

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocation = singleAllocation(runId);

    assertThat(allocation.lines()).hasSize(1);
    var line = allocation.lines().getFirst();
    assertThat(line.matterId()).isEqualTo(matter);
    assertThat(line.matterName()).isEqualTo("Single Matter");
    assertThat(line.clientShare()).isEqualByComparingTo(allocation.clientShare());
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
          .as("line %s within a cent of its pro-rata share", line.matterId())
          .isLessThan(new BigDecimal("0.01"));
    }
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
            .sorted(Comparator.comparing(InterestAllocationLineResponse::matterId))
            .toList();
    assertThat(byMatter).extracting(InterestAllocationLineResponse::matterId).isEqualTo(matters);
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
          .isEqualByComparingTo(lineFor(allocation, line.matterId()).clientShare());
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
        .extracting(InterestAllocationLineResponse::matterId)
        .containsExactlyInAnyOrder(funded, null);
    assertThat(allocation.lines()).allSatisfy(line -> assertThat(line.clientShare()).isPositive());
    assertThat(sumOf(allocation.lines())).isEqualByComparingTo(allocation.clientShare());
  }

  @Test
  void coClientMatter_eachClientGetsOwnLineOnTheMatter() throws Exception {
    UUID account = newAccount();
    UUID clientA = newClient("Co-client Alpha");
    UUID clientB = newClient("Co-client Beta");
    UUID matter = newMatter("Shared Matter");
    deposit(account, clientA, matter, "10000.00", LocalDate.of(2025, 2, 10));
    deposit(account, clientB, matter, "5000.00", LocalDate.of(2025, 2, 10));

    UUID runId = calculatedRun(account, MAR_1, MAR_31);
    var allocations = allocations(runId);
    assertThat(allocations).hasSize(2);

    for (var allocation : allocations) {
      assertThat(allocation.lines()).hasSize(1);
      var line = allocation.lines().getFirst();
      assertThat(line.matterId()).isEqualTo(matter);
      assertThat(line.clientShare()).isEqualByComparingTo(allocation.clientShare());
    }
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
        .extracting(InterestAllocationLineResponse::matterId)
        .containsExactlyInAnyOrder(m1, m2);
    assertThat(sumOf(x.lines())).isEqualByComparingTo(x.clientShare());
    assertThat(y.lines()).hasSize(1);
    assertThat(y.lines().getFirst().matterId()).isNull();
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

  /**
   * {@code matter} null = a legacy client-level posting (recorded before G89-F28 made a matter
   * mandatory; D6 keeps such rows): recorded on a throwaway matter of the client, then stripped of
   * it, so the ledger card moves exactly as the pre-F28 service moved it.
   */
  private void deposit(UUID account, UUID client, UUID matter, String amount, LocalDate date) {
    int n = ++counter;
    UUID posted = postingMatter(client, matter);
    UUID id =
        as(
            ownerId,
            () ->
                transactionService
                    .recordDeposit(
                        account,
                        new RecordDepositRequest(
                            client,
                            posted,
                            new BigDecimal(amount),
                            "IMA-DEP-" + n,
                            "Deposit",
                            date))
                    .id());
    legacyIfUnmattered(matter, id);
  }

  private UUID payment(UUID account, UUID client, UUID matter, String amount, LocalDate date) {
    int n = ++counter;
    UUID posted = postingMatter(client, matter);
    UUID id =
        as(
            ownerId,
            () ->
                transactionService
                    .recordPayment(
                        account,
                        new RecordPaymentRequest(
                            client,
                            posted,
                            new BigDecimal(amount),
                            "IMA-PAY-" + n,
                            "Payment",
                            date))
                    .id());
    legacyIfUnmattered(matter, id);
    return id;
  }

  /** The matter to post on: {@code matter} linked to the client, or a throwaway one (legacy). */
  private UUID postingMatter(UUID client, UUID matter) {
    if (matter == null) {
      return TestTrustMatterHelper.openClientMatter(
          tenantSchema,
          transactionTemplate,
          projectRepository,
          client,
          "IMA legacy posting matter " + counter,
          ownerId);
    }
    TestTrustMatterHelper.linkClientMatter(
        tenantSchema, transactionTemplate, customerProjectRepository, client, matter, ownerId);
    return matter;
  }

  private void legacyIfUnmattered(UUID matter, UUID transactionId) {
    if (matter == null) {
      TestTrustMatterHelper.stripMatter(
          tenantSchema, transactionTemplate, entityManager, transactionId);
    }
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
        .filter(l -> java.util.Objects.equals(l.matterId(), projectId))
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
