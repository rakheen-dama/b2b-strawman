package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountingMigrationTest {

  private static final String API_KEY = "test-api-key";
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
    syncMember(ORG_ID, "user_v85_owner", "v85_owner@test.com", "V85 Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void v85CreatesAllTenTables() throws Exception {
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

  @Test
  void v85ClientLedgerCardBalanceConstraintPreventsNegative() throws Exception {
    // Insert a valid trust account using schema-qualified table name
    jdbcTemplate.update(
        "INSERT INTO "
            + tenantSchema
            + ".trust_accounts "
            + "(id, account_name, bank_name, branch_code, account_number, "
            + " account_type, is_primary, require_dual_approval, status, opened_date, "
            + " created_at, updated_at) "
            + "VALUES (gen_random_uuid(), 'Test Trust Account', 'FNB', '250655', "
            + "        '62123456789', 'GENERAL', true, false, 'ACTIVE', ?, now(), now())",
        LocalDate.now());

    // Attempt to insert a client_ledger_card with negative balance — should violate CHECK
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO "
                        + tenantSchema
                        + ".client_ledger_cards "
                        + "(id, trust_account_id, customer_id, balance, total_deposits, "
                        + " total_payments, total_fee_transfers, total_interest_credited, "
                        + " created_at, updated_at) "
                        + "SELECT gen_random_uuid(), id, gen_random_uuid(), -1, 0, 0, 0, 0, now(), now() "
                        + "FROM "
                        + tenantSchema
                        + ".trust_accounts LIMIT 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "%s",
                      "email": "%s",
                      "name": "%s",
                      "avatarUrl": null,
                      "orgRole": "%s"
                    }
                    """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }
}
