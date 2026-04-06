package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountingConstants;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
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

/**
 * Integration tests for investment basis distinction in interest calculations. Validates that
 * CLIENT_INSTRUCTION investments use the statutory 5% LPFF share (Section 86(5)), while
 * FIRM_DISCRETION investments use the configured LpffRate table rate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentBasisInterestTest {

  private static final String ORG_ID = "org_inv_basis_interest_test";

  // The LPFF rate configured for the test account (75% LPFF share)
  private static final BigDecimal CONFIGURED_LPFF_SHARE = new BigDecimal("0.7500");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InterestService interestService;
  @Autowired private InterestAllocationRepository interestAllocationRepository;
  @Autowired private TrustInvestmentService investmentService;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID ownerMemberId;
  private UUID adminMemberId;
  private String clientInstructionCustomerId;
  private String firmDiscretionCustomerId;
  private String mixedCustomerId;
  private UUID interestRunId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Investment Basis Interest Test Org", null)
            .schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_basis_owner",
                "inv_basis_owner@test.com",
                "Inv Basis Owner",
                "owner"));

    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_basis_admin",
                "inv_basis_admin@test.com",
                "Inv Basis Admin",
                "admin"));

    // Enable trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner");

    // Create trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Inv Basis Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000500",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-12-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    // Create LPFF rate: 7.5% annual, 75% LPFF share, effective 2025-12-01
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2025-12-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Test rate for investment basis tests"
                    }
                    """))
        .andExpect(status().isCreated());

    // Create three customers (one for each scenario)
    clientInstructionCustomerId =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "CI Client", "ci_client@test.com");
    firmDiscretionCustomerId =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "FD Client", "fd_client@test.com");
    mixedCustomerId =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "Mixed Client", "mixed_client@test.com");

    // Deposit funds for all customers (50,000 each)
    for (var custId :
        List.of(clientInstructionCustomerId, firmDiscretionCustomerId, mixedCustomerId)) {
      createDeposit(
          trustAccountId, custId, new BigDecimal("50000.00"), "DEP-" + custId, "2026-01-01");
    }

    // Place investments for each customer
    // Customer 1: CLIENT_INSTRUCTION only
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new TrustInvestmentService.PlaceInvestmentRequest(
                        UUID.fromString(clientInstructionCustomerId),
                        "Investec",
                        "1110001111",
                        new BigDecimal("10000.00"),
                        new BigDecimal("0.0800"),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 7, 15),
                        "Client-instructed investment",
                        InvestmentBasis.CLIENT_INSTRUCTION)));

    // Customer 2: FIRM_DISCRETION only
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new TrustInvestmentService.PlaceInvestmentRequest(
                        UUID.fromString(firmDiscretionCustomerId),
                        "ABSA",
                        "2220002222",
                        new BigDecimal("10000.00"),
                        new BigDecimal("0.0700"),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 7, 15),
                        "Firm-discretion investment",
                        InvestmentBasis.FIRM_DISCRETION)));

    // Customer 3: Mixed (one of each)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new TrustInvestmentService.PlaceInvestmentRequest(
                        UUID.fromString(mixedCustomerId),
                        "Nedbank",
                        "3330003333",
                        new BigDecimal("5000.00"),
                        new BigDecimal("0.0600"),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 7, 15),
                        "Client-instructed in mixed",
                        InvestmentBasis.CLIENT_INSTRUCTION)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new TrustInvestmentService.PlaceInvestmentRequest(
                        UUID.fromString(mixedCustomerId),
                        "Standard Bank",
                        "4440004444",
                        new BigDecimal("5000.00"),
                        new BigDecimal("0.0650"),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 7, 15),
                        "Firm-discretion in mixed",
                        InvestmentBasis.FIRM_DISCRETION)));

    // Create and calculate interest run for all tests to share
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(() -> interestService.calculateInterest(runResponse.id()));

    interestRunId = runResponse.id();
  }

  private void createDeposit(
      UUID accountId, String custId, BigDecimal amount, String reference, String date)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner");
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
                        .formatted(custId, amount.toPlainString(), reference, date)))
        .andExpect(status().isCreated());
  }

  // --- 453.5: Statutory rate enforcement tests ---

  @Test
  void clientInstruction_usesStatutory5PercentLpffShare() throws Exception {
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var ciAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(clientInstructionCustomerId)))
            .findFirst()
            .orElseThrow();

    // Verify LPFF share uses statutory 5% (not the configured 75%)
    var expectedLpffShare =
        ciAllocation
            .grossInterest()
            .multiply(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
            .setScale(2, RoundingMode.HALF_UP);
    assertThat(ciAllocation.lpffShare()).isEqualByComparingTo(expectedLpffShare);
    assertThat(ciAllocation.statutoryRateApplied()).isTrue();
  }

  @Test
  void firmDiscretion_usesLpffRateTableRate() throws Exception {
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var fdAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(firmDiscretionCustomerId)))
            .findFirst()
            .orElseThrow();

    // Verify LPFF share uses the configured rate (75%), not the statutory 5%
    var expectedLpffShare =
        fdAllocation
            .grossInterest()
            .multiply(CONFIGURED_LPFF_SHARE)
            .setScale(2, RoundingMode.HALF_UP);
    assertThat(fdAllocation.lpffShare()).isEqualByComparingTo(expectedLpffShare);
    assertThat(fdAllocation.statutoryRateApplied()).isFalse();
  }

  @Test
  void mixedBasis_appliesStatutoryRate() throws Exception {
    // When a client has ANY CLIENT_INSTRUCTION investment, the statutory rate applies
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var mixedAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(mixedCustomerId)))
            .findFirst()
            .orElseThrow();

    // Mixed client has a CLIENT_INSTRUCTION investment, so statutory rate should apply
    var expectedLpffShare =
        mixedAllocation
            .grossInterest()
            .multiply(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
            .setScale(2, RoundingMode.HALF_UP);
    assertThat(mixedAllocation.lpffShare()).isEqualByComparingTo(expectedLpffShare);
    assertThat(mixedAllocation.statutoryRateApplied()).isTrue();
  }

  @Test
  void clientInstruction_hasStatutoryRateAppliedTrueAndNullLpffRateId() throws Exception {
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var ciAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(clientInstructionCustomerId)))
            .findFirst()
            .orElseThrow();

    assertThat(ciAllocation.statutoryRateApplied()).isTrue();
    assertThat(ciAllocation.lpffRateId()).isNull();
  }

  @Test
  void firmDiscretion_hasStatutoryRateAppliedFalseAndLpffRateIdPopulated() throws Exception {
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var fdAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(firmDiscretionCustomerId)))
            .findFirst()
            .orElseThrow();

    assertThat(fdAllocation.statutoryRateApplied()).isFalse();
    assertThat(fdAllocation.lpffRateId()).isNotNull();
  }

  @Test
  void backwardCompatibility_defaultFirmDiscretionUsesGeneralRate() throws Exception {
    // The FIRM_DISCRETION customer should use the general (configured) rate
    // This is the backward-compatible behavior (same as before this feature)
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    var fdAllocation =
        allocations.stream()
            .filter(a -> a.customerId().equals(UUID.fromString(firmDiscretionCustomerId)))
            .findFirst()
            .orElseThrow();

    // The LPFF share should be computed with the general configured rate
    assertThat(fdAllocation.grossInterest()).isPositive();
    assertThat(fdAllocation.lpffShare()).isPositive();
    assertThat(fdAllocation.clientShare()).isPositive();
    // Client share = gross - LPFF; with 75% LPFF, client gets 25%
    assertThat(fdAllocation.clientShare())
        .isEqualByComparingTo(fdAllocation.grossInterest().subtract(fdAllocation.lpffShare()));
  }

  // --- 453.7: Interest allocation audit trail tests ---

  @Test
  void auditTrail_allAllocationsHaveCorrectRateSource() throws Exception {
    var runId = interestRunId;

    var allocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.getAllocations(runId));

    for (var alloc : allocations) {
      if (alloc.customerId().equals(UUID.fromString(clientInstructionCustomerId))
          || alloc.customerId().equals(UUID.fromString(mixedCustomerId))) {
        // CLIENT_INSTRUCTION or mixed: statutory rate
        assertThat(alloc.statutoryRateApplied())
            .as("Allocation for customer %s should have statutory rate applied", alloc.customerId())
            .isTrue();
        assertThat(alloc.lpffRateId())
            .as("Allocation for customer %s should have null lpffRateId", alloc.customerId())
            .isNull();
      } else if (alloc.customerId().equals(UUID.fromString(firmDiscretionCustomerId))) {
        // FIRM_DISCRETION: configured rate
        assertThat(alloc.statutoryRateApplied())
            .as(
                "Allocation for customer %s should NOT have statutory rate applied",
                alloc.customerId())
            .isFalse();
        assertThat(alloc.lpffRateId())
            .as("Allocation for customer %s should have lpffRateId populated", alloc.customerId())
            .isNotNull();
      }
    }
  }

  @Test
  void auditTrail_filterByStatutoryRateApplied_allUse5Percent() throws Exception {
    var runId = interestRunId;

    // Query allocations directly via repository to filter by statutory_rate_applied
    var allAllocations =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestAllocationRepository.findByInterestRunId(runId));

    var statutoryAllocations =
        allAllocations.stream().filter(InterestAllocation::isStatutoryRateApplied).toList();

    assertThat(statutoryAllocations).isNotEmpty();

    for (var alloc : statutoryAllocations) {
      // Verify each statutory allocation uses exactly 5%
      var expectedLpff =
          alloc
              .getGrossInterest()
              .multiply(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
              .setScale(2, RoundingMode.HALF_UP);
      assertThat(alloc.getLpffShare())
          .as(
              "Statutory allocation for customer %s should use exactly 5%% LPFF share",
              alloc.getCustomerId())
          .isEqualByComparingTo(expectedLpff);
    }
  }
}
