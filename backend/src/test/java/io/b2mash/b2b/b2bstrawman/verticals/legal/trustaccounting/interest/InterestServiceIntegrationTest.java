package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestServiceIntegrationTest {

  private static final String ORG_ID = "org_interest_calc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InterestService interestService;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;

  private String tenantSchema;
  private UUID trustAccountId;
  private String customerId1;
  private String customerId2;
  private UUID ownerMemberId;
  private UUID adminMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Interest Calc Test Org", null).schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_interest_owner",
                "interest_owner@test.com",
                "Interest Owner",
                "owner"));

    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_interest_admin",
                "interest_admin@test.com",
                "Interest Admin",
                "admin"));

    // Enable the trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner");

    // Create a trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Interest Calc Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000100",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    // Create customers
    customerId1 =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Interest Client Alpha", "alpha@test.com");
    customerId2 =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "Interest Client Beta", "beta@test.com");

    // Create LPFF rate effective from 2026-01-01 (7.5% annual, 75% LPFF share)
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Test rate 7.5%"
                    }
                    """))
        .andExpect(status().isCreated());

    // Deposit funds for both customers in January so all tests have a baseline
    // (deposits are RECORDED immediately and included in balance calculations)
    createDeposit(
        trustAccountId, customerId1, new BigDecimal("100000.00"), "DEP-SETUP-001", "2026-01-15");
    createDeposit(
        trustAccountId, customerId2, new BigDecimal("50000.00"), "DEP-SETUP-002", "2026-01-15");
  }

  // --- Helper Methods ---

  private void createDeposit(
      UUID accountId, String customerId, BigDecimal amount, String reference, String date)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Test deposit",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(customerId, amount.toPlainString(), reference, date)))
        .andExpect(status().isCreated());
  }

  private void createLpffRate(
      String effectiveFrom, BigDecimal ratePercent, BigDecimal lpffSharePercent) throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "%s",
                      "ratePercent": %s,
                      "lpffSharePercent": %s,
                      "notes": "Rate change"
                    }
                    """
                        .formatted(
                            effectiveFrom,
                            ratePercent.toPlainString(),
                            lpffSharePercent.toPlainString())))
        .andExpect(status().isCreated());
  }

  // ==========================================================================
  // 445.6 -- Interest Calculation Tests (7 tests)
  // ==========================================================================

  @Test
  void singleClientSingleRate_correctAverageDailyBalance() throws Exception {
    // Customer1 has 100,000 deposited in January (via @BeforeAll).
    // Create interest run for March 2026 (31 days) -- no new transactions in period,
    // opening balance carries forward.
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

    assertThat(runResponse.status()).isEqualTo("DRAFT");
    assertThat(runResponse.totalInterest()).isEqualByComparingTo(BigDecimal.ZERO);

    // Calculate interest
    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    assertThat(calculated.totalInterest()).isNotNull();
    assertThat(BigDecimal.ZERO.compareTo(calculated.totalInterest())).isLessThan(0);

    // Verify allocations
    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    // Should have allocation for customer1
    var customer1Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId1)))
            .findFirst()
            .orElse(null);

    assertThat(customer1Alloc).isNotNull();
    assertThat(customer1Alloc.daysInPeriod()).isEqualTo(31);
    assertThat(BigDecimal.ZERO.compareTo(customer1Alloc.averageDailyBalance())).isLessThan(0);

    // With 100,000 balance and no transactions in March, avg balance = 100,000
    // gross = 100,000 * 0.075 / 365 * 31 = ~636.99
    assertThat(customer1Alloc.averageDailyBalance())
        .isEqualByComparingTo(new BigDecimal("100000.00"));
    assertThat(customer1Alloc.grossInterest())
        .isEqualByComparingTo(
            new BigDecimal("100000.00")
                .multiply(new BigDecimal("0.0750"))
                .multiply(new BigDecimal("31"))
                .divide(new BigDecimal("365"), 2, RoundingMode.HALF_UP));
  }

  @Test
  void multipleClients_proportionalAllocation() throws Exception {
    // Both customers have baseline deposits from @BeforeAll:
    // Customer1: 100,000, Customer2: 50,000 (both on 2026-01-15)
    // Create interest run for April 2026 (30 days) -- both should have opening balances
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    // Both clients should have allocations
    assertThat(allocations.size()).isGreaterThanOrEqualTo(2);

    var customer1Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId1)))
            .findFirst()
            .orElse(null);
    var customer2Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId2)))
            .findFirst()
            .orElse(null);

    assertThat(customer1Alloc).isNotNull();
    assertThat(customer2Alloc).isNotNull();

    // Customer1 has larger balance (100k vs 50k), so should have higher interest
    assertThat(customer1Alloc.grossInterest()).isGreaterThan(customer2Alloc.grossInterest());

    // Total should equal sum of allocations
    var expectedTotal = BigDecimal.ZERO;
    for (var alloc : allocations) {
      expectedTotal = expectedTotal.add(alloc.grossInterest());
    }
    assertThat(calculated.totalInterest()).isEqualByComparingTo(expectedTotal);
  }

  @Test
  void midPeriodRateChange_proRataSplit() throws Exception {
    // Create a new LPFF rate effective from May 16 (8% annual, 80% LPFF share)
    createLpffRate("2026-05-16", new BigDecimal("0.0800"), new BigDecimal("0.8000"));

    // Create interest run for May 2026 (31 days)
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    // Interest should be calculated (pro-rata across rate change)
    assertThat(BigDecimal.ZERO.compareTo(calculated.totalInterest())).isLessThan(0);

    // Verify allocations exist
    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    assertThat(allocations).isNotEmpty();

    // Each allocation should have interest split across two rate periods
    // (May 1-15 at 7.5%, May 16-31 at 8%)
    for (var alloc : allocations) {
      assertThat(alloc.daysInPeriod()).isEqualTo(31);
    }
  }

  @Test
  void clientWithZeroBalanceForPartOfPeriod() throws Exception {
    // Create a new customer who receives a deposit mid-period.
    // Zero balance for the first half, positive for the second half.
    var customerId3 =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner"),
            "Interest Client Gamma",
            "gamma@test.com");

    // Deposit on June 16 (midway through June -- 30-day month)
    createDeposit(
        trustAccountId, customerId3, new BigDecimal("10000.00"), "DEP-INT-003", "2026-06-16");

    // Create interest run for June 2026 (30 days)
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    // Customer3 had 0 balance for June 1-15, then 10,000 for June 16-30 (15 days)
    var customer3Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId3)))
            .findFirst();

    assertThat(customer3Alloc).isPresent();
    // Average daily balance should be about 5,000 (10,000 * 15 / 30)
    assertThat(customer3Alloc.get().averageDailyBalance()).isLessThan(new BigDecimal("10000.00"));
    assertThat(BigDecimal.ZERO.compareTo(customer3Alloc.get().grossInterest())).isLessThan(0);
  }

  @Test
  void rounding_lpffShareRoundedFirst_clientShareIsDifference() throws Exception {
    // Create a deposit that will produce non-round interest amounts
    var customerId4 =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner"),
            "Interest Client Delta",
            "delta@test.com");

    createDeposit(
        trustAccountId, customerId4, new BigDecimal("33333.33"), "DEP-INT-004", "2026-07-01");

    // Create interest run for July 2026 (31 days)
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    var customer4Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId4)))
            .findFirst()
            .orElseThrow();

    // Verify rounding: lpff_share + client_share must equal gross_interest exactly
    assertThat(customer4Alloc.lpffShare().add(customer4Alloc.clientShare()))
        .isEqualByComparingTo(customer4Alloc.grossInterest());

    // LPFF share should be rounded to 2 decimal places
    assertThat(customer4Alloc.lpffShare().scale()).isLessThanOrEqualTo(2);
    assertThat(customer4Alloc.clientShare().scale()).isLessThanOrEqualTo(2);
  }

  @Test
  void clientWithNoTransactionsInPeriod_butOpeningBalance() throws Exception {
    // Customer1 already has deposits from earlier months
    // Create interest run for August 2026 -- no new transactions, but opening balance exists
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    // Should still generate interest based on opening balance
    assertThat(BigDecimal.ZERO.compareTo(calculated.totalInterest())).isLessThan(0);

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    // Customer1 should have an allocation from their existing balance
    var customer1Alloc =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId1)))
            .findFirst();
    assertThat(customer1Alloc).isPresent();
    assertThat(BigDecimal.ZERO.compareTo(customer1Alloc.get().grossInterest())).isLessThan(0);
  }

  @Test
  void emptyAccount_noAllocationsCreated() throws Exception {
    // Create a separate trust account with no deposits
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner");
    var emptyAccountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Empty Interest Test Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000101",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var emptyAccountId = UUID.fromString(TestEntityHelper.extractId(emptyAccountResult));

    // Create LPFF rate for the empty account
    mockMvc
        .perform(
            post("/api/trust-accounts/" + emptyAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Test rate for empty account"
                    }
                    """))
        .andExpect(status().isCreated());

    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        emptyAccountId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    // No allocations, zero totals
    assertThat(calculated.totalInterest()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(calculated.totalLpffShare()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(calculated.totalClientShare()).isEqualByComparingTo(BigDecimal.ZERO);

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));
    assertThat(allocations).isEmpty();
  }

  // ==========================================================================
  // 445.7 -- Edge Case Tests (3 tests)
  // ==========================================================================

  @Test
  void overlappingRunPrevention() throws Exception {
    // Create first run for October 2026
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                interestService.createInterestRun(
                    trustAccountId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

    // Try to create overlapping run -- should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, ownerMemberId)
                    .call(
                        () ->
                            interestService.createInterestRun(
                                trustAccountId,
                                LocalDate.of(2026, 10, 15),
                                LocalDate.of(2026, 11, 15))))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("overlaps");
  }

  @Test
  void noLpffRateAvailable_returnsError() throws Exception {
    // Create a separate trust account with no LPFF rate
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_owner");
    var noRateAccountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "No Rate Interest Test Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000102",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var noRateAccountId = UUID.fromString(TestEntityHelper.extractId(noRateAccountResult));

    // Try to create interest run -- should fail because no LPFF rate configured
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, ownerMemberId)
                    .call(
                        () ->
                            interestService.createInterestRun(
                                noRateAccountId,
                                LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 11, 30))))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("LPFF rate");
  }

  @Test
  void singleDayPeriod_calculatesCorrectly() throws Exception {
    // Create interest run for a single day (December 1, 2026)
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1)));

    assertThat(runResponse.status()).isEqualTo("DRAFT");

    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runResponse.id()));

    // Should calculate for exactly 1 day
    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    for (var alloc : allocations) {
      assertThat(alloc.daysInPeriod()).isEqualTo(1);
      // Verify: gross = avg_balance * (rate / 365) * 1
      assertThat(BigDecimal.ZERO.compareTo(alloc.grossInterest())).isLessThanOrEqualTo(0);
      // LPFF + client = gross
      assertThat(alloc.lpffShare().add(alloc.clientShare()))
          .isEqualByComparingTo(alloc.grossInterest());
    }
  }

  // ==========================================================================
  // 445.11 -- Posting Integration Tests (4 tests)
  // ==========================================================================

  @Test
  void postInterestRun_createsInterestCreditPerClient() throws Exception {
    // Create, calculate, approve (by admin), and post an interest run
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .call(() -> interestService.approveInterestRun(runResponse.id()));

    var posted =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(() -> interestService.postInterestRun(runResponse.id()));

    assertThat(posted.status()).isEqualTo("POSTED");
    assertThat(posted.postedAt()).isNotNull();

    // Verify allocations have trust_transaction_id set
    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(posted.id()));

    for (var alloc : allocations) {
      if (alloc.clientShare().compareTo(BigDecimal.ZERO) > 0) {
        assertThat(alloc.trustTransactionId()).isNotNull();

        // Verify the transaction exists and is INTEREST_CREDIT
        var txn =
            ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                .call(() -> trustTransactionRepository.findById(alloc.trustTransactionId()));
        assertThat(txn).isPresent();
        assertThat(txn.get().getTransactionType()).isEqualTo("INTEREST_CREDIT");
        assertThat(txn.get().getAmount()).isEqualByComparingTo(alloc.clientShare());
      }
    }
  }

  @Test
  void postInterestRun_createsSingleInterestLpffTransaction() throws Exception {
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .call(() -> interestService.approveInterestRun(runResponse.id()));

    var posted =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(() -> interestService.postInterestRun(runResponse.id()));

    // Find INTEREST_LPFF transaction with matching reference
    var lpffRef = "INT-LPFF-" + posted.id();
    var allTxns =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    trustTransactionRepository
                        .findByTrustAccountIdOrderByTransactionDateDesc(
                            trustAccountId, org.springframework.data.domain.Pageable.unpaged())
                        .getContent());

    var lpffTxns =
        allTxns.stream()
            .filter(t -> "INTEREST_LPFF".equals(t.getTransactionType()))
            .filter(t -> t.getReference().equals(lpffRef))
            .toList();

    assertThat(lpffTxns).hasSize(1);
    assertThat(lpffTxns.getFirst().getAmount()).isEqualByComparingTo(posted.totalLpffShare());
    assertThat(lpffTxns.getFirst().getCustomerId()).isNull();
  }

  @Test
  void postInterestRun_clientLedgerBalancesIncreasedByClientShare() throws Exception {
    // Record pre-posting balances
    var preBalance1 =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    clientLedgerCardRepository
                        .findByTrustAccountIdAndCustomerId(
                            trustAccountId, UUID.fromString(customerId1))
                        .orElseThrow()
                        .getBalance());

    var preBalance2 =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    clientLedgerCardRepository
                        .findByTrustAccountIdAndCustomerId(
                            trustAccountId, UUID.fromString(customerId2))
                        .orElseThrow()
                        .getBalance());

    // Create, calculate, approve, post
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    // Get allocations to know expected client shares
    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .call(() -> interestService.approveInterestRun(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(() -> interestService.postInterestRun(runResponse.id()));

    // Check post-posting balances
    var postBalance1 =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    clientLedgerCardRepository
                        .findByTrustAccountIdAndCustomerId(
                            trustAccountId, UUID.fromString(customerId1))
                        .orElseThrow()
                        .getBalance());

    var postBalance2 =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    clientLedgerCardRepository
                        .findByTrustAccountIdAndCustomerId(
                            trustAccountId, UUID.fromString(customerId2))
                        .orElseThrow()
                        .getBalance());

    var customer1Share =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId1)))
            .findFirst()
            .map(a -> a.clientShare())
            .orElse(BigDecimal.ZERO);

    var customer2Share =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(customerId2)))
            .findFirst()
            .map(a -> a.clientShare())
            .orElse(BigDecimal.ZERO);

    assertThat(postBalance1).isEqualByComparingTo(preBalance1.add(customer1Share));
    assertThat(postBalance2).isEqualByComparingTo(preBalance2.add(customer2Share));
  }

  @Test
  void postedRun_cannotBeRecalculated() throws Exception {
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 30)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .call(() -> interestService.approveInterestRun(runResponse.id()));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(() -> interestService.postInterestRun(runResponse.id()));

    // Try to recalculate -- should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .call(() -> interestService.calculateInterest(runResponse.id())))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("non-DRAFT");
  }

  // ==========================================================================
  // 445.13 -- Self-Approval Prevention Test (1 test)
  // ==========================================================================

  @Test
  void creatorCannotApproveOwnInterestRun() throws Exception {
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 31)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    // Owner created it, owner tries to approve -- should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, ownerMemberId)
                    .call(() -> interestService.approveInterestRun(runResponse.id())))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Self-approval not allowed");
  }
}
