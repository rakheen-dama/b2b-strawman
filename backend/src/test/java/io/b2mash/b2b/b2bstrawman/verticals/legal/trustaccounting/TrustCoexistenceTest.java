/*
 * Trust Accounting Coexistence Tests (Epic 451B; re-fixtured Phase 86 / Epic 604)
 *
 * These tests verify trust accounting module isolation between a legal tenant and a
 * non-trust tenant. Since Phase 86 legal-za is the only vertical profile, so the
 * non-trust tenant is provisioned with no profile and no enabled modules. Kept as a
 * guard: trust must never leak into a tenant that has not enabled it.
 *
 * 1. NON-TRUST TENANT ISOLATION: A tenant without trust_accounting cannot access trust
 *    endpoints — the module guard blocks all trust service calls.
 *
 * 2. LEGAL TENANT ACCESS: A legal-za tenant with trust_accounting enabled can
 *    create and list trust accounts successfully.
 *
 * 3. SCHEMA COEXISTENCE: Trust tables exist in all tenant schemas (via Flyway)
 *    but remain empty for tenants that never use them.
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
  private static final String NON_TRUST_ORG_ID = "org_trust_coex_nontrust";

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
  private String nonTrustSchema;
  private UUID legalMemberId;
  private UUID nonTrustMemberId;

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

    // --- Provision non-trust tenant (no profile) ---
    nonTrustSchema =
        provisioningService
            .provisionTenant(NON_TRUST_ORG_ID, "Trust Coex Non-Trust Firm", null)
            .schemaName();
    nonTrustMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                NON_TRUST_ORG_ID,
                "user_trust_coex_nontrust",
                "trust_coex_nontrust@test.com",
                "Trust Coex Non-Trust Owner",
                "owner"));

    // Pin enabled modules to empty (no trust_accounting)
    ScopedValue.where(RequestScopes.TENANT_ID, nonTrustSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of());
                      orgSettingsRepository.save(settings);
                    }));
  }

  // ===== Test 1: Non-trust tenant cannot access trust endpoints =====

  @Test
  void nonTrustTenantCannotAccessTrustService() {
    runInNonTrustTenant(
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

  // ===== Test 3: Trust tables exist in both schemas but empty for non-trust tenant =====

  @Test
  void trustTablesExistInBothSchemasButEmptyForNonTrustTenant() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      // Verify all 10 trust tables exist in BOTH schemas
      for (String schema : List.of(legalSchema, nonTrustSchema)) {
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

      // Verify all trust tables are empty in the non-trust schema.
      // NOTE: Schema and table identifiers cannot be parameterized in SQL prepared
      // statements — only values can be bound as parameters. String formatting is
      // acceptable here because both nonTrustSchema (from TenantProvisioningService)
      // and tableName (from TRUST_TABLES constant) are controlled test constants.
      // We use Statement (not PreparedStatement) to make this intent explicit.
      for (String tableName : TRUST_TABLES) {
        assertThat(nonTrustSchema).as("schema name must be safe identifier").matches("[a-z0-9_]+");
        assertThat(tableName).as("table name must be safe identifier").matches("[a-z0-9_]+");
        try (var stmt = conn.createStatement()) {
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT COUNT(*) FROM \"%s\".\"%s\"".formatted(nonTrustSchema, tableName));
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1))
              .as("Table %s should be empty in non-trust schema %s", tableName, nonTrustSchema)
              .isEqualTo(0);
        }
      }
    }
  }

  // ===== Test 4: Trust capability on one tenant doesn't affect another =====

  @Test
  void trustCapabilityDoesNotAffectNonTrustTenant() {
    // Confirm legal tenant has trust_accounting enabled
    runInLegalTenant(() -> assertThat(moduleGuard.isModuleEnabled("trust_accounting")).isTrue());

    // Confirm non-trust tenant still rejects trust_accounting
    runInNonTrustTenant(
        () -> assertThat(moduleGuard.isModuleEnabled("trust_accounting")).isFalse());
  }

  // ===== Test 5: Module guard prevents cross-module access =====

  @Test
  void moduleGuardPreventsAccessInNonTrustAllowsInLegal() {
    // Non-trust tenant: requireModule should throw
    runInNonTrustTenant(
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

  private void runInNonTrustTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, nonTrustSchema)
        .where(RequestScopes.ORG_ID, NON_TRUST_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nonTrustMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
