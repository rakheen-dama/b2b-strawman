package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
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
class TrustInvestmentServiceIntegrationTest {

  private static final String ORG_ID = "org_trust_invest_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustInvestmentRepository investmentRepository;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID customerId;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Investment Test Org", null).schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_invest_owner",
                "invest_owner@test.com",
                "Investment Owner",
                "owner"));

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

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_owner");

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
                          "accountName": "Investment Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000200",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    // Create customer
    customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc, ownerJwt, "Investment Client Alpha", "invest_alpha@test.com"));

    // Deposit funds for the customer (100,000)
    createDeposit(
        trustAccountId,
        customerId.toString(),
        new BigDecimal("100000.00"),
        "DEP-INVEST-001",
        "2026-01-15");
  }

  // --- Helper Methods ---

  private void createDeposit(
      UUID accountId, String custId, BigDecimal amount, String reference, String date)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_owner");
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

  // --- 446.4: Investment Lifecycle Tests ---

  @Test
  void placeInvestment_createsPaymentTransactionAndInvestmentRecord() throws Exception {
    var response =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            customerId,
                            "ABSA Bank",
                            "9012345678",
                            new BigDecimal("50000.00"),
                            new BigDecimal("0.0650"),
                            LocalDate.of(2026, 2, 1),
                            LocalDate.of(2026, 8, 1),
                            "6-month fixed deposit")));

    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.principal()).isEqualByComparingTo(new BigDecimal("50000.00"));
    assertThat(response.interestRate()).isEqualByComparingTo(new BigDecimal("0.0650"));
    assertThat(response.institution()).isEqualTo("ABSA Bank");
    assertThat(response.accountNumber()).isEqualTo("9012345678");
    assertThat(response.interestEarned()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.depositTransactionId()).isNotNull();
    assertThat(response.maturityDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(response.notes()).isEqualTo("6-month fixed deposit");
    assertThat(response.trustAccountId()).isEqualTo(trustAccountId);
    assertThat(response.customerId()).isEqualTo(customerId);
  }

  @Test
  void recordInterestEarned_incrementsTotal() throws Exception {
    // Place an investment first
    var placed =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            customerId,
                            "Nedbank",
                            "5551234567",
                            new BigDecimal("10000.00"),
                            new BigDecimal("0.0700"),
                            LocalDate.of(2026, 2, 1),
                            LocalDate.of(2026, 5, 1),
                            null)));

    // Record interest twice
    var afterFirst =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.recordInterestEarned(placed.id(), new BigDecimal("175.00")));

    assertThat(afterFirst.interestEarned()).isEqualByComparingTo(new BigDecimal("175.00"));

    var afterSecond =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.recordInterestEarned(placed.id(), new BigDecimal("125.00")));

    assertThat(afterSecond.interestEarned()).isEqualByComparingTo(new BigDecimal("300.00"));
  }

  @Test
  void withdrawInvestment_createsDepositWithPrincipalPlusInterest() throws Exception {
    // Place an investment
    var placed =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            customerId,
                            "Standard Bank",
                            "7778889990",
                            new BigDecimal("5000.00"),
                            new BigDecimal("0.0550"),
                            LocalDate.of(2026, 2, 1),
                            null, // call deposit — no maturity date
                            "Call deposit")));

    // Record some interest
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .run(() -> investmentService.recordInterestEarned(placed.id(), new BigDecimal("250.00")));

    // Withdraw
    var withdrawn =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(() -> investmentService.withdrawInvestment(placed.id()));

    assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
    assertThat(withdrawn.withdrawalAmount())
        .isEqualByComparingTo(new BigDecimal("5250.00")); // 5000 + 250
    assertThat(withdrawn.withdrawalDate()).isEqualTo(LocalDate.now());
    assertThat(withdrawn.withdrawalTransactionId()).isNotNull();
  }

  @Test
  void getMaturing_returnsInvestmentsMaturingWithinWindow() throws Exception {
    // Place an investment that matures soon (within 30 days from today)
    var maturingSoon =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            customerId,
                            "FNB",
                            "1112223334",
                            new BigDecimal("2000.00"),
                            new BigDecimal("0.0500"),
                            LocalDate.now().minusDays(60),
                            LocalDate.now().plusDays(10), // matures in 10 days
                            "Short-term deposit")));

    // Place an investment that matures far in the future
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new TrustInvestmentService.PlaceInvestmentRequest(
                        customerId,
                        "Capitec",
                        "4445556667",
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.0400"),
                        LocalDate.now().minusDays(30),
                        LocalDate.now().plusDays(365), // matures in a year
                        "Long-term deposit")));

    // Query for investments maturing within 30 days
    var maturing =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(() -> investmentService.getMaturing(trustAccountId, 30));

    assertThat(maturing).isNotEmpty();
    assertThat(maturing).anyMatch(inv -> inv.id().equals(maturingSoon.id()));
    assertThat(maturing)
        .noneMatch(
            inv -> inv.institution().equals("Capitec") && inv.accountNumber().equals("4445556667"));
  }

  // --- 446.5: Investment Edge Case Tests ---

  @Test
  void placeInvestment_withInsufficientBalance_fails() {
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, ownerMemberId)
                    .call(
                        () ->
                            investmentService.placeInvestment(
                                trustAccountId,
                                new TrustInvestmentService.PlaceInvestmentRequest(
                                    customerId,
                                    "Investec",
                                    "9998887776",
                                    new BigDecimal("999999999.00"), // way more than balance
                                    new BigDecimal("0.0800"),
                                    LocalDate.of(2026, 3, 1),
                                    LocalDate.of(2026, 9, 1),
                                    null))))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Insufficient balance");
  }

  @Test
  void withdrawInvestment_alreadyWithdrawn_fails() throws Exception {
    // Place and withdraw an investment
    var placed =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            customerId,
                            "Absa",
                            "3332221110",
                            new BigDecimal("1000.00"),
                            new BigDecimal("0.0500"),
                            LocalDate.of(2026, 2, 1),
                            null,
                            null)));

    // Withdraw first time — should succeed
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .run(() -> investmentService.withdrawInvestment(placed.id()));

    // Withdraw again — should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, ownerMemberId)
                    .call(() -> investmentService.withdrawInvestment(placed.id())))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("ACTIVE or MATURED");
  }
}
