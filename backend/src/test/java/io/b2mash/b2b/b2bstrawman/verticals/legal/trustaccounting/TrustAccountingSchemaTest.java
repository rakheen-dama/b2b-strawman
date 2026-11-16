package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountingSchemaTest {
  private static final String ORG_ID = "org_v85_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V85 Migration Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_v85_owner", "v85_owner@test.com", "V85 Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createsAllTenTables() throws Exception {
    List<String> expectedTables =
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

    try (Connection conn = dataSource.getConnection()) {
      for (String tableName : expectedTables) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, tableName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).as("ResultSet should have a row for " + tableName).isTrue();
          assertThat(rs.getInt(1))
              .as("Table %s should exist in schema %s", tableName, tenantSchema)
              .isEqualTo(1);
        }
      }
    }
  }

  /**
   * Phase 92 / 646A (ADR-378): client balances are derived from the posted rows, so {@code
   * client_ledger_cards} is only the per-client lock anchor — the stored balance, the running
   * totals, the last-transaction date, their CHECKs and {@code idx_client_ledger_balance} are gone.
   * (Replaces {@code clientLedgerCardBalanceConstraintPreventsNegative}: the database no longer
   * refuses a negative client balance — the CHECK it pinned was dropped with the column.)
   */
  @Test
  void clientLedgerCardsHasOnlyTheLockAnchorColumns() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'client_ledger_cards'",
            String.class,
            tenantSchema);
    assertThat(columns)
        .containsExactlyInAnyOrder(
            "id", "trust_account_id", "customer_id", "created_at", "updated_at");

    Integer balanceIndexes =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = ?"
                + " AND indexname = 'idx_client_ledger_balance'",
            Integer.class,
            tenantSchema);
    assertThat(balanceIndexes).isZero();
  }
}
