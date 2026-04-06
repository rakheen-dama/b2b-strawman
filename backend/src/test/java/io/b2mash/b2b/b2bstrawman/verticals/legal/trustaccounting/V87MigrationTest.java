package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V87MigrationTest {
  private static final String ORG_ID = "org_v87_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private String memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V87 Migration Test Org", null);
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_v87_owner", "v87_owner@test.com", "V87 Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void v87AddsNewColumnsToExistingTables() throws Exception {
    List<String[]> expectedColumns =
        List.of(
            new String[] {"trust_investments", "investment_basis"},
            new String[] {"checklist_instance_items", "verification_provider"},
            new String[] {"checklist_instance_items", "verification_reference"},
            new String[] {"checklist_instance_items", "verification_status"},
            new String[] {"checklist_instance_items", "verified_at"},
            new String[] {"checklist_instance_items", "verification_metadata"},
            new String[] {"interest_allocations", "lpff_rate_id"},
            new String[] {"interest_allocations", "statutory_rate_applied"});

    try (Connection conn = dataSource.getConnection()) {
      for (String[] tableCol : expectedColumns) {
        String tableName = tableCol[0];
        String columnName = tableCol[1];
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, tableName);
          ps.setString(3, columnName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next())
              .as("ResultSet should have a row for %s.%s", tableName, columnName)
              .isTrue();
          assertThat(rs.getInt(1))
              .as("Column %s.%s should exist in schema %s", tableName, columnName, tenantSchema)
              .isEqualTo(1);
        }
      }
    }
  }

  @Test
  void v87InvestmentBasisCheckConstraintRejectsInvalidValues() {
    // Insert prerequisite data: customer, trust account, trust transaction
    String customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO "
                + tenantSchema
                + ".customers "
                + "(id, name, email, customer_type, lifecycle_status, created_by, "
                + " created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'Test Client', 'v87test@test.com', "
                + "        'INDIVIDUAL', 'ACTIVE', ?::uuid, now(), now()) "
                + "RETURNING id::text",
            String.class,
            memberId);

    String trustAccountId =
        jdbcTemplate.queryForObject(
            "INSERT INTO "
                + tenantSchema
                + ".trust_accounts "
                + "(id, account_name, bank_name, branch_code, account_number, "
                + " account_type, is_primary, require_dual_approval, status, opened_date, "
                + " created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'Test Trust Account', 'FNB', '250655', "
                + "        '62123456789', 'GENERAL', true, false, 'ACTIVE', CURRENT_DATE, "
                + "        now(), now()) "
                + "RETURNING id::text",
            String.class);

    String txnId =
        jdbcTemplate.queryForObject(
            "INSERT INTO "
                + tenantSchema
                + ".trust_transactions "
                + "(id, trust_account_id, customer_id, transaction_type, amount, "
                + " reference, description, transaction_date, status, recorded_by, created_at) "
                + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'DEPOSIT', 10000.00, "
                + "        'REF001', 'Test deposit', CURRENT_DATE, 'RECORDED', ?::uuid, now()) "
                + "RETURNING id::text",
            String.class,
            trustAccountId,
            customerId,
            memberId);

    // Attempt to insert a trust_investment with invalid investment_basis
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO "
                        + tenantSchema
                        + ".trust_investments "
                        + "(id, trust_account_id, customer_id, institution, account_number, "
                        + " principal, interest_rate, deposit_date, deposit_transaction_id, "
                        + " investment_basis, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, "
                        + "        'Test Bank', '1234567', 10000.00, 0.05, CURRENT_DATE, "
                        + "        ?::uuid, 'INVALID', now(), now())",
                    trustAccountId,
                    customerId,
                    txnId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
