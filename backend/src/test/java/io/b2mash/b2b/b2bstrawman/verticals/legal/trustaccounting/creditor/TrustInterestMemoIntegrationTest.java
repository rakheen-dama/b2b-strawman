package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemoService.SaveInterestMemoRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Phase 92 slice 650B (task 650.7, architecture §16.2.9, §16.3.13, §16.4.2, ADR-384): memo records
 * for at-source sweeps. A {@code PUT …/interest-memos/{yyyy-MM}} creates the month's memo or
 * corrects it in place (one row per account and month), each save audited with the previous figures
 * on an update; {@code GET …/interest-memos?year=} lists the year's memos oldest first. A memo
 * posts nothing: saving and correcting one — for an unlocked month and for a month whose Rule 54.15
 * list is locked — moves no term of the equation ({@code Expected.none()}), leaves the locked list
 * byte-identical and raises no integrity flag. Capability matrix and refusals.
 *
 * <p>One harness tenant, a fresh account per test; the class ends with {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInterestMemoIntegrationTest {

  private static final String ORG_ID = "org_trust_interest_memo";
  private static final String OWNER = "user_tim_owner";
  private static final String ADMIN = "user_tim_admin";

  /** The default "member" role: VIEW_TRUST, no MANAGE_TRUST. */
  private static final String MEMBER = "user_tim_member";

  /** A member on a custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST = "user_tim_no_trust";

  /** Module off — its own tenant, never given {@code trust_accounting}. */
  private static final String NO_MODULE_ORG_ID = "org_trust_interest_memo_nomod";

  private static final String NO_MODULE_OWNER = "user_tim_nomod_owner";

  private static final String ENTITY_TYPE = "trust_interest_memo";
  private static final String SAVED = "trust_interest_memo.saved";
  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  private static final String FEB_BODY =
      """
      {"grossInterest":1234.56,"grossCharges":115.00,"chargesVat":15.00,\
      "sourceNote":"FNB advice LPFF-FEB"}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private TrustInterestMemoRepository memoRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;
  @Autowired private TrustInterestMemoService memoService;
  @Autowired private DataSource dataSource;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID adminId;
  private int statementCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Interest Memo Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tim_owner@test.com", "TIM Owner", "owner"));
    adminId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, "tim_admin@test.com", "TIM Admin", "admin"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "tim_member@test.com", "TIM Member", "member");
    var noTrustId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "tim_no_trust@test.com", "TIM No Trust", "member");
    fixture.enableTrustModule(schema);
    assignRole(noTrustId, createCustomRole("TIM No Trust Role", "VIEW_LEGAL"));
    books = fixture.tenant(schema, ORG_ID, ownerId);

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Interest Memo NoMod Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER,
        "tim_nomod@test.com",
        "TIM NoMod Owner",
        "owner");
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- upsert and audit -------------------------------------------------------------------------

  @Test
  void save_createsTheMonthsMemo_aSecondSaveCorrectsTheSameRow_andEachSaveIsAudited()
      throws Exception {
    var account = books.freshAccount(false);
    var created =
        putMemo(account, "2026-02", FEB_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trustAccountId").value(account.toString()))
            .andExpect(jsonPath("$.periodMonth").value("2026-02-01"))
            .andExpect(jsonPath("$.grossInterest").value(1234.56))
            .andExpect(jsonPath("$.grossCharges").value(115.0))
            .andExpect(jsonPath("$.chargesVat").value(15.0))
            .andExpect(jsonPath("$.sourceNote").value("FNB advice LPFF-FEB"))
            .andExpect(jsonPath("$.recordedBy").value(ownerId.toString()))
            .andExpect(jsonPath("$.recordedByName").value("TIM Owner"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();
    var memoId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    String createdAt = JsonPath.read(created.getResponse().getContentAsString(), "$.createdAt");
    assertStorePrecision(created);

    var corrected =
        putMemo(
                account,
                "2026-02",
                """
            {"grossInterest":1300.00,"grossCharges":230.00,"chargesVat":30.00,\
            "sourceNote":"  FNB advice LPFF-FEB (corrected)  "}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(memoId.toString()))
            .andExpect(jsonPath("$.periodMonth").value("2026-02-01"))
            .andExpect(jsonPath("$.grossInterest").value(1300.0))
            .andExpect(jsonPath("$.grossCharges").value(230.0))
            .andExpect(jsonPath("$.chargesVat").value(30.0))
            .andExpect(jsonPath("$.sourceNote").value("FNB advice LPFF-FEB (corrected)"))
            .andExpect(jsonPath("$.createdAt").value(createdAt))
            .andReturn();
    assertStorePrecision(corrected);
    String correctedUpdatedAt =
        JsonPath.read(corrected.getResponse().getContentAsString(), "$.updatedAt");

    var rows = memosOf(account, 2026, 2, 2);
    assertThat(rows)
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getId()).isEqualTo(memoId);
              assertThat(m.getGrossInterest()).isEqualByComparingTo("1300.00");
              assertThat(m.getRecordedBy()).isEqualTo(ownerId);
              assertThat(m.getUpdatedAt()).isAfterOrEqualTo(m.getCreatedAt());
              // The responses returned exactly what is stored (microsecond timestamptz).
              assertThat(m.getCreatedAt()).isEqualTo(Instant.parse(createdAt));
              assertThat(m.getUpdatedAt()).isEqualTo(Instant.parse(correctedUpdatedAt));
            });

    var events = audits(memoId);
    assertThat(events).hasSize(2);
    var first = events.stream().filter(e -> !e.getDetails().containsKey("previous_gross_interest"));
    var update = events.stream().filter(e -> e.getDetails().containsKey("previous_gross_interest"));
    assertThat(first.toList())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getEntityType()).isEqualTo(ENTITY_TYPE);
              assertThat(e.getDetails())
                  .containsEntry("trust_account_id", account.toString())
                  .containsEntry("period_month", "2026-02")
                  .containsEntry("gross_interest", "1234.56")
                  .containsEntry("gross_charges", "115.00")
                  .containsEntry("charges_vat", "15.00")
                  .containsEntry("source_note", "FNB advice LPFF-FEB")
                  .doesNotContainKeys(
                      "previous_gross_charges", "previous_charges_vat", "previous_source_note");
            });
    assertThat(update.toList())
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.getDetails())
                    .containsEntry("gross_interest", "1300.00")
                    .containsEntry("gross_charges", "230.00")
                    .containsEntry("charges_vat", "30.00")
                    .containsEntry("source_note", "FNB advice LPFF-FEB (corrected)")
                    .containsEntry("previous_gross_interest", "1234.56")
                    .containsEntry("previous_gross_charges", "115.00")
                    .containsEntry("previous_charges_vat", "15.00")
                    .containsEntry("previous_source_note", "FNB advice LPFF-FEB"));
  }

  @Test
  void list_returnsTheYearsMemosOldestFirst_onlyForTheAccountAndYear() throws Exception {
    var account = books.freshAccount(false);
    var other = books.freshAccount(false);
    var march = memoId(putMemo(account, "2026-03", body("30.00", "10.00", "1.30", "March")));
    var january = memoId(putMemo(account, "2026-01", body("10.00", "0.00", "0.00", "January")));
    var december = memoId(putMemo(account, "2025-12", body("5.00", "2.00", "0.26", "December")));
    var otherFebruary =
        memoId(putMemo(other, "2026-02", body("99.00", "0.00", "0.00", "Other account")));

    mockMvc
        .perform(get(memosUrl(account) + "?year=2026").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(january.toString()))
        .andExpect(jsonPath("$[0].periodMonth").value("2026-01-01"))
        .andExpect(jsonPath("$[0].recordedByName").value("TIM Owner"))
        .andExpect(jsonPath("$[1].id").value(march.toString()))
        .andExpect(jsonPath("$[1].periodMonth").value("2026-03-01"));
    mockMvc
        .perform(get(memosUrl(account) + "?year=2025").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(december.toString()))
        .andExpect(jsonPath("$[0].periodMonth").value("2025-12-01"));
    mockMvc
        .perform(get(memosUrl(other) + "?year=2026").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(otherFebruary.toString()));

    // No year: the current year in South Africa
    var currentYear = LocalDate.now(clock).getYear();
    var noYear =
        mockMvc
            .perform(get(memosUrl(account)).with(owner()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(noYear, "$[*].id");
    List<String> months = JsonPath.read(noYear, "$[*].periodMonth");
    assertThat(months).allSatisfy(m -> assertThat(m).startsWith(currentYear + "-"));
    if (currentYear == 2026) {
      assertThat(ids).containsExactly(january.toString(), march.toString());
    } else {
      assertThat(ids).doesNotContain(december.toString(), otherFebruary.toString());
    }
  }

  // --- no effect on any term ---------------------------------------------------------------------

  /**
   * A memo posts nothing: saving and correcting it moves no term ({@code Expected.none()}), the
   * services still agree with the oracle, and the integrity report is unchanged.
   */
  @Test
  void noEffectOnAnyTerm_savingAndCorrectingAMemo_movesNothing() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("memo terms");
    books.deposit(account, client, "1000.00");
    books.postNonClientDirect(account, "FUND_INTEREST", "200.00", LocalDate.of(2026, 3, 5));
    var before = check(account);

    moves(
        account,
        Expected.none(),
        () ->
            putMemo(account, "2026-03", body("1234.56", "115.00", "15.00", "FNB advice MAR"))
                .andExpect(status().isOk()));
    moves(
        account,
        Expected.none(),
        () ->
            putMemo(account, "2026-03", body("1500.00", "230.00", "30.00", "FNB advice MAR v2"))
                .andExpect(status().isOk()));

    var after = check(account);
    assertThat(after.position()).isEqualTo(before.position());
    assertThat(after.balanced()).isEqualTo(before.balanced());
    assertThat(codes(after)).isEqualTo(codes(before));
    books.assertServicesAgree(account);
  }

  /**
   * ADR-384: a month whose Rule 54.15 list is locked takes a memo (create and correction) — nothing
   * moves, the locked list row is byte-identical, {@code lockedThrough} stays, the list gains no
   * entry made after the lock and no {@code BACKDATED_AFTER_LOCK} flag is raised.
   */
  @Test
  void aLockedMonth_takesAMemo_andTheLockedListDoesNotChange() throws Exception {
    var account = books.freshAccount(false);
    var march = matchedMarchStatement(account, "tim-locked-march.csv");
    var listId = complete(reconcile(account, MARCH_END, march.id()).id()).monthlyListId();
    assertThat(listId).as("completing to 31 March locks March").isNotNull();
    var listBefore = listFingerprint(listId);
    accountLockedThrough(account, "2026-03-31");

    moves(
        account,
        Expected.none(),
        () ->
            putMemo(account, "2026-03", body("80.00", "40.00", "5.22", "FNB advice locked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodMonth").value("2026-03-01")));
    moves(
        account,
        Expected.none(),
        () ->
            putMemo(account, "2026-03", body("85.00", "46.00", "6.00", "FNB advice locked v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossInterest").value(85.0)));

    assertThat(listFingerprint(listId)).isEqualTo(listBefore);
    accountLockedThrough(account, "2026-03-31");
    mockMvc
        .perform(get("/api/trust-monthly-lists/{id}", listId).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.adjustmentsSinceLock.entries.length()").value(0));
    assertThat(codes(check(account))).doesNotContain("BACKDATED_AFTER_LOCK");
    assertThat(memosOf(account, 2026, 3, 3)).hasSize(1);
  }

  // --- capabilities
  // -------------------------------------------------------------------------------

  @Test
  void capabilityMatrix() throws Exception {
    var account = books.freshAccount(false);
    var memoId = memoId(putMemo(account, "2026-01", body("10.00", "5.00", "0.65", "Owner")));
    mockMvc
        .perform(get(memosUrl(account) + "?year=2026").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    // An admin (MANAGE_TRUST) corrects it and becomes the last saver
    putMemo(account, "2026-01", body("11.00", "5.00", "0.65", "Admin"), admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(memoId.toString()))
        .andExpect(jsonPath("$.recordedBy").value(adminId.toString()))
        .andExpect(jsonPath("$.recordedByName").value("TIM Admin"));
    mockMvc
        .perform(get(memosUrl(account) + "?year=2026").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(memoId.toString()));

    // The default member role: VIEW_TRUST without MANAGE_TRUST
    var member = TestJwtFactory.memberJwt(ORG_ID, MEMBER);
    mockMvc
        .perform(get(memosUrl(account) + "?year=2026").with(member))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(memoId.toString()));
    forbidden(putMemo(account, "2026-01", body("99.00", "0.00", "0.00", "Member"), member));
    assertThat(memosOf(account, 2026, 1, 1))
        .singleElement()
        .satisfies(m -> assertThat(m.getGrossInterest()).isEqualByComparingTo("11.00"));

    // VIEW_LEGAL only
    var noTrust = TestJwtFactory.memberJwt(ORG_ID, NO_TRUST);
    forbidden(mockMvc.perform(get(memosUrl(account) + "?year=2026").with(noTrust)));
    forbidden(putMemo(account, "2026-01", body("99.00", "0.00", "0.00", "No trust"), noTrust));

    // Module off: the guard answers 403 before the 404 existence check runs
    var noModule = TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER);
    var randomAccount = UUID.randomUUID();
    moduleOff(mockMvc.perform(get(memosUrl(randomAccount)).with(noModule)));
    moduleOff(putMemo(randomAccount, "2026-01", body("1.00", "0.00", "0.00", "No mod"), noModule));

    // Unknown account
    var unknown = UUID.randomUUID();
    notFound(mockMvc.perform(get(memosUrl(unknown)).with(owner())), unknown);
    notFound(putMemo(unknown, "2026-01", body("1.00", "0.00", "0.00", "Unknown")), unknown);
  }

  // --- refusals ----------------------------------------------------------------------------------

  @Test
  void refusals_areProblemDetails_andSaveNothing() throws Exception {
    var account = books.freshAccount(false);

    validation(
        putMemo(account, "2026-01", body("-0.01", "0.00", "0.00", "Negative")), "grossInterest");
    validation(
        putMemo(account, "2026-01", body("1.00", "10.005", "0.00", "Three places")),
        "grossCharges");
    validation(putMemo(account, "2026-01", body("1.00", "0.00", "0.00", "   ")), "sourceNote");
    validation(
        putMemo(account, "2026-01", body("1.00", "0.00", "0.00", "x".repeat(501))), "sourceNote");
    validation(
        putMemo(
            account,
            "2026-01",
            "{\"grossCharges\":0.00,\"chargesVat\":0.00,\"sourceNote\":\"No interest\"}"),
        "grossInterest");
    refused(
        putMemo(account, "2026-01", body("1.00", "10.00", "20.00", "VAT above")),
        "Cannot save memo",
        "The VAT cannot be more than the gross charges it is included in.");
    var next = YearMonth.now(clock).plusMonths(1);
    refused(
        putMemo(account, next.toString(), body("1.00", "0.00", "0.00", "Future")),
        "Cannot save memo",
        "A memo can be saved for this month or an earlier one, not for "
            + next.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
            + ".");
    // Before January 1900: the list reads years from 1900, so such a memo could never be listed
    refused(
        putMemo(account, "1899-12", body("1.00", "0.00", "0.00", "Too early")),
        "Cannot save memo",
        "A memo can be saved for January 1900 or a later month, not for December 1899.");
    refused(
        putMemo(account, "2026-13", body("1.00", "0.00", "0.00", "Malformed")),
        "Invalid parameter value",
        "Parameter 'month' should be of type YearMonth");
    refused(
        mockMvc.perform(get(memosUrl(account) + "?year=1800").with(owner())),
        "Invalid year",
        "Choose a year from 1900 to 9999.");

    // Nothing persisted and nothing audited for a refusal
    assertThat(memosOf(account, 2026, 1, 12)).isEmpty();
    assertThat(memosOf(account, next.getYear(), next.getMonthValue(), next.getMonthValue()))
        .isEmpty();
    assertThat(memosOf(account, 1899, 12, 12)).isEmpty();

    // The boundary: the VAT equal to the charges, and the current month, are accepted
    var current = YearMonth.now(clock);
    var boundary =
        memoId(putMemo(account, current.toString(), body("0.00", "10.00", "10.00", "Boundary")));
    var earliest = memoId(putMemo(account, "1900-01", body("1.00", "0.00", "0.00", "Earliest")));
    mockMvc
        .perform(get(memosUrl(account) + "?year=1900").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(earliest.toString()));
    assertThat(audits(boundary)).hasSize(1);
    assertThat(auditCountForAccount(account)).isEqualTo(2);
  }

  // --- the first-insert conflict (A-M1) ----------------------------------------------------------

  /**
   * Two first saves for the same account and month: a gate connection inserts the row and holds its
   * transaction open; the service save does not see it (no memo yet), inserts, and blocks on the
   * unique index — observed in {@code pg_stat_activity}, not a sleep — until the gate commits. The
   * loser gets the 409, and the winner's figures stand.
   */
  @Test
  void aFirstSaveRacingAnotherForTheSameMonth_isTheConflict_andTheWinnerStands() throws Exception {
    var account = books.freshAccount(false);
    var month = YearMonth.of(2026, 2);
    var error = new AtomicReference<Throwable>();
    var finished = new AtomicBoolean(false);
    Thread saver;
    var pool = dataSource.unwrap(HikariDataSource.class);
    try (Connection gate =
        DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
      gate.setAutoCommit(false);
      int gatePid;
      try (var st = gate.createStatement();
          var rs = st.executeQuery("SELECT pg_backend_pid()")) {
        rs.next();
        gatePid = rs.getInt(1);
      }
      try (var ps =
          gate.prepareStatement(
              "INSERT INTO \""
                  + books.schema()
                  + "\".trust_interest_memos (trust_account_id, period_month, gross_interest,"
                  + " source_note, recorded_by) VALUES (?, ?, 7.00, 'Winner', ?)")) {
        ps.setObject(1, account);
        ps.setObject(2, month.atDay(1));
        ps.setObject(3, adminId);
        ps.executeUpdate();
      }
      saver =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      books.as(ownerId, () -> memoService.save(account, month, request("Loser")));
                    } catch (Throwable t) {
                      error.set(t);
                    } finally {
                      finished.set(true);
                    }
                  });
      try {
        assertThat(awaitMemoInsertBlockedBy(gatePid, finished))
            .as("the save blocked on the gate's uncommitted row")
            .isTrue();
      } finally {
        gate.commit();
      }
    }
    saver.join(30_000);
    assertThat(saver.isAlive()).as("the save finished within 30 s").isFalse();

    assertThat(error.get()).isInstanceOf(ResourceConflictException.class);
    var problem = ((ResourceConflictException) error.get()).getBody();
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Memo saved at the same time");
    assertThat(problem.getDetail())
        .isEqualTo(
            "Another member saved the memo for February 2026 on this trust account at the same"
                + " time. Reload the page and try again.");
    assertThat(memosOf(account, 2026, 2, 2))
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getSourceNote()).isEqualTo("Winner");
              assertThat(m.getGrossInterest()).isEqualByComparingTo("7.00");
            });
    assertThat(auditCountForAccount(account)).isZero();
  }

  /** Any other integrity failure on the first insert is not the concurrent-save conflict. */
  @Test
  void anotherIntegrityFailureOnTheFirstInsert_isNotMappedToTheConflict() {
    var account = books.freshAccount(false);
    var unknownMember = UUID.randomUUID(); // recorded_by references members(id)
    var error = new AtomicReference<Throwable>();
    books.as(
        unknownMember,
        () -> {
          try {
            memoService.save(account, YearMonth.of(2026, 1), request("No such member"));
          } catch (Throwable t) {
            error.set(t);
          }
        });
    assertThat(error.get())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("trust_interest_memos_recorded_by_fkey");
    assertThat(memosOf(account, 2026, 1, 1)).isEmpty();
  }

  // --- helpers ----------------------------------------------------------------------------------

  private interface Step {
    void run() throws Exception;
  }

  /** {@link TrustBooksHarness#assertMoves} as the owner, around a MockMvc step. */
  private void moves(UUID account, Expected expected, Step step) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                expected,
                () -> {
                  try {
                    step.run();
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                }));
  }

  private static String memosUrl(UUID account) {
    return "/api/trust-accounts/" + account + "/interest-memos";
  }

  private static String body(
      String grossInterest, String grossCharges, String chargesVat, String sourceNote) {
    return """
        {"grossInterest":%s,"grossCharges":%s,"chargesVat":%s,"sourceNote":"%s"}"""
        .formatted(grossInterest, grossCharges, chargesVat, sourceNote);
  }

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private JwtRequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private ResultActions putMemo(UUID account, String month, String json) throws Exception {
    return putMemo(account, month, json, owner());
  }

  private ResultActions putMemo(
      UUID account, String month, String json, JwtRequestPostProcessor jwt) throws Exception {
    return mockMvc.perform(
        put(memosUrl(account) + "/" + month)
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /**
   * The response's timestamps carry no sub-microsecond part: Postgres timestamptz stores
   * microseconds, and Linux clocks give nanoseconds — an untruncated value would differ from the
   * stored one (the CI failure on fb900e5e3).
   */
  private static void assertStorePrecision(MvcResult result) throws Exception {
    var body = result.getResponse().getContentAsString();
    for (var path : List.of("$.createdAt", "$.updatedAt")) {
      var instant = Instant.parse(JsonPath.<String>read(body, path));
      assertThat(instant).as(path).isEqualTo(instant.truncatedTo(ChronoUnit.MICROS));
    }
  }

  private static UUID memoId(ResultActions result) throws Exception {
    var response = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private List<TrustInterestMemo> memosOf(UUID account, int year, int fromMonth, int toMonth) {
    return books.read(
        () ->
            memoRepository.findByTrustAccountIdAndPeriodMonthBetweenOrderByPeriodMonthAsc(
                account, LocalDate.of(year, fromMonth, 1), LocalDate.of(year, toMonth, 1)));
  }

  private List<AuditEvent> audits(UUID memoId) {
    return books.read(
        () ->
            auditEventRepository
                .findByFilter(ENTITY_TYPE, memoId, null, SAVED, null, null, PageRequest.of(0, 10))
                .getContent());
  }

  /** The memo audit events of {@code account} (scoped by the account id in their details). */
  private long auditCountForAccount(UUID account) {
    return books.read(
        () ->
            auditEventRepository
                .findByFilter(ENTITY_TYPE, null, null, SAVED, null, null, PageRequest.of(0, 500))
                .getContent()
                .stream()
                .filter(e -> account.toString().equals(e.getDetails().get("trust_account_id")))
                .count());
  }

  private String listFingerprint(UUID listId) {
    return jdbc.queryForObject(
        "SELECT md5(m::text) FROM \"" + books.schema() + "\".trust_monthly_lists m WHERE m.id = ?",
        String.class,
        listId);
  }

  private void accountLockedThrough(UUID account, String lockedThrough) throws Exception {
    mockMvc
        .perform(get("/api/trust-accounts/{id}", account).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockedThrough").value(lockedThrough));
  }

  private TrustIntegrityReport check(UUID account) {
    var report = new TrustIntegrityReport[1];
    books.as(ownerId, () -> report[0] = integrityService.check(account, books.today(), null));
    return report[0];
  }

  private static List<String> codes(TrustIntegrityReport report) {
    return report.flags().stream().map(IntegrityFlag::code).sorted().toList();
  }

  /** A March statement with one R1 000 deposit (1 March), matched. */
  private BankStatementResponse matchedMarchStatement(UUID account, String fileName) {
    var client = books.newClient("march");
    var deposit = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            fileName,
            "01/03/2026,Deposit received,1000.00,1000.00,TIM-M" + (++statementCounter));
    match(march, 0, deposit);
    return march;
  }

  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    var csv =
        """
        FNB Trust Account Statement - Account 62400000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", lines));
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        ownerId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    books.as(ownerId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  private TrustReconciliationResponse reconcile(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        ownerId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] = reconciliationService.calculateReconciliation(created.id());
        });
    return recon[0];
  }

  private TrustReconciliationResponse complete(UUID reconciliationId) {
    var completed = new TrustReconciliationResponse[1];
    books.as(
        ownerId,
        () -> completed[0] = reconciliationService.completeReconciliation(reconciliationId));
    return completed[0];
  }

  private static void forbidden(ResultActions result) throws Exception {
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  private static void moduleOff(ResultActions result) throws Exception {
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This feature is not enabled for your organization. "
                        + "An admin can enable it in Settings → Features."))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
  }

  private static void notFound(ResultActions result, UUID account) throws Exception {
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustAccount not found"))
        .andExpect(jsonPath("$.detail").value("No trustaccount found with id " + account));
  }

  /** Bean validation refused exactly one field, {@code field}. */
  private static void validation(ResultActions result, String field) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors.length()").value(1))
        .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
  }

  private static SaveInterestMemoRequest request(String sourceNote) {
    return new SaveInterestMemoRequest(
        new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, sourceNote);
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} runs a trust_interest_memos
   * statement (true) or the saver finishes without having been seen blocked (false).
   */
  private boolean awaitMemoInsertBlockedBy(int holderPid, AtomicBoolean finished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE '%trust_interest_memos%'",
              Long.class, holderPid);
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (finished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the save neither blocked nor finished within 30 s");
  }

  private static void refused(ResultActions result, String title, String detail) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value(title))
        .andExpect(jsonPath("$.detail").value(detail));
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
}
