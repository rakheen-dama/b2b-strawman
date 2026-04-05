/*
 * Trust Accounting Coexistence Tests (Epic 451B)
 *
 * These tests verify trust accounting module isolation between legal and
 * accounting tenants:
 *
 * 1. ACCOUNTING TENANT ISOLATION: An accounting-za tenant cannot access trust
 *    endpoints — the module guard blocks all trust service calls.
 *
 * 2. LEGAL TENANT ACCESS: A legal-za tenant with trust_accounting enabled can
 *    create and list trust accounts successfully.
 *
 * 3. SCHEMA COEXISTENCE: Trust tables exist in all tenant schemas (via Flyway)
 *    but remain empty for accounting tenants that never use them.
 *
 * 4. CROSS-TENANT PROTECTION: Enabling trust_accounting on one tenant does not
 *    affect the module guard behavior of another tenant.
 *
 * 5. MODULE GUARD CORRECTNESS: Direct requireModule() calls throw/succeed
 *    based on the current tenant's enabled modules.
 */
package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustCoexistenceTest {
  private static final String LEGAL_ORG_ID = "org_trust_coex_legal";
  private static final String ACCOUNTING_ORG_ID = "org_trust_coex_acct";

  private static final List<String> TRUST_TABLES =
      List.of(
          "trust_accounts",
          "lpff_rates",
          "trust_transactions",
          "client_ledger_cards",
          "bank_statements",
          "bank_statement_lines",
          "trust_reconciliations",
          "interest_runs",
          "interest_allocations",
          "trust_investments");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DataSource dataSource;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private VerticalModuleGuard moduleGuard;

  private String legalSchema;
  private String accountingSchema;
  private UUID legalMemberId;
  private UUID accountingMemberId;

  @BeforeAll
  void setup() throws Exception {
    // --- Provision legal tenant ---
    legalSchema =
        provisioningService
            .provisionTenant(LEGAL_ORG_ID, "Trust Coex Legal Firm", "legal-za")
            .schemaName();
    legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_trust_coex_legal",
                "trust_coex_legal@test.com",
                "Trust Coex Legal Owner",
                "owner"));

    // Explicitly enable trust_accounting for the legal tenant
    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(
                          List.of(
                              "court_calendar",
                              "conflict_check",
                              "lssa_tariff",
                              "trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // --- Provision accounting tenant ---
    accountingSchema =
        provisioningService
            .provisionTenant(ACCOUNTING_ORG_ID, "Trust Coex Accounting Firm", "accounting-za")
            .schemaName();
    accountingMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ACCOUNTING_ORG_ID,
                "user_trust_coex_acct",
                "trust_coex_acct@test.com",
                "Trust Coex Accounting Owner",
                "owner"));

    // Enable accounting modules (no trust_accounting)
    ScopedValue.where(RequestScopes.TENANT_ID, accountingSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("regulatory_deadlines"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  // ===== Test 1: Accounting tenant cannot access trust endpoints =====

  @Test
  void accountingTenantCannotAccessTrustService() {
    runInAccountingTenant(
        () ->
            assertThatThrownBy(() -> trustAccountService.listTrustAccounts())
                .isInstanceOf(ModuleNotEnabledException.class));
  }

  // ===== Test 2: Legal tenant with trust_accounting can access trust endpoints =====

  @Test
  void legalTenantCanAccessTrustService() {
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      trustAccountService.createTrustAccount(
                          new TrustAccountService.CreateTrustAccountRequest(
                              "Coex Test Trust Account",
                              "First National Bank",
                              "250655",
                              "62000000001",
                              "GENERAL",
                              true,
                              false,
                              new BigDecimal("50000.00"),
                              LocalDate.of(2024, 1, 15),
                              "Coexistence test trust account"));

                  assertThat(response).isNotNull();
                  assertThat(response.accountName()).isEqualTo("Coex Test Trust Account");
                  assertThat(response.bankName()).isEqualTo("First National Bank");

                  // Verify listing also works
                  var accounts = trustAccountService.listTrustAccounts();
                  assertThat(accounts).isNotEmpty();
                  assertThat(accounts)
                      .anyMatch(a -> a.accountName().equals("Coex Test Trust Account"));
                }));
  }

  // ===== Test 3: Trust tables exist in both schemas but empty for accounting =====

  @Test
  void trustTablesExistInBothSchemasButEmptyForAccounting() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      // Verify all 10 trust tables exist in BOTH schemas
      for (String schema : List.of(legalSchema, accountingSchema)) {
        for (String tableName : TRUST_TABLES) {
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "SELECT COUNT(*) FROM information_schema.tables "
                      + "WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                .as("Table %s should exist in schema %s", tableName, schema)
                .isEqualTo(1);
          }
        }
      }

      // Verify all trust tables are empty in the accounting schema
      for (String tableName : TRUST_TABLES) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM \"%s\".\"%s\"".formatted(accountingSchema, tableName))) {
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1))
              .as("Table %s should be empty in accounting schema %s", tableName, accountingSchema)
              .isEqualTo(0);
        }
      }
    }
  }

  // ===== Test 4: Trust capability on one tenant doesn't affect accounting =====

  @Test
  void trustCapabilityDoesNotAffectAccountingTenant() {
    // Confirm legal tenant has trust_accounting enabled
    runInLegalTenant(() -> assertThat(moduleGuard.isModuleEnabled("trust_accounting")).isTrue());

    // Confirm accounting tenant still rejects trust_accounting
    runInAccountingTenant(
        () -> assertThat(moduleGuard.isModuleEnabled("trust_accounting")).isFalse());
  }

  // ===== Test 5: Module guard prevents cross-module access =====

  @Test
  void moduleGuardPreventsAccessInAccountingAllowsInLegal() {
    // Accounting tenant: requireModule should throw
    runInAccountingTenant(
        () ->
            assertThatThrownBy(() -> moduleGuard.requireModule("trust_accounting"))
                .isInstanceOf(ModuleNotEnabledException.class));

    // Legal tenant: requireModule should succeed
    runInLegalTenant(
        () ->
            assertThatCode(() -> moduleGuard.requireModule("trust_accounting"))
                .doesNotThrowAnyException());
  }

  // ===== Helpers =====

  private void runInLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private void runInAccountingTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, accountingSchema)
        .where(RequestScopes.ORG_ID, ACCOUNTING_ORG_ID)
        .where(RequestScopes.MEMBER_ID, accountingMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
