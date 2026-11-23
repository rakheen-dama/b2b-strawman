package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 93 / 653A (task 653.3, architecture §17.7.2): tenant {@code V32__phase93_take_on_core}
 * (guide V30/V31; next free at build time). Constraint cases run on a demo-seeded ({@code
 * legal-za}) tenant; the processing-activity insert runs on scratch schemas walked to V31 with
 * Phase 92 trust data, one ZA and one with no jurisdiction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnCoreSchemaTest {

  private static final String ORG_ID = "org_takeon_core_schema_test";
  private static final String MIGRATION_FILE = "db/migration/tenant/V32__phase93_take_on_core.sql";
  private static final String PREVIOUS_VERSION = "31";
  private static final String ACTIVITY = "Client and Trust Take-On";

  // Never provisioned or mapped — only name the scratch schemas.
  private static final String SCRATCH_ZA =
      SchemaNameGenerator.generateSchemaName("org_takeon_core_scratch_za");
  private static final String SCRATCH_NONE =
      SchemaNameGenerator.generateSchemaName("org_takeon_core_scratch_none");

  private static final String OWNER = "00000000-0000-0000-0000-000000000001";
  private static final String CLIENT = "00000000-0000-0000-0000-0000000000c1";
  private static final String MATTER = "00000000-0000-0000-0000-0000000000a1";
  private static final String ACCOUNT = "00000000-0000-0000-0000-0000000000e1";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private String schema;
  private UUID memberId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Take-On Schema Test Org", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_takeon_schema_owner",
                "takeon_schema_owner@test.com",
                "Take-On Schema Owner",
                "owner"));
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    trustAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES (?, 'Take-on schema trust', 'FNB', '250655', '62099990001', 'GENERAL', false,
                false, 'ACTIVE', DATE '2025-01-01')
        """
            .formatted(schema),
        trustAccountId);

    walkScratchSchema(SCRATCH_ZA, "'ZA'");
    walkScratchSchema(SCRATCH_NONE, "NULL");
  }

  @AfterEach
  void clearTakeOnRows() {
    for (String table :
        List.of("take_on_sign_offs", "take_on_rows", "take_on_sources", "take_on_batches")) {
      jdbcTemplate.update("DELETE FROM \"%s\".%s".formatted(schema, table));
    }
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + SCRATCH_ZA + "\" CASCADE");
    exec("DROP SCHEMA IF EXISTS \"" + SCRATCH_NONE + "\" CASCADE");
  }

  // (a)
  @Test
  void fourTablesExistWithTheirKeyColumns() {
    String[][] expected = {
      {"take_on_batches", "dataset"}, {"take_on_batches", "status"},
      {"take_on_batches", "cutover_date"}, {"take_on_batches", "trust_account_id"},
      {"take_on_batches", "validation_hash"}, {"take_on_batches", "summary"},
      {"take_on_batches", "mid_month_acknowledged"}, {"take_on_sources", "batch_id"},
      {"take_on_sources", "section"}, {"take_on_sources", "file_sha256"},
      {"take_on_sources", "sheet_names"}, {"take_on_sources", "column_mapping"},
      {"take_on_rows", "raw_values"}, {"take_on_rows", "mapped_values"},
      {"take_on_rows", "dedup_key"}, {"take_on_rows", "block"},
      {"take_on_sign_offs", "validation_hash"}, {"take_on_sign_offs", "revoked_reason"}
    };
    for (String[] column : expected) {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns"
                  + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
              Integer.class,
              schema,
              column[0],
              column[1]);
      assertThat(count).as("%s.%s", column[0], column[1]).isEqualTo(1);
    }
  }

  // (b)
  @Test
  void datasetAndStatusChecksRejectUnknownValues() {
    assertRejected(
        () -> insertBatch("PAYROLL", "UPLOADED", null, null), "chk_take_on_batch_dataset");
    assertRejected(() -> insertBatch("CLIENTS", "FAILED", null, null), "chk_take_on_batch_status");
  }

  // (c)
  @Test
  void trustAccountIsRequiredExactlyForATrustPosition() {
    assertRejected(
        () -> insertBatch("TRUST_POSITION", "UPLOADED", null, null),
        "chk_take_on_batch_trust_account");
    assertRejected(
        () -> insertBatch("CLIENTS", "UPLOADED", trustAccountId, null),
        "chk_take_on_batch_trust_account");
    assertThat(insertBatch("TRUST_POSITION", "UPLOADED", trustAccountId, null)).isNotNull();
  }

  // (d)
  @Test
  void debtorBatchCannotBeValidatedWithoutACutoverDate() {
    assertRejected(
        () -> insertBatch("DEBTOR_BALANCES", "VALIDATED", null, null), "chk_take_on_batch_cutover");
    assertThat(insertBatch("DEBTOR_BALANCES", "MAPPED", null, null)).isNotNull();
    assertThat(insertBatch("DEBTOR_BALANCES", "VALIDATED", null, LocalDate.of(2026, 10, 31)))
        .isNotNull();
  }

  // (e)
  @Test
  void oneCommittingBatchPerTenant_inAnyDataset() {
    String indexDef =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            String.class,
            schema,
            "uq_take_on_batches_committing");
    assertThat(indexDef)
        .contains("UNIQUE")
        .contains("((true))")
        .contains("WHERE ((status)::text = 'COMMITTING'::text)");

    insertBatch("CLIENTS", "COMMITTING", null, null);
    assertRejected(
        () -> insertBatch("MATTERS", "COMMITTING", null, null), "uq_take_on_batches_committing");
  }

  // (f)
  @Test
  void oneCommittedTrustPositionPerAccount() {
    LocalDate cutover = LocalDate.of(2026, 10, 31);
    insertBatch("TRUST_POSITION", "COMPLETED", trustAccountId, cutover);
    assertThat(insertBatch("TRUST_POSITION", "CANCELLED", trustAccountId, cutover)).isNotNull();
    assertRejected(
        () -> insertBatch("TRUST_POSITION", "COMPLETED", trustAccountId, cutover),
        "uq_take_on_batches_trust_position");
  }

  // (g)
  @Test
  void createdDedupKeyIsUniqueAcrossBatches_andBlockOnlyOnErrorRows() {
    UUID batchA = insertBatch("CLIENTS", "COMPLETED", null, null);
    UUID batchB = insertBatch("CLIENTS", "VALIDATED", null, null);
    UUID sourceA = insertSource(batchA);
    UUID sourceB = insertSource(batchB);

    insertRow(batchA, sourceA, 2, "CREATED", "email:a@example.test", false);
    assertRejected(
        () -> insertRow(batchB, sourceB, 2, "CREATED", "email:a@example.test", false),
        "uq_take_on_rows_created_key");
    assertThat(insertRow(batchB, sourceB, 3, "VALIDATED", "email:a@example.test", false))
        .isNotNull();
    assertRejected(
        () -> insertRow(batchB, sourceB, 4, "VALIDATED", null, true), "chk_take_on_row_block");
    assertThat(insertRow(batchB, sourceB, 5, "ERROR", null, true)).isNotNull();
    assertRejected(
        () -> insertRow(batchB, sourceB, 5, "PENDING", null, false), "uq_take_on_rows_source_row");
  }

  // (h)
  @Test
  void oneLiveSignOffPerKind_andTheMemberMustExist() {
    UUID batch = insertBatch("CLIENTS", "VALIDATED", null, null);
    UUID first = insertSignOff(batch, memberId);
    assertRejected(() -> insertSignOff(batch, memberId), "uq_take_on_sign_offs_live");

    assertRejected(
        () ->
            jdbcTemplate.update(
                "UPDATE \"%s\".take_on_sign_offs SET revoked_at = now() WHERE id = ?"
                    .formatted(schema),
                first),
        "chk_take_on_sign_off_revoked");
    jdbcTemplate.update(
        "UPDATE \"%s\".take_on_sign_offs SET revoked_at = now(), revoked_reason = 'REVALIDATED'"
                .formatted(schema)
            + " WHERE id = ?",
        first);
    assertThat(insertSignOff(batch, memberId)).isNotNull();

    UUID otherBatch = insertBatch("CLIENTS", "VALIDATED", null, null);
    assertRejected(
        () -> insertSignOff(otherBatch, UUID.randomUUID()), "take_on_sign_offs_member_fkey");
  }

  // (i)
  @Test
  void zaTenantWalkedFromPhase92_getsTheProcessingActivityOnce_evenOnARerun() throws Exception {
    assertThat(activityCount(SCRATCH_ZA)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM \""
                    + SCRATCH_ZA
                    + "\".trust_transactions"
                    + " WHERE transaction_type = 'DEPOSIT'"))
        .as("Phase 92 data survives")
        .isEqualTo(1);

    rerunMigrationBody(SCRATCH_ZA);
    assertThat(activityCount(SCRATCH_ZA)).isEqualTo(1);
  }

  // (j)
  @Test
  void tenantWithoutAJurisdiction_getsNoProcessingActivity() throws Exception {
    assertThat(activityCount(SCRATCH_NONE)).isZero();
    rerunMigrationBody(SCRATCH_NONE);
    assertThat(activityCount(SCRATCH_NONE)).isZero();
  }

  // --- helpers: demo-seeded tenant ---

  private UUID insertBatch(String dataset, String status, UUID account, LocalDate cutover) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".take_on_batches
          (id, dataset, status, trust_account_id, cutover_date, created_by, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, now(), now())
        """
            .formatted(schema),
        id,
        dataset,
        status,
        account,
        cutover,
        memberId);
    return id;
  }

  private UUID insertSource(UUID batchId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".take_on_sources
          (id, batch_id, section, file_name, content_type, file_format, file_size, file_sha256,
           parse_options, uploaded_by, uploaded_at)
        VALUES (?, ?, 'MAIN', 'clients.csv', 'text/csv', 'CSV', 120, ?, '{}'::jsonb, ?, now())
        """
            .formatted(schema),
        id,
        batchId,
        "f".repeat(64),
        memberId);
    return id;
  }

  private UUID insertRow(
      UUID batchId, UUID sourceId, int rowNumber, String status, String dedupKey, boolean block) {
    UUID id = UUID.randomUUID();
    boolean hasResult = "CREATED".equals(status);
    jdbcTemplate.update(
        """
        INSERT INTO "%s".take_on_rows
          (id, batch_id, source_id, dataset, section, row_number, raw_values, dedup_key, status,
           block, result_entity_type, result_entity_id, created_at, updated_at)
        VALUES (?, ?, ?, 'CLIENTS', 'MAIN', ?, '{"Name": "A"}'::jsonb, ?, ?, ?, ?, ?, now(), now())
        """
            .formatted(schema),
        id,
        batchId,
        sourceId,
        rowNumber,
        dedupKey,
        status,
        block,
        hasResult ? "CLIENT" : null,
        hasResult ? UUID.randomUUID() : null);
    return id;
  }

  private UUID insertSignOff(UUID batchId, UUID signer) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".take_on_sign_offs
          (id, batch_id, kind, member_id, member_name, role_name, statement_version,
           statement_text, validation_hash, signed_at)
        VALUES (?, ?, 'FICA_ATTESTATION', ?, 'Owner', 'Owner', 'FICA-ATTEST-v1', 'I attest.', ?,
                now())
        """
            .formatted(schema),
        id,
        batchId,
        signer,
        "a".repeat(64));
    return id;
  }

  private static void assertRejected(Runnable insert, String constraint) {
    assertThatThrownBy(insert::run)
        .as("rejected by %s", constraint)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  // --- helpers: scratch schemas walked from Phase 92 ---

  private void walkScratchSchema(String scratch, String jurisdiction) throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + scratch + "\" CASCADE");
    exec("CREATE SCHEMA \"" + scratch + "\"");
    tenantFlyway(scratch).target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_toc', 'toc@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".org_settings (id, data_protection_jurisdiction)
        VALUES (gen_random_uuid(), %j);
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('%c', 'Phase 92 client', 'p92@test.com', '%o');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%m', 'Phase 92 matter', '%o');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%a', 'Trust 1', 'FNB', '250655', '62000000093', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES (gen_random_uuid(), '%a', 'DEPOSIT', 1000.00, '%c', '%m', 'DEP-1',
                DATE '2026-09-30', 'RECORDED', '%o', 'CLIENT', 1000.00, 1000.00, now());
        """
            .replace("%s", scratch)
            .replace("%o", OWNER)
            .replace("%j", jurisdiction)
            .replace("%c", CLIENT)
            .replace("%m", MATTER)
            .replace("%a", ACCOUNT));
    tenantFlyway(scratch).load().migrate();
  }

  private int activityCount(String scratch) throws Exception {
    return count(
        "SELECT count(*) FROM \""
            + scratch
            + "\".processing_activities WHERE category = '"
            + ACTIVITY
            + "'");
  }

  private void rerunMigrationBody(String scratch) throws Exception {
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + scratch + "\"");
        s.execute(body);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  private int count(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getInt(1);
    }
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private FluentConfiguration tenantFlyway(String scratch) {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(scratch)
        .baselineOnMigrate(false);
  }
}
