package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 651A (task 651.5; architecture §16.3.14, §16.7.10, §16.9.4; ADR-375, ADR-383): the
 * calculated interest run is removed. On a provisioned tenant the run's three tables do not exist
 * (lpff_rates stays until 651E; idx_trust_txn_project is kept), the seven run routes are unmapped
 * (404 — not the 645A 400, not 403), and the engine package, its event and its notification type
 * are gone. A scratch schema migrated to V26 and seeded with a DRAFT and an APPROVED run
 * (allocations and matter lines) migrates to V27 cleanly, and the V27 body is a no-op when run
 * twice (§16.7.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestRunRemovalTest {

  private static final String ORG_ID = "org_interest_run_removal";
  private static final String OWNER = "user_irr_owner";

  private static final List<String> RUN_TABLES =
      List.of("interest_allocation_lines", "interest_allocations", "interest_runs");

  /** The drop migration (pinned, not "latest"). */
  private static final String DROP_VERSION = "27";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V27__phase92_drop_interest_run.sql";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_interest_run_removal_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%client", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%rate", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%runDraft", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%runApproved", "00000000-0000-0000-0000-0000000000f2");
    IDS.put("%allocDraft", "00000000-0000-0000-0000-0000000000f3");
    IDS.put("%allocApproved", "00000000-0000-0000-0000-0000000000f4");
    IDS.put("%lineDraft", "00000000-0000-0000-0000-0000000000f5");
    IDS.put("%lineApproved", "00000000-0000-0000-0000-0000000000f6");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final DataSource migrationDataSource;

  private String tenantSchema;
  private UUID trustAccountId;

  @Autowired
  InterestRunRemovalTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void setup() throws Exception {
    // --- provisioned tenant (migrated to latest, so V27 has run) ---
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Interest Run Removal Org", null).schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "irr_owner@test.com", "IRR Owner", "owner");
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
    // A real account: were the list route still mapped it would answer 200 here, so a 404
    // proves the route is gone rather than the account missing.
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Interest Run Removal Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000006510",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    // --- scratch schema: V26 with runs on it, then V27 (the walked-dev-schema case) ---
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("26")).load().migrate();
    seedRuns();
    tenantFlyway().target(MigrationVersion.fromVersion(DROP_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  // --- provisioned tenant ---

  @Test
  void theThreeRunTablesDoNotExist_lpffRatesAndTheMatterIndexStay() {
    for (String table : RUN_TABLES) {
      assertThat(regclass(tenantSchema + "." + table)).as(table).isNull();
    }
    assertThat(regclass(tenantSchema + ".lpff_rates")).as("lpff_rates (651E)").isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = ?"
                    + " AND tablename = 'trust_transactions' AND indexname = 'idx_trust_txn_project'",
                Integer.class,
                tenantSchema))
        .isEqualTo(1);
  }

  @Test
  void theRunRoutesAreUnmapped() throws Exception {
    // Were InterestController's seven mappings (§16.4.9). Unmapped → 404, not 400 / 403.
    UUID runId = UUID.randomUUID();
    String account = "/api/trust-accounts/" + trustAccountId + "/interest-runs";
    String run = "/api/interest-runs/" + runId;
    List<MockHttpServletRequestBuilder> requests =
        List.of(
            get(account),
            post(account)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"2025-03-01\",\"periodEnd\":\"2025-03-31\"}"),
            get(run),
            delete(run),
            post(run + "/calculate"),
            post(run + "/approve"),
            post(run + "/post"));
    for (var request : requests) {
      mockMvc
          .perform(request.with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not Found"))
          // "No static resource …" = no handler mapping matched — not a business 404 from a live
          // route
          .andExpect(jsonPath("$.detail").value(startsWith("No static resource ")));
    }
  }

  @Test
  void theEngineItsEventAndItsNotificationTypeAreGone() throws Exception {
    for (String name :
        List.of(
            "interest.InterestService",
            "interest.InterestController",
            "interest.InterestRun",
            "interest.InterestAllocation",
            "interest.InterestAllocationLine",
            "interest.InterestRunRepository",
            "interest.InterestAllocationRepository",
            "interest.InterestAllocationLineRepository")) {
      assertThatThrownBy(
              () ->
                  Class.forName(
                      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting." + name))
          .as(name)
          .isInstanceOf(ClassNotFoundException.class);
    }
    assertThat(
            Arrays.stream(TrustDomainEvent.class.getPermittedSubclasses())
                .map(Class::getSimpleName))
        .contains("ReconciliationCompleted") // non-vacuous
        .doesNotContain("InterestPosted");
    assertThat(NotificationService.NOTIFICATION_TYPES)
        .contains("TRUST_RECONCILIATION_COMPLETED")
        .doesNotContain("TRUST_INTEREST_POSTED");
    mockMvc
        .perform(get("/api/notifications/preferences").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.preferences[*].notificationType", hasItem("TRUST_RECONCILIATION_COMPLETED")))
        .andExpect(
            jsonPath("$.preferences[*].notificationType", not(hasItem("TRUST_INTEREST_POSTED"))));
  }

  // --- scratch schema (V26 with runs → V27) ---

  @Test
  void aSchemaHoldingRunsMigratesToV27_theRunTablesGo_theRateStays() throws Exception {
    for (String table : RUN_TABLES) {
      assertThat(text("SELECT COALESCE(to_regclass('\"%s\"." + table + "')::text, 'NULL')"))
          .as(table)
          .isEqualTo("NULL");
    }
    assertThat(text("SELECT count(*) FROM \"%s\".lpff_rates WHERE id = '%rate'")).isEqualTo("1");
    assertThat(
            text(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_trust_txn_project'"))
        .isEqualTo("1");
    assertThat(
            text(
                "SELECT success::text FROM \"%s\".flyway_schema_history WHERE version = '"
                    + DROP_VERSION
                    + "'"))
        .isEqualTo("true");
  }

  @Test
  void runningTheMigrationBodyTwice_isANoOp() throws Exception {
    var before = tables();
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + SCRATCH_SCHEMA + "\"");
        s.execute(body);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
    assertThat(tables()).isEqualTo(before).contains("lpff_rates", "trust_transactions");
  }

  // --- helpers ---

  /** A V26 schema with a DRAFT and an APPROVED run, each with an allocation and a matter line. */
  private void seedRuns() throws Exception {
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_irr', 'irr@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%client', 'Client A', 'a@irr.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%account', 'Trust', 'FNB', '250655', '62000000027', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01', NULL);
        INSERT INTO "%s".lpff_rates (id, trust_account_id, effective_from, rate_percent,
                                     lpff_share_percent)
        VALUES ('%rate', '%account', DATE '2025-01-01', 0.0500, 0.7500);
        INSERT INTO "%s".interest_runs (id, trust_account_id, period_start, period_end, lpff_rate_id,
                                        total_interest, total_lpff_share, total_client_share,
                                        status, approved_by, created_by)
        VALUES ('%runDraft', '%account', DATE '2025-03-01', DATE '2025-03-31', '%rate',
                17.34, 5.00, 12.34, 'DRAFT', NULL, '%owner'),
               ('%runApproved', '%account', DATE '2025-04-01', DATE '2025-04-30', '%rate',
                17.34, 5.00, 12.34, 'APPROVED', '%owner', '%owner');
        INSERT INTO "%s".interest_allocations (id, interest_run_id, customer_id,
                                               average_daily_balance, days_in_period,
                                               gross_interest, lpff_share, client_share)
        VALUES ('%allocDraft', '%runDraft', '%client', 6500.00, 31, 17.34, 5.00, 12.34),
               ('%allocApproved', '%runApproved', '%client', 6500.00, 30, 17.34, 5.00, 12.34);
        INSERT INTO "%s".interest_allocation_lines (id, interest_allocation_id, project_id,
                                                    balance_days, weight, client_share)
        VALUES ('%lineDraft', '%allocDraft', '%matter', 201500.00, 1, 12.34),
               ('%lineApproved', '%allocApproved', '%matter', 195000.00, 1, 12.34);
        """);
    // Non-vacuous: the runs really were there before V27.
    assertThat(text("SELECT count(*) FROM \"%s\".interest_allocation_lines")).isEqualTo("2");
  }

  /** The scratch schema's tables — what a second run of the body must leave untouched. */
  private List<String> tables() throws Exception {
    return texts(
        "SELECT table_name FROM information_schema.tables WHERE table_schema = '%s' ORDER BY 1");
  }

  private String regclass(String qualified) {
    return jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, qualified);
  }

  private String text(String sql) throws Exception {
    var rows = texts(sql);
    assertThat(rows).as("one row from %s", sql).hasSize(1);
    return rows.getFirst();
  }

  /** Every row of {@code sql}, its columns joined with {@code |} (NULL as "NULL"). */
  private List<String> texts(String sql) throws Exception {
    var rows = new ArrayList<String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(resolve(sql))) {
      int columns = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        var values = new ArrayList<String>();
        for (int i = 1; i <= columns; i++) {
          var value = rs.getString(i);
          values.add(value == null ? "NULL" : value);
        }
        rows.add(String.join("|", values));
      }
    }
    return rows;
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(resolve(sql));
    }
  }

  /** Replaces the id placeholders, then {@code %s} with the scratch schema. */
  private static String resolve(String sql) {
    var resolved = sql;
    for (var entry : IDS.entrySet()) {
      resolved = resolved.replace(entry.getKey(), entry.getValue());
    }
    return resolved.replace("%s", SCRATCH_SCHEMA);
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
