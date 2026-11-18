package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateLpffRateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.TrustAccountResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.UpdateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountServiceTest {
  private static final String ORG_ID = "org_trust_account_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TrustCreditorAccountRepository creditorAccountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

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
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_svc_owner",
                "trust_svc@test.com",
                "Trust Svc Owner",
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
                  assertThat(response.accountType()).isEqualTo(TrustAccountType.GENERAL);
                  assertThat(response.isPrimary()).isTrue();
                  assertThat(response.status()).isEqualTo(TrustAccountStatus.ACTIVE);
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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

                  trustAccountService.createTrustAccount(request);

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  @Test
  void createTrustAccount_opensTheTwoNonClientCreditorAccounts() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      trustAccountService.createTrustAccount(
                          new CreateTrustAccountRequest(
                              "Creditor Ledgers Account",
                              "Nedbank",
                              "198765",
                              "5550001111",
                              "GENERAL",
                              false,
                              false,
                              null,
                              LocalDate.of(2026, 2, 1),
                              null));

                  var creditorAccounts =
                      creditorAccountRepository.findByTrustAccountId(response.id());
                  assertThat(creditorAccounts)
                      .extracting(
                          TrustCreditorAccount::getAccountType, TrustCreditorAccount::getName)
                      .containsExactlyInAnyOrder(
                          tuple(
                              TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
                              "Legal Practitioners' Fidelity Fund — interest and charges"),
                          tuple(
                              TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS,
                              "Unidentified deposits"));
                  assertThat(creditorAccounts)
                      .allSatisfy(
                          a -> {
                            assertThat(a.getTrustAccountId()).isEqualTo(response.id());
                            assertThat(a.getCreatedAt()).isNotNull();
                          });

                  // One trust_creditor_account.created audit per opened ledger (D2)
                  for (var creditorAccount : creditorAccounts) {
                    var events =
                        auditEventRepository
                            .findByFilter(
                                "trust_creditor_account",
                                creditorAccount.getId(),
                                null,
                                "trust_creditor_account.created",
                                null,
                                null,
                                PageRequest.of(0, 10))
                            .getContent();
                    assertThat(events).hasSize(1);
                  }
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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

                  var created = trustAccountService.createTrustAccount(createRequest);

                  var updateRequest =
                      new UpdateTrustAccountRequest(
                          "Updated Account Name",
                          "New Bank",
                          "222222",
                          "0000000002",
                          true,
                          new BigDecimal("50000.00"),
                          "Updated notes",
                          null);

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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

                  var created = trustAccountService.createTrustAccount(createRequest);

                  var closed = trustAccountService.closeTrustAccount(created.id());

                  assertThat(closed.status()).isEqualTo(TrustAccountStatus.CLOSED);
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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

                  var closedAccount = trustAccountService.createTrustAccount(closedRequest);
                  trustAccountService.closeTrustAccount(closedAccount.id());

                  var activeAccounts = trustAccountService.listTrustAccounts();

                  assertThat(activeAccounts).allMatch(a -> TrustAccountStatus.ACTIVE == a.status());
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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

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
              TestMemberHelper.syncMember(
                  mockMvc,
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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

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
                          null,
                          InvestmentBasis.FIRM_DISCRETION);

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

  // --- 648C: an investment account carries its basis (ADR-379) ---

  @Test
  void createInvestmentAccount_carriesItsBasisAndDerivedSection_andAuditsTheBasis() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var firm =
                      trustAccountService.createTrustAccount(
                          investmentRequest(
                              "Basis s86(3) Account", InvestmentBasis.FIRM_DISCRETION));
                  var client =
                      trustAccountService.createTrustAccount(
                          investmentRequest(
                              "Basis s86(4) Account", InvestmentBasis.CLIENT_INSTRUCTION));

                  assertThat(firm.investmentBasis()).isEqualTo(InvestmentBasis.FIRM_DISCRETION);
                  assertThat(firm.section()).isEqualTo(TrustAccountSection.S86_3);
                  assertThat(client.investmentBasis())
                      .isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
                  assertThat(client.section()).isEqualTo(TrustAccountSection.S86_4);

                  // Persisted, and read back the same way
                  assertThat(
                          trustAccountRepository
                              .findById(client.id())
                              .orElseThrow()
                              .getInvestmentBasis())
                      .isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
                  assertThat(trustAccountService.getTrustAccount(firm.id()).section())
                      .isEqualTo(TrustAccountSection.S86_3);

                  var created = createdAudit(client.id());
                  assertThat(created.getDetails())
                      .containsEntry("investment_basis", "CLIENT_INSTRUCTION")
                      .containsEntry("account_type", "INVESTMENT");
                }));
  }

  @Test
  void createNonInvestmentAccount_reportsUnderSection86_2_withNoBasis() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (var type : List.of("GENERAL", "SECTION_86")) {
                    var response =
                        trustAccountService.createTrustAccount(
                            new CreateTrustAccountRequest(
                                "No Basis " + type,
                                "Absa",
                                "632005",
                                "40" + type.length(),
                                type,
                                false,
                                false,
                                null,
                                LocalDate.of(2026, 3, 1),
                                null));

                    assertThat(response.investmentBasis()).isNull();
                    assertThat(response.section()).isEqualTo(TrustAccountSection.S86_2);
                    assertThat(createdAudit(response.id()).getDetails())
                        .doesNotContainKey("investment_basis");
                  }
                }));
  }

  @Test
  void createTrustAccount_refusesAnInvestmentWithoutBasis_andABasisOnAnyOtherType() {
    long before = accountCount();

    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        trustAccountService.createTrustAccount(investmentRequest("No Basis", null)))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getTitle()).isEqualTo("Investment basis required");
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "An investment account needs its basis: section 86(3) (the firm"
                                  + " invests surplus trust money) or section 86(4) (invested on a"
                                  + " client's instruction).");
                    }));

    for (var type : List.of("GENERAL", "SECTION_86")) {
      runInTenant(
          () ->
              assertThatThrownBy(
                      () ->
                          trustAccountService.createTrustAccount(
                              new CreateTrustAccountRequest(
                                  "Basis On " + type,
                                  "Absa",
                                  "632005",
                                  "5550009999",
                                  type,
                                  false,
                                  false,
                                  null,
                                  LocalDate.of(2026, 3, 1),
                                  null,
                                  InvestmentBasis.FIRM_DISCRETION)))
                  .isInstanceOfSatisfying(
                      InvalidStateException.class,
                      e -> {
                        assertThat(e.getBody().getTitle())
                            .isEqualTo("Investment basis not allowed");
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "Only an investment account carries an investment basis. A trust"
                                    + " banking account reports under section 86(2).");
                      }));
    }

    // Refused before anything is written
    assertThat(accountCount()).isEqualTo(before);
  }

  @Test
  void updateTrustAccount_setsTheBasisOnAnInvestmentAccountOnly() {
    UUID[] ids = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var investment =
                      trustAccountService.createTrustAccount(
                          investmentRequest(
                              "Basis Update Investment", InvestmentBasis.FIRM_DISCRETION));
                  ids[0] = investment.id();

                  // null = unchanged
                  var unchanged =
                      trustAccountService.updateTrustAccount(
                          investment.id(),
                          new UpdateTrustAccountRequest(
                              "Basis Update Investment (renamed)",
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null));
                  assertThat(unchanged.investmentBasis())
                      .isEqualTo(InvestmentBasis.FIRM_DISCRETION);
                  assertThat(unchanged.section()).isEqualTo(TrustAccountSection.S86_3);

                  var changed =
                      trustAccountService.updateTrustAccount(
                          investment.id(),
                          new UpdateTrustAccountRequest(
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              InvestmentBasis.CLIENT_INSTRUCTION));
                  assertThat(changed.investmentBasis())
                      .isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
                  assertThat(changed.section()).isEqualTo(TrustAccountSection.S86_4);

                  var updatedEvents =
                      auditEventRepository
                          .findByFilter(
                              "trust_account",
                              investment.id(),
                              null,
                              "trust_account.updated",
                              null,
                              null,
                              PageRequest.of(0, 10))
                          .getContent();
                  // the basis change records the prior basis (s86(3) -> s86(4)); the rename
                  // (basis unchanged) does not
                  assertThat(updatedEvents)
                      .anySatisfy(
                          e ->
                              assertThat(e.getDetails())
                                  .containsEntry("investment_basis", "CLIENT_INSTRUCTION")
                                  .containsEntry("investment_basis_from", "FIRM_DISCRETION"));
                  assertThat(updatedEvents)
                      .anySatisfy(
                          e ->
                              assertThat(e.getDetails())
                                  .containsEntry("investment_basis", "FIRM_DISCRETION")
                                  .doesNotContainKey("investment_basis_from"));

                  ids[1] =
                      trustAccountService
                          .createTrustAccount(
                              new CreateTrustAccountRequest(
                                  "Basis Update General",
                                  "Absa",
                                  "632005",
                                  "5550007777",
                                  "GENERAL",
                                  false,
                                  false,
                                  null,
                                  LocalDate.of(2026, 3, 1),
                                  null))
                          .id();
                }));

    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        trustAccountService.updateTrustAccount(
                            ids[1],
                            new UpdateTrustAccountRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                InvestmentBasis.FIRM_DISCRETION)))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e ->
                        assertThat(e.getBody().getTitle())
                            .isEqualTo("Investment basis not allowed")));

    runInTenant(
        () -> {
          assertThat(trustAccountRepository.findById(ids[1]).orElseThrow().getInvestmentBasis())
              .isNull();
          assertThat(trustAccountRepository.findById(ids[0]).orElseThrow().getInvestmentBasis())
              .isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
        });
  }

  // --- 648A (648C found.md F-2): a basis change is refused while investments are held ---

  @Test
  void updateTrustAccount_refusesABasisChange_whileTheAccountHoldsAnActiveOrMaturedInvestment() {
    for (var status : List.of("ACTIVE", "MATURED")) {
      UUID[] ids = new UUID[2];
      runInTenant(
          () ->
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    ids[0] =
                        trustAccountService
                            .createTrustAccount(
                                investmentRequest(
                                    "Basis Held " + status, InvestmentBasis.FIRM_DISCRETION))
                            .id();
                    ids[1] = fundingAccount("Basis Held Funding " + status);
                  }));
      investmentDirect(ids[1], ids[0], status);

      runInTenant(
          () ->
              assertThatThrownBy(
                      () ->
                          trustAccountService.updateTrustAccount(
                              ids[0], basisUpdate(InvestmentBasis.CLIENT_INSTRUCTION)))
                  .as("basis change with a %s investment", status)
                  .isInstanceOfSatisfying(
                      InvalidStateException.class,
                      e -> {
                        assertThat(e.getBody().getTitle())
                            .isEqualTo("Cannot change investment basis");
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "This investment account holds investments; withdraw them"
                                    + " before changing its basis.");
                      }));

      runInTenant(
          () -> {
            assertThat(trustAccountRepository.findById(ids[0]).orElseThrow().getInvestmentBasis())
                .isEqualTo(InvestmentBasis.FIRM_DISCRETION);
            // The same basis (no change) and other details still update
            var same =
                trustAccountService.updateTrustAccount(
                    ids[0], basisUpdate(InvestmentBasis.FIRM_DISCRETION));
            assertThat(same.investmentBasis()).isEqualTo(InvestmentBasis.FIRM_DISCRETION);
            var renamed =
                trustAccountService.updateTrustAccount(
                    ids[0],
                    new UpdateTrustAccountRequest(
                        "Basis Held Renamed " + status, null, null, null, null, null, null, null));
            assertThat(renamed.accountName()).isEqualTo("Basis Held Renamed " + status);
          });
    }
  }

  @Test
  void updateTrustAccount_allowsABasisChange_whenItsInvestmentsAreWithdrawnOrCancelled() {
    UUID[] ids = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  ids[0] =
                      trustAccountService
                          .createTrustAccount(
                              investmentRequest("Basis Released", InvestmentBasis.FIRM_DISCRETION))
                          .id();
                  ids[1] = fundingAccount("Basis Released Funding");
                }));
    investmentDirect(ids[1], ids[0], "WITHDRAWN");
    investmentDirect(ids[1], ids[0], "CANCELLED");

    runInTenant(
        () -> {
          var changed =
              trustAccountService.updateTrustAccount(
                  ids[0], basisUpdate(InvestmentBasis.CLIENT_INSTRUCTION));
          assertThat(changed.investmentBasis()).isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
        });
  }

  // --- 648A review F-4: an account is not closed while an investment is held through it ---

  @Test
  void closeTrustAccount_isRefused_whileItHoldsOrFundsAnActiveOrMaturedInvestment() {
    for (var status : List.of("ACTIVE", "MATURED")) {
      UUID[] ids = new UUID[2];
      runInTenant(
          () ->
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    ids[0] =
                        trustAccountService
                            .createTrustAccount(
                                investmentRequest(
                                    "Close Held " + status, InvestmentBasis.FIRM_DISCRETION))
                            .id();
                    ids[1] = fundingAccount("Close Held Funding " + status);
                  }));
      investmentDirect(ids[1], ids[0], status);

      // The investment account holding it, and the banking account it was placed from
      for (var accountId : ids) {
        runInTenant(
            () ->
                assertThatThrownBy(() -> trustAccountService.closeTrustAccount(accountId))
                    .as("close with a %s investment", status)
                    .isInstanceOfSatisfying(
                        InvalidStateException.class,
                        e -> {
                          assertThat(e.getBody().getTitle())
                              .isEqualTo("Cannot close trust account");
                          assertThat(e.getBody().getDetail())
                              .isEqualTo(
                                  "An investment placed from or held in this account has not been"
                                      + " withdrawn. Withdraw it before closing the account.");
                        }));
        runInTenant(
            () ->
                assertThat(trustAccountRepository.findById(accountId).orElseThrow().getStatus())
                    .isEqualTo(TrustAccountStatus.ACTIVE));
      }
    }
  }

  @Test
  void closeTrustAccount_isAllowed_whenItsInvestmentsAreWithdrawnOrCancelled() {
    UUID[] ids = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  ids[0] =
                      trustAccountService
                          .createTrustAccount(
                              investmentRequest("Close Released", InvestmentBasis.FIRM_DISCRETION))
                          .id();
                  ids[1] = fundingAccount("Close Released Funding");
                }));
    investmentDirect(ids[1], ids[0], "WITHDRAWN");
    investmentDirect(ids[1], ids[0], "CANCELLED");

    for (var accountId : ids) {
      runInTenant(
          () ->
              assertThat(trustAccountService.closeTrustAccount(accountId).status())
                  .isEqualTo(TrustAccountStatus.CLOSED));
    }
  }

  // --- 650D: lockedThrough (§16.3.10, ADR-382) ---

  @Test
  void lockedThrough_isTheLatestLockedPeriodEnd_ofEachAccount_onGetAndList() {
    UUID[] ids = new UUID[3];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  ids[0] = fundingAccount("Locked Through A");
                  ids[1] = fundingAccount("Locked Through B");
                  ids[2] = fundingAccount("Locked Through C");
                }));
    // Out of order, so the latest period end (not the last insert) is what is asserted
    lockedListDirect(ids[0], LocalDate.of(2026, 1, 31));
    lockedListDirect(ids[0], LocalDate.of(2026, 3, 31));
    lockedListDirect(ids[0], LocalDate.of(2026, 2, 28));
    lockedListDirect(ids[1], LocalDate.of(2026, 2, 28));

    runInTenant(
        () -> {
          assertThat(trustAccountService.getTrustAccount(ids[0]).lockedThrough())
              .isEqualTo(LocalDate.of(2026, 3, 31));
          assertThat(trustAccountService.getTrustAccount(ids[1]).lockedThrough())
              .as("per account: A's lists never set B's lockedThrough")
              .isEqualTo(LocalDate.of(2026, 2, 28));
          assertThat(trustAccountService.getTrustAccount(ids[2]).lockedThrough()).isNull();

          assertThat(trustAccountService.listTrustAccounts())
              .extracting(TrustAccountResponse::id, TrustAccountResponse::lockedThrough)
              .contains(
                  tuple(ids[0], LocalDate.of(2026, 3, 31)),
                  tuple(ids[1], LocalDate.of(2026, 2, 28)),
                  tuple(ids[2], null));
        });
  }

  @Test
  void lockedThrough_isNullUntilAListIsLocked_andEveryAccountResponseCarriesIt() {
    UUID[] id = new UUID[1];
    runInTenant(
        () -> {
          var created =
              trustAccountService.createTrustAccount(
                  new CreateTrustAccountRequest(
                      "Locked Through D",
                      "Absa",
                      "632005",
                      "5550008888",
                      "GENERAL",
                      false,
                      false,
                      null,
                      LocalDate.of(2026, 3, 1),
                      null));
          assertThat(created.lockedThrough()).as("create response").isNull();
          id[0] = created.id();
          assertThat(trustAccountService.getTrustAccount(id[0]).lockedThrough()).isNull();
        });

    lockedListDirect(id[0], LocalDate.of(2026, 4, 30));

    runInTenant(
        () -> {
          assertThat(
                  trustAccountService
                      .updateTrustAccount(
                          id[0],
                          new UpdateTrustAccountRequest(
                              "Locked Through D renamed", null, null, null, null, null, null, null))
                      .lockedThrough())
              .as("update response")
              .isEqualTo(LocalDate.of(2026, 4, 30));
          assertThat(trustAccountService.getTrustAccount(id[0]).lockedThrough())
              .isEqualTo(LocalDate.of(2026, 4, 30));
          assertThat(trustAccountService.closeTrustAccount(id[0]).lockedThrough())
              .as("close response")
              .isEqualTo(LocalDate.of(2026, 4, 30));
        });
  }

  // --- Helpers ---

  private static UpdateTrustAccountRequest basisUpdate(InvestmentBasis basis) {
    return new UpdateTrustAccountRequest(null, null, null, null, null, null, null, basis);
  }

  /** A non-primary GENERAL account (the funding account of a directly written investment). */
  private UUID fundingAccount(String name) {
    return trustAccountService
        .createTrustAccount(
            new CreateTrustAccountRequest(
                name,
                "Absa",
                "632005",
                "5550008888",
                "GENERAL",
                false,
                false,
                null,
                LocalDate.of(2026, 3, 1),
                null))
        .id();
  }

  /**
   * A section 86(3) investment of {@code status} held in {@code investmentAccountId}, written
   * straight to the table (no posting — the basis check reads only the investment's status).
   */
  private void investmentDirect(UUID fundingAccountId, UUID investmentAccountId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_investments (trust_account_id, investment_account_id, institution,
            account_number, principal, interest_rate, deposit_date, status, investment_basis)
        VALUES (?, ?, 'Investec', 'F2-HELD', 1000.00, 0.0500, DATE '2026-03-01', ?,
            'FIRM_DISCRETION')
        """
            .formatted(tenantSchema),
        fundingAccountId,
        investmentAccountId,
        status);
  }

  /**
   * A locked Rule 54.15 list of {@code accountId} for {@code periodEnd} (a month end), with its
   * COMPLETED reconciliation, written straight to the tables (650D: lockedThrough reads only
   * trust_monthly_lists.period_end; the lock path itself is TrustMonthlyListIntegrationTest's).
   */
  private void lockedListDirect(UUID accountId, LocalDate periodEnd) {
    UUID reconciliationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO "%s".trust_reconciliations (trust_account_id, period_end, bank_balance,
                cashbook_balance, client_ledger_total, adjusted_bank_balance, is_balanced, status,
                completed_by, completed_at)
            VALUES (?, ?, 0, 0, 0, 0, true, 'COMPLETED', ?, now())
            RETURNING id
            """
                .formatted(tenantSchema),
            UUID.class,
            accountId,
            periodEnd,
            memberId);
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_monthly_lists (trust_account_id, period_end, reconciliation_id,
            bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance,
            cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance,
            unidentified_deposit_total, inter_account_balance, creditor_total, difference, lines,
            locked_by, locked_at)
        VALUES (?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '[]'::jsonb, ?, now())
        """
            .formatted(tenantSchema),
        accountId,
        periodEnd,
        reconciliationId,
        memberId);
  }

  private static CreateTrustAccountRequest investmentRequest(String name, InvestmentBasis basis) {
    return new CreateTrustAccountRequest(
        name,
        "Investec",
        "580105",
        "7770001234",
        "INVESTMENT",
        false,
        false,
        null,
        LocalDate.of(2026, 3, 1),
        null,
        basis);
  }

  private AuditEvent createdAudit(UUID accountId) {
    var events =
        auditEventRepository
            .findByFilter(
                "trust_account",
                accountId,
                null,
                "trust_account.created",
                null,
                null,
                PageRequest.of(0, 10))
            .getContent();
    assertThat(events).hasSize(1);
    return events.getFirst();
  }

  private long accountCount() {
    long[] count = new long[1];
    runInTenant(() -> count[0] = trustAccountRepository.count());
    return count[0];
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
