package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateLpffRateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.UpdateTrustAccountRequest;
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
class TrustAccountServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_trust_account_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Account Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_trust_svc_owner", "trust_svc@test.com", "Trust Svc Owner", "owner"));

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
  }

  @Test
  void createTrustAccount_savesWithActiveStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "Test Trust Account",
                          "First National Bank",
                          "250655",
                          "1234567890",
                          "GENERAL",
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Test notes");

                  var response = trustAccountService.createTrustAccount(request);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.accountName()).isEqualTo("Test Trust Account");
                  assertThat(response.bankName()).isEqualTo("First National Bank");
                  assertThat(response.branchCode()).isEqualTo("250655");
                  assertThat(response.accountNumber()).isEqualTo("1234567890");
                  assertThat(response.accountType()).isEqualTo("GENERAL");
                  assertThat(response.isPrimary()).isTrue();
                  assertThat(response.status()).isEqualTo("ACTIVE");
                  assertThat(response.openedDate()).isEqualTo(LocalDate.of(2026, 1, 1));
                }));
  }

  @Test
  void createTrustAccount_emitsAuditEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long auditCountBefore = auditEventRepository.count();

                  var request =
                      new CreateTrustAccountRequest(
                          "Audit Test Account",
                          "Audit Bank",
                          "123456",
                          "9876543210",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 2, 1),
                          null);

                  trustAccountService.createTrustAccount(request);

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  @Test
  void updateTrustAccount_changesBankDetails() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "Update Test Account",
                          "Old Bank",
                          "111111",
                          "0000000001",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 15),
                          null);

                  var created = trustAccountService.createTrustAccount(createRequest);

                  var updateRequest =
                      new UpdateTrustAccountRequest(
                          "Updated Account Name",
                          "New Bank",
                          "222222",
                          "0000000002",
                          true,
                          new BigDecimal("50000.00"),
                          "Updated notes");

                  var updated = trustAccountService.updateTrustAccount(created.id(), updateRequest);

                  assertThat(updated.accountName()).isEqualTo("Updated Account Name");
                  assertThat(updated.bankName()).isEqualTo("New Bank");
                  assertThat(updated.branchCode()).isEqualTo("222222");
                  assertThat(updated.accountNumber()).isEqualTo("0000000002");
                  assertThat(updated.requireDualApproval()).isTrue();
                  assertThat(updated.paymentApprovalThreshold())
                      .isEqualByComparingTo(new BigDecimal("50000.00"));
                  assertThat(updated.notes()).isEqualTo("Updated notes");
                }));
  }

  @Test
  void closeTrustAccount_succeedsWhenNoClientLedgerBalances() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "Close Test Account",
                          "Close Bank",
                          "333333",
                          "0000000003",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 20),
                          null);

                  var created = trustAccountService.createTrustAccount(createRequest);

                  var closed = trustAccountService.closeTrustAccount(created.id());

                  assertThat(closed.status()).isEqualTo("CLOSED");
                  assertThat(closed.closedDate()).isNotNull();
                }));
  }

  @Test
  void closeTrustAccount_failsWhenAlreadyClosed() {
    // Create and close in one transaction
    UUID[] accountId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "Already Closed Account",
                          "Some Bank",
                          "444444",
                          "0000000004",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 25),
                          null);

                  var created = trustAccountService.createTrustAccount(createRequest);
                  trustAccountService.closeTrustAccount(created.id());
                  accountId[0] = created.id();
                }));

    // Assert in a separate scope — the exception marks the transaction rollback-only
    runInTenant(
        () ->
            assertThatThrownBy(() -> trustAccountService.closeTrustAccount(accountId[0]))
                .hasMessageContaining("already closed"));
  }

  @Test
  void listTrustAccounts_returnsActiveAccountsOnly() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create an active account
                  var activeRequest =
                      new CreateTrustAccountRequest(
                          "Active List Account",
                          "List Bank",
                          "555555",
                          "0000000005",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 2, 1),
                          null);

                  trustAccountService.createTrustAccount(activeRequest);

                  // Create and close another account
                  var closedRequest =
                      new CreateTrustAccountRequest(
                          "Closed List Account",
                          "List Bank 2",
                          "666666",
                          "0000000006",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 2, 1),
                          null);

                  var closedAccount = trustAccountService.createTrustAccount(closedRequest);
                  trustAccountService.closeTrustAccount(closedAccount.id());

                  var activeAccounts = trustAccountService.listTrustAccounts();

                  assertThat(activeAccounts).allMatch(a -> "ACTIVE".equals(a.status()));
                  assertThat(activeAccounts)
                      .anyMatch(a -> "Active List Account".equals(a.accountName()));
                  assertThat(activeAccounts)
                      .noneMatch(a -> "Closed List Account".equals(a.accountName()));
                }));
  }

  @Test
  void addLpffRate_andResolveEffectiveRate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "LPFF Test Account",
                          "LPFF Bank",
                          "777777",
                          "0000000007",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          null);

                  var account = trustAccountService.createTrustAccount(createRequest);

                  // Add an older rate
                  trustAccountService.addLpffRate(
                      account.id(),
                      new CreateLpffRateRequest(
                          LocalDate.of(2026, 1, 1),
                          new BigDecimal("0.0500"),
                          new BigDecimal("0.2500"),
                          "Initial rate"));

                  // Add a newer rate
                  trustAccountService.addLpffRate(
                      account.id(),
                      new CreateLpffRateRequest(
                          LocalDate.of(2026, 3, 1),
                          new BigDecimal("0.0600"),
                          new BigDecimal("0.3000"),
                          "Updated rate"));

                  // As of Feb 2026, should get the January rate
                  var febRate =
                      trustAccountService.getCurrentLpffRate(
                          account.id(), LocalDate.of(2026, 2, 15));
                  assertThat(febRate.ratePercent()).isEqualByComparingTo(new BigDecimal("0.0500"));
                  assertThat(febRate.lpffSharePercent())
                      .isEqualByComparingTo(new BigDecimal("0.2500"));

                  // As of April 2026, should get the March rate
                  var aprRate =
                      trustAccountService.getCurrentLpffRate(
                          account.id(), LocalDate.of(2026, 4, 1));
                  assertThat(aprRate.ratePercent()).isEqualByComparingTo(new BigDecimal("0.0600"));
                  assertThat(aprRate.lpffSharePercent())
                      .isEqualByComparingTo(new BigDecimal("0.3000"));

                  // List rates should return both, newest first
                  var allRates = trustAccountService.listLpffRates(account.id());
                  assertThat(allRates).hasSize(2);
                  assertThat(allRates.get(0).effectiveFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
                }));
  }

  @Test
  void moduleGuard_throwsWhenTrustAccountingNotEnabled() {
    // Provision a separate tenant without trust_accounting enabled
    String noModuleOrg = "org_trust_no_module";
    String noModuleSchema;
    try {
      noModuleSchema =
          provisioningService.provisionTenant(noModuleOrg, "No Module Org", null).schemaName();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    UUID noModuleMemberId;
    try {
      noModuleMemberId =
          UUID.fromString(
              syncMember(
                  noModuleOrg,
                  "user_no_module_owner",
                  "no_module@test.com",
                  "No Module Owner",
                  "owner"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final String schema = noModuleSchema;
    final UUID mid = noModuleMemberId;

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, noModuleOrg)
        .where(RequestScopes.MEMBER_ID, mid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              assertThatThrownBy(() -> trustAccountService.listTrustAccounts())
                  .isInstanceOf(
                      io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException.class);
            });
  }

  @Test
  void getCurrentLpffRate_withNoRates_throwsError() {
    UUID[] accountId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "No Rates Account",
                          "No Rates Bank",
                          "888888",
                          "0000000008",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          null);

                  var account = trustAccountService.createTrustAccount(createRequest);
                  accountId[0] = account.id();
                }));

    // Assert outside transaction to avoid rollback-only marking
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        trustAccountService.getCurrentLpffRate(
                            accountId[0], LocalDate.of(2026, 6, 1)))
                .hasMessageContaining("No LPFF rate configured"));
  }

  @Test
  void getCurrentLpffRate_returnsMostRecentBeforeDate() {
    UUID[] accountId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateTrustAccountRequest(
                          "Rate Resolution Account",
                          "Rate Bank",
                          "999999",
                          "0000000009",
                          "INVESTMENT",
                          false,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          null);

                  var account = trustAccountService.createTrustAccount(createRequest);
                  accountId[0] = account.id();

                  // Add three rates
                  trustAccountService.addLpffRate(
                      account.id(),
                      new CreateLpffRateRequest(
                          LocalDate.of(2026, 1, 1),
                          new BigDecimal("0.0400"),
                          new BigDecimal("0.2000"),
                          "Q1 rate"));

                  trustAccountService.addLpffRate(
                      account.id(),
                      new CreateLpffRateRequest(
                          LocalDate.of(2026, 4, 1),
                          new BigDecimal("0.0500"),
                          new BigDecimal("0.2500"),
                          "Q2 rate"));

                  trustAccountService.addLpffRate(
                      account.id(),
                      new CreateLpffRateRequest(
                          LocalDate.of(2026, 7, 1),
                          new BigDecimal("0.0600"),
                          new BigDecimal("0.3000"),
                          "Q3 rate"));

                  // Query for a date in Q2 — should get Q2 rate
                  var q2Rate =
                      trustAccountService.getCurrentLpffRate(
                          account.id(), LocalDate.of(2026, 5, 15));
                  assertThat(q2Rate.ratePercent()).isEqualByComparingTo(new BigDecimal("0.0500"));

                  // Query for exact Q3 start — should get Q3 rate
                  var q3Rate =
                      trustAccountService.getCurrentLpffRate(
                          account.id(), LocalDate.of(2026, 7, 1));
                  assertThat(q3Rate.ratePercent()).isEqualByComparingTo(new BigDecimal("0.0600"));
                }));

    // Assert exception outside transaction to avoid rollback-only marking
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        trustAccountService.getCurrentLpffRate(
                            accountId[0], LocalDate.of(2025, 12, 31)))
                .hasMessageContaining("No LPFF rate configured"));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
