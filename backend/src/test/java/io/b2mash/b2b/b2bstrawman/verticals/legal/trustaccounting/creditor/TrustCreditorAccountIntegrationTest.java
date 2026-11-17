package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 646B (task 646.13, ADR-376, architecture §16.2.2 / §16.2.4 / §16.7.4): the
 * non-client trust creditor ledger.
 *
 * <p>Part A — a provisioned tenant over HTTP: {@code createTrustAccount} opens the Fund
 * interest-and-charges ledger and the unidentified-deposits ledger, and {@code GET
 * /api/trust-accounts/{id}/creditor-accounts} returns their derived balances as at a date (a Fund
 * ledger in debit is flagged, never refused).
 *
 * <p>Part B — a scratch tenant schema migrated to V17 (pinned, T17), seeded with open and closed
 * trust accounts and posted / pending / reversed client rows, then migrated to V18: both ledgers
 * are opened for every account, no existing row changes, the constraints are swapped, a re-run is a
 * no-op, and {@code chk_trust_txn_ledger_target} admits exactly the four ledger shapes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustCreditorAccountIntegrationTest {

  private static final String ORG_ID = "org_trust_creditor_accounts";
  private static final String OWNER_SUBJECT = "user_trust_creditor_owner";
  private static final String MEMBER_SUBJECT = "user_trust_creditor_member";

  /** A member on a custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST_SUBJECT = "user_trust_creditor_no_trust";

  /** Module off — its own tenant, never given {@code trust_accounting}. */
  private static final String NO_MODULE_ORG_ID = "org_trust_creditor_accounts_nomod";

  private static final String NO_MODULE_OWNER_SUBJECT = "user_trust_creditor_nomod_owner";

  private static final String FUND_NAME =
      "Legal Practitioners' Fidelity Fund — interest and charges";
  private static final String UNIDENTIFIED_NAME = "Unidentified deposits";

  // --- Part B constants ---

  /** The creditor-ledger migration (T17: pinned, not "latest"). */
  private static final String CREDITOR_VERSION = "18";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V18__phase92_trust_creditor_accounts.sql";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_creditor_scratch");

  /** Placeholders, longest first: a key that prefixes another must be replaced after it. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%clientA", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%clientB", "00000000-0000-0000-0000-0000000000a2");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%account2", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%account1", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%investment", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%payRevR", "00000000-0000-0000-0000-0000000000d4");
    IDS.put("%payRev", "00000000-0000-0000-0000-0000000000d3");
    IDS.put("%payPend", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%depClosed", "00000000-0000-0000-0000-0000000000d5");
    IDS.put("%dep", "00000000-0000-0000-0000-0000000000d1");
  }

  /** A quoted SQL literal in a constraint definition, e.g. {@code 'DEPOSIT'::character varying}. */
  private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;
  @Autowired private TrustBooksFixture fixture;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private String tenantSchema;
  private String ownerMemberId;
  private String fundLedgerScratchId;
  private List<String> fingerprintBefore;
  private TrustBooksFixture.Tenant books;

  @BeforeAll
  void setup() throws Exception {
    // Part A — a provisioned tenant with the module on
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Creditor Accounts Test Org", null)
            .schemaName();
    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            OWNER_SUBJECT,
            "trust_creditor_owner@test.com",
            "Trust Creditor Owner",
            "owner");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        MEMBER_SUBJECT,
        "trust_creditor_member@test.com",
        "Trust Creditor Member",
        "member");
    var noTrustMemberId =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            NO_TRUST_SUBJECT,
            "trust_creditor_no_trust@test.com",
            "Trust Creditor No Trust",
            "member");
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
    assignRole(noTrustMemberId, createCustomRole("Trust Creditor No Trust Role", "VIEW_LEGAL"));
    books = fixture.tenant(tenantSchema, ORG_ID, UUID.fromString(ownerMemberId));

    // A second tenant with the module off (precedent: TrustPositionIntegrationTest)
    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Creditor No Module Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER_SUBJECT,
        "trust_creditor_nomod@test.com",
        "Trust Creditor NoMod Owner",
        "owner");

    // Part B — scratch schema: V17, seed, fingerprint, V18
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("17")).load().migrate();
    seedV17Shapes();
    fingerprintBefore = fingerprint();
    tenantFlyway().target(MigrationVersion.fromVersion(CREDITOR_VERSION)).load().migrate();
    fundLedgerScratchId =
        text(
            "SELECT id FROM \"%s\".trust_creditor_accounts WHERE trust_account_id = '%account1'"
                + " AND account_type = 'FUND_INTEREST_AND_CHARGES'");
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  // ============ Part A — provisioned tenant, HTTP ============

  @Test
  void createTrustAccount_opensBothCreditorAccounts() throws Exception {
    var accountId = createTrustAccount("Creditor Ledgers A1", "62000000101");

    mockMvc
        .perform(get(creditorAccountsUrl(accountId)).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].accountType").value("FUND_INTEREST_AND_CHARGES"))
        .andExpect(jsonPath("$[0].name").value(FUND_NAME))
        .andExpect(jsonPath("$[1].accountType").value("UNIDENTIFIED_DEPOSITS"))
        .andExpect(jsonPath("$[1].name").value(UNIDENTIFIED_NAME))
        .andExpect(jsonPath("$[0].trustAccountId").value(accountId))
        .andExpect(jsonPath("$[1].trustAccountId").value(accountId))
        .andExpect(jsonPath("$[0].inDebit").value(false))
        .andExpect(jsonPath("$[1].inDebit").value(false))
        .andExpect(jsonPath("$[0].asAt").value(LocalDate.now(clock).toString()))
        .andExpect(jsonPath("$[1].asAt").value(LocalDate.now(clock).toString()))
        .andExpect(jsonPath("$[0].openItems").doesNotExist())
        .andExpect(jsonPath("$[1].openItems").doesNotExist());

    var body = getBody(creditorAccountsUrl(accountId));
    assertThat(balance(body, 0)).isEqualByComparingTo("0");
    assertThat(balance(body, 1)).isEqualByComparingTo("0");
  }

  @Test
  void balance_isDerivedAsAtTheDate_andAFundLedgerInDebitIsFlagged() throws Exception {
    var accountId = createTrustAccount("Creditor Ledgers A2", "62000000102");
    var fundLedgerId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM \"%s\".trust_creditor_accounts WHERE trust_account_id = ?::uuid AND account_type = 'FUND_INTEREST_AND_CHARGES'"
                .formatted(tenantSchema),
            UUID.class,
            accountId);
    // A posted NON_CLIENT sweep on 2026-03-10 (tests may write rows directly; ArchUnit
    // TrustPostingRulesTest checks main classes only — 646B has no posting path)
    jdbcTemplate.update(
        """
        INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type, amount,
            reference, transaction_date, status, recorded_by, ledger, creditor_account_id,
            cashbook_effect, ledger_effect, posted_at)
        VALUES (?::uuid, 'FUND_SWEEP', 100.00, 'REF-SWEEP', DATE '2026-03-10', 'RECORDED',
                ?::uuid, 'NON_CLIENT', ?, -100.00, -100.00, now())
        """
            .formatted(tenantSchema),
        accountId,
        ownerMemberId,
        fundLedgerId);

    var dayBefore = getBody(creditorAccountsUrl(accountId) + "?asAt=2026-03-09");
    assertThat(balance(dayBefore, 0)).isEqualByComparingTo("0");
    assertThat((Boolean) JsonPath.read(dayBefore, "$[0].inDebit")).isFalse();
    assertThat((String) JsonPath.read(dayBefore, "$[0].asAt")).isEqualTo("2026-03-09");

    var onTheDay = getBody(creditorAccountsUrl(accountId) + "?asAt=2026-03-10");
    assertThat((String) JsonPath.read(onTheDay, "$[0].accountType"))
        .isEqualTo("FUND_INTEREST_AND_CHARGES");
    assertThat(balance(onTheDay, 0)).isEqualByComparingTo("-100.00");
    assertThat((Boolean) JsonPath.read(onTheDay, "$[0].inDebit")).isTrue();
    assertThat((String) JsonPath.read(onTheDay, "$[0].asAt")).isEqualTo("2026-03-10");
    assertThat(balance(onTheDay, 1)).as("unidentified deposits").isEqualByComparingTo("0");
    assertThat((Boolean) JsonPath.read(onTheDay, "$[1].inDebit")).isFalse();
  }

  @Test
  void unknownAccount_is404() throws Exception {
    var unknown = UUID.randomUUID();
    mockMvc
        .perform(get(creditorAccountsUrl(unknown.toString())).with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustAccount not found"))
        .andExpect(jsonPath("$.detail").value("No trustaccount found with id " + unknown));
  }

  @Test
  void aMemberWithViewTrust_canRead() throws Exception {
    var accountId = createTrustAccount("Creditor Ledgers A4", "62000000104");
    mockMvc
        .perform(
            get(creditorAccountsUrl(accountId))
                .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER_SUBJECT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void aCallerWithoutViewTrust_isForbidden() throws Exception {
    var accountId = createTrustAccount("Creditor Ledgers A5", "62000000105");
    mockMvc
        .perform(
            get(creditorAccountsUrl(accountId))
                .with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void moduleOff_isForbidden_beforeTheAccountIsLookedUp() throws Exception {
    // An unknown id: the module guard answers 403 before the 404 existence check runs.
    mockMvc
        .perform(
            get(creditorAccountsUrl(UUID.randomUUID().toString()))
                .with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
  }

  // ============ Part A — the Fund ledger's rows (647C, §16.4.2) ============

  /**
   * The exit-month worked set on a fresh account, recorded from one statement through the real
   * write path ({@code POST /api/bank-statement-lines/{id}/record}): interest +1 234,56 on d1; a
   * recoverable charge 115,00 (VAT 15,00) and then a non-recoverable charge 57,50 (VAT 7,50) on d2;
   * a sweep 1 119,56 on d3. Running balances in posting order: 1 234,56 → 1 119,56 → 1 062,06 →
   * −57,50.
   */
  private record FundSet(String accountId, UUID fundId, UUID sweepLineId) {}

  private FundSet recordWorkedSet(String name, String accountNumber) throws Exception {
    var accountId = createTrustAccount(name, accountNumber);
    var account = UUID.fromString(accountId);
    var d1 = LocalDate.now(clock).minusDays(10);
    var d2 = d1.plusDays(2);
    var d3 = d1.plusDays(4);
    var interest = books.unmatchedLine(account, d1, "1234.56", "INT-" + accountNumber);
    var chargeRecoverable = books.addLine(interest.statementId(), 2, d2, "-115.00", null);
    var chargeNot = books.addLine(interest.statementId(), 3, d2, "-57.50", null);
    var sweep = books.addLine(interest.statementId(), 4, d3, "-1119.56", "LPFF-" + accountNumber);

    recordLine(interest.lineId(), "{\"classification\":\"FUND_INTEREST\"}");
    recordLine(
        chargeRecoverable,
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":15.00}");
    recordLine(
        chargeNot,
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":false,\"chargeVatAmount\":7.50}");
    recordLine(sweep, "{\"classification\":\"FUND_SWEEP\"}");
    return new FundSet(
        accountId, books.creditorAccount(account, "FUND_INTEREST_AND_CHARGES"), sweep);
  }

  @Test
  void fundLedgerRows_newestFirst_inPostingOrder() throws Exception {
    var set = recordWorkedSet("Creditor Ledgers R1", "62000000201");

    var body = getBody(fundRowsUrl(set.fundId()));
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].transactionType"))
        .containsExactly("FUND_SWEEP", "BANK_CHARGE", "BANK_CHARGE", "FUND_INTEREST");
    assertThat(amounts(body, "amount")).containsExactly("1119.56", "57.50", "115.00", "1234.56");
    assertThat(JsonPath.<String>read(body, "$.content[0].typeLabel"))
        .isEqualTo("Interest paid to the Legal Practitioners' Fidelity Fund");
    assertThat(JsonPath.<String>read(body, "$.content[3].typeLabel"))
        .isEqualTo("Interest received for the Legal Practitioners' Fidelity Fund");
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].direction"))
        .containsExactly("OUT", "OUT", "OUT", "IN");
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].ledger")).containsOnly("NON_CLIENT");
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].creditorAccountId"))
        .containsOnly(set.fundId().toString());
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].trustAccountId"))
        .containsOnly(set.accountId());
    assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(body, "$.page.number")).isZero();

    // The other creditor ledger of the same account has no rows
    var unidentified =
        books.creditorAccount(UUID.fromString(set.accountId()), "UNIDENTIFIED_DEPOSITS");
    var empty = getBody(fundRowsUrl(unidentified));
    assertThat(JsonPath.<List<Object>>read(empty, "$.content")).isEmpty();
    assertThat(JsonPath.<Integer>read(empty, "$.page.totalElements")).isZero();
  }

  @Test
  void runningBalance_isTheWindowOverPostedRows_beforePaging_andEndsAtTheLedgerBalance()
      throws Exception {
    var set = recordWorkedSet("Creditor Ledgers R2", "62000000202");

    var body = getBody(fundRowsUrl(set.fundId()));
    assertThat(amounts(body, "runningBalance"))
        .containsExactly("-57.50", "1062.06", "1119.56", "1234.56");

    // The window runs before the page is cut: page 1 of size 2 keeps the older rows' balances
    var page1 = getBody(fundRowsUrl(set.fundId()) + "?page=1&size=2");
    assertThat(JsonPath.<List<String>>read(page1, "$.content[*].transactionType"))
        .containsExactly("BANK_CHARGE", "FUND_INTEREST");
    assertThat(amounts(page1, "runningBalance")).containsExactly("1119.56", "1234.56");
    assertThat(JsonPath.<Integer>read(page1, "$.page.totalPages")).isEqualTo(2);

    // The newest row's balance is the ledger balance (same row set: posted_at IS NOT NULL)
    var accounts = getBody(creditorAccountsUrl(set.accountId()));
    assertThat(balance(accounts, 0)).isEqualByComparingTo("-57.50");
    assertThat((Boolean) JsonPath.read(accounts, "$[0].inDebit")).isTrue();
    assertThat(new BigDecimal(amounts(body, "runningBalance").getFirst()))
        .isEqualByComparingTo(balance(accounts, 0));

    // Unmatch the sweep line: a same-date REVERSAL contra; the original is REVERSED and keeps its
    // own effect and balance (every posted row counts), the newest balance is the ledger balance.
    mockMvc
        .perform(
            post("/api/bank-statement-lines/{lineId}/unmatch", set.sweepLineId()).with(owner()))
        .andExpect(status().isOk());

    var after = getBody(fundRowsUrl(set.fundId()));
    assertThat(JsonPath.<List<String>>read(after, "$.content[*].transactionType"))
        .containsExactly("REVERSAL", "FUND_SWEEP", "BANK_CHARGE", "BANK_CHARGE", "FUND_INTEREST");
    assertThat(JsonPath.<List<String>>read(after, "$.content[*].status"))
        .containsExactly("RECORDED", "REVERSED", "RECORDED", "RECORDED", "RECORDED");
    assertThat(JsonPath.<String>read(after, "$.content[0].direction")).isEqualTo("IN");
    assertThat(amounts(after, "runningBalance"))
        .containsExactly("1062.06", "-57.50", "1062.06", "1119.56", "1234.56");
    var accountsAfter = getBody(creditorAccountsUrl(set.accountId()));
    assertThat(balance(accountsAfter, 0)).isEqualByComparingTo("1062.06");
    assertThat(new BigDecimal(amounts(after, "runningBalance").getFirst()))
        .isEqualByComparingTo(balance(accountsAfter, 0));
  }

  @Test
  void bankCharges_showRecoverableAndVat_otherRowsCarryNone() throws Exception {
    var set = recordWorkedSet("Creditor Ledgers R3", "62000000203");
    mockMvc
        .perform(
            post("/api/bank-statement-lines/{lineId}/unmatch", set.sweepLineId()).with(owner()))
        .andExpect(status().isOk());

    // Newest first: REVERSAL, FUND_SWEEP (reversed), charge 57.50, charge 115.00, FUND_INTEREST
    mockMvc
        .perform(get(fundRowsUrl(set.fundId())).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.content[0].chargeRecoverable").isEmpty())
        .andExpect(jsonPath("$.content[0].chargeVatAmount").isEmpty())
        .andExpect(jsonPath("$.content[1].chargeRecoverable").isEmpty())
        .andExpect(jsonPath("$.content[1].chargeVatAmount").isEmpty())
        .andExpect(jsonPath("$.content[2].chargeRecoverable").value(false))
        .andExpect(jsonPath("$.content[2].chargeVatAmount").value(7.50))
        .andExpect(jsonPath("$.content[3].chargeRecoverable").value(true))
        .andExpect(jsonPath("$.content[3].chargeVatAmount").value(15.00))
        .andExpect(jsonPath("$.content[4].chargeRecoverable").isEmpty())
        .andExpect(jsonPath("$.content[4].chargeVatAmount").isEmpty());
  }

  @Test
  void fundLedgerRows_unknownCreditor404_viewTrustRequired_moduleGuardFirst() throws Exception {
    var unknown = UUID.randomUUID();
    mockMvc
        .perform(get(fundRowsUrl(unknown)).with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("TrustCreditorAccount not found"));

    var accountId = createTrustAccount("Creditor Ledgers R4", "62000000204");
    var fundId = books.creditorAccount(UUID.fromString(accountId), "FUND_INTEREST_AND_CHARGES");
    mockMvc
        .perform(get(fundRowsUrl(fundId)).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER_SUBJECT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
    mockMvc
        .perform(get(fundRowsUrl(fundId)).with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"));
    mockMvc
        .perform(
            get(fundRowsUrl(unknown))
                .with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"));
  }

  // ============ Part B — scratch schema, V17 → V18 ============

  @Test
  void migration_opensBothLedgersForEveryTrustAccount_openAndClosed() throws Exception {
    for (var account : List.of("%account1", "%account2")) {
      assertThat(
              texts(
                  "SELECT account_type || ':' || name FROM \"%s\".trust_creditor_accounts"
                      + " WHERE trust_account_id = '"
                      + account
                      + "' ORDER BY account_type"))
          .as("creditor accounts of %s", account)
          .containsExactly(
              "FUND_INTEREST_AND_CHARGES:" + FUND_NAME,
              "UNIDENTIFIED_DEPOSITS:" + UNIDENTIFIED_NAME);
    }
    assertThat(text("SELECT status FROM \"%s\".trust_accounts WHERE id = '%account2'"))
        .isEqualTo("CLOSED");
    assertThat(count("SELECT count(*) FROM \"%s\".trust_creditor_accounts")).isEqualTo(4);
  }

  @Test
  void migration_leavesEveryExistingRowUnchanged() throws Exception {
    assertThat(fingerprintBefore).hasSize(5);
    assertThat(fingerprint()).isEqualTo(fingerprintBefore);
    assertThat(
            count(
                "SELECT count(*) FROM \"%s\".trust_transactions"
                    + " WHERE creditor_account_id IS NOT NULL OR investment_id IS NOT NULL"))
        .isZero();
  }

  @Test
  void constraints_areSwapped() throws Exception {
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid ="
                    + " c.connamespace WHERE c.conname = 'chk_trust_txn_customer_required'"
                    + " AND n.nspname = '%s'"))
        .isZero();
    assertThat(constraintDef("chk_trust_txn_ledger_target"))
        .contains("NON_CLIENT")
        .contains("INTER_ACCOUNT")
        .contains("creditor_account_id");

    var constrained = new LinkedHashSet<String>();
    var matcher = QUOTED.matcher(constraintDef("chk_trust_txn_type"));
    while (matcher.find()) {
      constrained.add(matcher.group(1));
    }
    assertThat(constrained)
        .hasSize(25)
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(TrustTransactionType.values()).map(Enum::name).toList());

    // account_type carries no CHECK on purpose (ADR-376): only the PK and the FK
    assertThat(
            texts(
                "SELECT c.conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                    + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                    + " WHERE t.relname = 'trust_creditor_accounts' AND n.nspname = '%s'"
                    + " ORDER BY c.conname"))
        .containsExactly(
            "trust_creditor_accounts_pkey", "trust_creditor_accounts_trust_account_id_fkey");

    assertThat(
            texts(
                "SELECT indexname FROM pg_indexes WHERE schemaname = '%s' AND indexname IN"
                    + " ('uq_trust_creditor_account_singleton', 'idx_trust_txn_creditor_account',"
                    + " 'idx_trust_txn_investment') ORDER BY indexname"))
        .containsExactly(
            "idx_trust_txn_creditor_account",
            "idx_trust_txn_investment",
            "uq_trust_creditor_account_singleton");
    assertThat(constraintDef("trust_transactions_creditor_account_id_fkey"))
        .contains("REFERENCES")
        .contains("trust_creditor_accounts(id)");
    assertThat(constraintDef("trust_transactions_investment_id_fkey"))
        .contains("REFERENCES")
        .contains("trust_investments(id)");
  }

  @Test
  void runningTheMigrationBodyTwice_isANoOp() throws Exception {
    var creditorAccountsBefore = creditorAccounts();
    var before = fingerprint();
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
    assertThat(creditorAccounts()).isEqualTo(creditorAccountsBefore);
    assertThat(fingerprint()).isEqualTo(before);
  }

  @Test
  void aSecondFundLedgerOnOneAccount_isRefused() throws Exception {
    var refusal =
        tryInRolledBackTransaction(
            "INSERT INTO \"%s\".trust_creditor_accounts (trust_account_id, account_type, name)"
                + " VALUES ('%account1', 'FUND_INTEREST_AND_CHARGES', 'A second Fund ledger')");
    assertThat((Throwable) refusal).isNotNull();
    assertThat(refusal.getSQLState()).isEqualTo("23505");
    assertThat(refusal.getMessage()).contains("uq_trust_creditor_account_singleton");
  }

  /**
   * {@code chk_trust_txn_ledger_target} (§16.2.2): each ledger's valid shape is admitted and each
   * forbidden combination is refused. Rows are unposted (so {@code chk_trust_txn_posting} holds)
   * and of a type of the right ledger (so {@code chk_trust_txn_type} holds); each insert is rolled
   * back.
   */
  @ParameterizedTest(name = "{0}")
  @CsvSource({
    // case,                                         ledger,            type,           cust,  proj,
    //  cred,  inv,   ok
    "1 CLIENT with client and matter,                CLIENT,            DEPOSIT,        true,  true,  false, false, true",
    "2 CLIENT with an investment (optional),         CLIENT,            INV_PLACE,      true,  true,  false, true,  true",
    "3 CLIENT_INVESTMENT with client and investment, CLIENT_INVESTMENT, INV_INTEREST,   true,  true,  false, true,  true",
    "4 NON_CLIENT on the Fund ledger,                NON_CLIENT,        FUND_INTEREST,  false, false, true,  false, true",
    "5 NON_CLIENT with an investment (optional),     NON_CLIENT,        FUND_INV_SHARE, false, false, true,  true,  true",
    "6 INTER_ACCOUNT with an investment,             INTER_ACCOUNT,     FIRM_INV_PLACE, false, false, false, true,  true",
    "7 CLIENT with no client (never neither),        CLIENT,            DEPOSIT,        false, false, false, false, false",
    "8 CLIENT with a creditor account (never both),  CLIENT,            DEPOSIT,        true,  false, true,  false, false",
    "9 CLIENT_INVESTMENT with no investment,         CLIENT_INVESTMENT, INV_INTEREST,   true,  false, false, false, false",
    "10 CLIENT_INVESTMENT with a creditor account,   CLIENT_INVESTMENT, INV_INTEREST,   true,  false, true,  true,  false",
    "11 NON_CLIENT with no creditor account,         NON_CLIENT,        FUND_INTEREST,  false, false, false, false, false",
    "12 NON_CLIENT with a client,                    NON_CLIENT,        FUND_INTEREST,  true,  false, true,  false, false",
    "13 NON_CLIENT with a matter,                    NON_CLIENT,        FUND_INTEREST,  false, true,  true,  false, false",
    "14 INTER_ACCOUNT with no investment,            INTER_ACCOUNT,     FIRM_INV_PLACE, false, false, false, false, false",
    "15 INTER_ACCOUNT with a client,                 INTER_ACCOUNT,     FIRM_INV_PLACE, true,  false, false, true,  false",
    "16 INTER_ACCOUNT with a creditor account,       INTER_ACCOUNT,     FIRM_INV_PLACE, false, false, true,  true,  false"
  })
  void ledgerTarget_matrix(
      String name,
      String ledger,
      String type,
      boolean customer,
      boolean project,
      boolean creditor,
      boolean investment,
      boolean admitted)
      throws Exception {
    var sql =
        """
        INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, transaction_date, status, recorded_by, ledger,
            creditor_account_id, investment_id)
        VALUES ('%account1', '<type>', 10.00, <customer>, <project>, 'REF-MATRIX',
                DATE '2025-03-10', 'AWAITING_APPROVAL', '%owner', '<ledger>', <creditor>,
                <investment>)
        """
            .replace("<type>", type)
            .replace("<ledger>", ledger)
            .replace("<customer>", customer ? "'%clientA'" : "NULL")
            .replace("<project>", project ? "'%matter'" : "NULL")
            .replace("<creditor>", creditor ? "'" + fundLedgerScratchId + "'" : "NULL")
            .replace("<investment>", investment ? "'%investment'" : "NULL");

    var refusal = tryInRolledBackTransaction(sql);

    if (admitted) {
      assertThat((Throwable) refusal).as("%s is admitted", name).isNull();
    } else {
      assertThat((Throwable) refusal).as("%s is refused", name).isNotNull();
      assertThat(refusal.getSQLState()).as("%s SQLState", name).isEqualTo("23514");
      assertThat(refusal.getMessage()).contains("chk_trust_txn_ledger_target");
    }
  }

  // ============ helpers — Part A ============

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  private static String creditorAccountsUrl(String accountId) {
    return "/api/trust-accounts/" + accountId + "/creditor-accounts";
  }

  private static String fundRowsUrl(UUID creditorAccountId) {
    return "/api/trust-creditor-accounts/" + creditorAccountId + "/transactions";
  }

  private void recordLine(UUID lineId, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/bank-statement-lines/{lineId}/record", lineId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  /** A numeric field of every page row, as plain strings (JSON numbers may arrive as Double). */
  private static List<String> amounts(String body, String field) {
    List<Object> values = JsonPath.read(body, "$.content[*]." + field);
    return values.stream()
        .map(v -> new BigDecimal(String.valueOf(v)).setScale(2).toPlainString())
        .toList();
  }

  private String createTrustAccount(String name, String accountNumber) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "%s",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """
                            .formatted(name, accountNumber)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private String getBody(String url) throws Exception {
    return mockMvc
        .perform(get(url).with(owner()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static BigDecimal balance(String body, int index) {
    Object value = JsonPath.read(body, "$[" + index + "].balance");
    return new BigDecimal(String.valueOf(value));
  }

  // ============ helpers — Part B ============

  /** The V17 shapes, with raw SQL and explicit effect columns (what 649B leaves). */
  private void seedV17Shapes() throws Exception {
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tca', 'tca@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%clientA', 'Client A', 'a@tca.test', 'INDIVIDUAL', 'ACTIVE', '%owner'),
               ('%clientB', 'Client B', 'b@tca.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, closed_date)
        VALUES ('%account1', 'Trust 1', 'FNB', '250655', '62000000021', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01', NULL),
               ('%account2', 'Trust 2', 'FNB', '250655', '62000000022', 'GENERAL', false, false,
                'CLOSED', DATE '2025-01-01', DATE '2025-06-30');
        """);

    // Account 1 — a posted deposit, a pending payment, a reversed payment and its contra (V17)
    txn(
        "%dep",
        "%account1", "DEPOSIT", "5000.00", "%clientA", "RECORDED", "5000", true, "2025-03-01");
    txn(
        "%payPend",
        "%account1",
        "PAYMENT",
        "700.00",
        "%clientA",
        "AWAITING_APPROVAL",
        "0",
        false,
        "2025-03-02");
    txn(
        "%payRev",
        "%account1", "PAYMENT", "1000.00", "%clientA", "REVERSED", "-1000", true, "2025-03-03");
    txn(
        "%payRevR",
        "%account1", "REVERSAL", "1000.00", "%clientA", "RECORDED", "1000", true, "2025-03-04");
    exec(
        "UPDATE \"%s\".trust_transactions SET reversal_of = '%payRev' WHERE id = '%payRevR';"
            + " UPDATE \"%s\".trust_transactions SET reversed_by_id = '%payRevR' WHERE id = '%payRev'");
    // Account 2 (closed) — a posted deposit
    txn(
        "%depClosed",
        "%account2", "DEPOSIT", "200.00", "%clientB", "RECORDED", "200", true, "2025-02-01");

    // An investment for the ledger-target matrix (deposit_transaction_id is NOT NULL)
    exec(
        """
        INSERT INTO "%s".trust_investments (id, trust_account_id, customer_id, institution,
            account_number, principal, interest_rate, deposit_date, deposit_transaction_id)
        VALUES ('%investment', '%account1', '%clientA', 'FNB', 'INV-1', 1000.00, 0.0500,
                DATE '2025-03-01', '%dep')
        """);
  }

  /** One V17-shape client-ledger row; a posted row's cashbook effect equals its ledger effect. */
  private void txn(
      String id,
      String account,
      String type,
      String amount,
      String customer,
      String status,
      String effect,
      boolean posted,
      String date)
      throws Exception {
    exec(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
                                             customer_id, project_id, reference,
                                             transaction_date, status, recorded_by, created_at,
                                             ledger, cashbook_effect, ledger_effect, posted_at)
        VALUES ('%id', '%acc', '%type', %amount, '%customer', '%matter', 'REF-%type',
                DATE '%date', '%status', '%owner', TIMESTAMPTZ '%date 10:00:00+02',
                'CLIENT', %effect, %effect, %postedAt)
        """
            .replace("%id", id)
            .replace("%acc", account)
            .replace("%type", type)
            .replace("%amount", amount)
            .replace("%customer", customer)
            .replace("%status", status)
            .replace("%effect", effect)
            .replace("%postedAt", posted ? "TIMESTAMPTZ '%date 10:00:00+02'" : "NULL")
            .replace("%date", date));
  }

  /**
   * Type, amount, status, ledger, effects and links (client and matter included — the columns
   * {@code chk_trust_txn_ledger_target} reasons about) of every row — what V18 (and a re-run) must
   * not touch.
   */
  private List<String> fingerprint() throws Exception {
    return texts(
        "SELECT id || ':' || transaction_type || ':' || amount || ':' || status || ':' || ledger"
            + " || ':' || cashbook_effect || ':' || ledger_effect || ':'"
            + " || COALESCE(posted_at::text, 'NULL') || ':'"
            + " || COALESCE(reversal_of::text, 'NULL') || ':'"
            + " || COALESCE(reversed_by_id::text, 'NULL') || ':'"
            + " || COALESCE(customer_id::text, 'NULL') || ':'"
            + " || COALESCE(project_id::text, 'NULL')"
            + " FROM \"%s\".trust_transactions ORDER BY id");
  }

  private List<String> creditorAccounts() throws Exception {
    return texts(
        "SELECT id || ':' || trust_account_id || ':' || account_type || ':' || name"
            + " FROM \"%s\".trust_creditor_accounts ORDER BY id");
  }

  /**
   * Runs {@code sql} in a transaction that is always rolled back; returns the refusal, or null when
   * the statement was admitted.
   */
  private SQLException tryInRolledBackTransaction(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        s.execute(resolve(sql));
        return null;
      } catch (SQLException e) {
        return e;
      } finally {
        c.rollback();
        c.setAutoCommit(true);
      }
    }
  }

  private String constraintDef(String name) throws Exception {
    return text(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n"
            + " ON n.oid = c.connamespace WHERE c.conname = '"
            + name
            + "' AND n.nspname = '%s'");
  }

  private long count(String sql) throws Exception {
    return Long.parseLong(text(sql));
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

  /** Replaces the id placeholders (longest first), then {@code %s} with the scratch schema. */
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
