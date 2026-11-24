package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.C;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Accounts;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Validated;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TakeOnCommitRefusal;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPositionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningSelfCheck;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustTakeOnRunner;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.ErrorResponseException;

/**
 * Phase 93 657C (task 657.24, §17.3.9, §17.3.13): the trust take-on committed through the real path
 * — upload → mapping → position → dry run → the owner's sign-off → the claim → the drain ({@link
 * TakeOnTestDrain}) — on the architecture §17.3.13 figures. Every take-on has its own fresh banking
 * and investment accounts; clients and matters are taken on once (A, B, C … with matters A1, B1, C1
 * …); every assertion is scoped to this test's own batch or accounts (read-only SQL for evidence).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class TrustPositionTakeOnIntegrationTest {

  private static final String ORG_ID = "org_trust_position_take_on_test";
  private static final String OWNER = "user_tpt_owner";
  private static final LocalDate CUTOVER = LocalDate.parse(C);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksService trustBooks;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;
  @Autowired private TrustAccountRepository trustAccounts;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TrustTakeOnRunner runner;
  @Autowired private ApplicationEvents events;
  @Autowired private org.springframework.context.ApplicationContext context;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository
      portalTrust;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("portalJdbcClient")
  private org.springframework.jdbc.core.simple.JdbcClient portalJdbc;

  private String schema;
  private UUID ownerId;
  private TrustBooksFixture.Tenant books;
  private TakeOnTrustWorld world;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Take-On Commit Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tpt_owner@test.com", "Commit Owner", "owner"));
    books = fixture.tenant(schema, ORG_ID, ownerId);
    world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, "tpt");
    world.takeOnClientsAndMatters(List.of("A", "B", "C", "D", "X", "Y", "Z", "PA", "PB", "PC"));
  }

  @AfterAll
  void booksStayBalanced() {
    books.assertFirmBalanced();
  }

  // 1
  @Test
  void theSection17313TakeOnPostsExactlyItsRows_approvedBySignerAndClearedAsTheRegistrySays()
      throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);

    String json = world.batchJson(batch.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(json, "$.counts.created")).isEqualTo(9);
    assertThat((Integer) JsonPath.read(json, "$.counts.skipped")).isZero();
    assertThat((Integer) JsonPath.read(json, "$.counts.failed")).isZero();
    assertThat((Object) JsonPath.read(json, "$.reportId")).isNotNull();
    assertThat((List<String>) JsonPath.read(json, "$.allowedActions"))
        .containsExactly("DOWNLOAD_RESULT");

    UUID a = world.client("A");
    UUID b = world.client("B");
    UUID c = world.client("C");
    UUID a1 = world.matter("A1");
    UUID b1 = world.matter("B1");
    UUID c1 = world.matter("C1");
    var rows =
        jdbc.queryForList(
            ("SELECT trust_account_id, transaction_type, amount, customer_id, project_id,"
                    + " cleared_at_cutover, status, approved_by, approved_at, posted_at,"
                    + " transaction_date, recorded_by FROM \"%s\".trust_transactions"
                    + " WHERE take_on_batch_id = ?")
                .formatted(schema),
            batch.batchId());
    var actual =
        rows.stream()
            .map(
                r ->
                    List.of(
                        r.get("trust_account_id"),
                        r.get("transaction_type"),
                        ((BigDecimal) r.get("amount")).toPlainString(),
                        String.valueOf(r.get("customer_id")),
                        String.valueOf(r.get("project_id")),
                        r.get("cleared_at_cutover")))
            .toList();
    UUID g = acc.g();
    assertThat(actual)
        .containsExactlyInAnyOrder(
            List.of(g, "TAKEON_CLIENT", "300000.00", a.toString(), a1.toString(), true),
            List.of(g, "TAKEON_CLIENT", "150000.00", b.toString(), b1.toString(), true),
            List.of(g, "TAKEON_CLIENT", "49500.00", c.toString(), c1.toString(), true),
            List.of(g, "TAKEON_FUND", "1250.00", "null", "null", true),
            List.of(g, "TAKEON_UNIDENTIFIED", "2000.00", "null", "null", true),
            List.of(g, "TAKEON_UNIDENTIFIED", "750.00", "null", "null", true),
            List.of(g, "TAKEON_FIRM_INV_OUT", "100000.00", "null", "null", true),
            List.of(g, "TAKEON_DEP_TRANSIT", "5000.00", b.toString(), b1.toString(), false),
            List.of(g, "TAKEON_DEP_OFFSET", "5000.00", b.toString(), b1.toString(), true),
            List.of(g, "TAKEON_PAY_UNPRES", "20000.00", a.toString(), a1.toString(), false),
            List.of(g, "TAKEON_PAY_OFFSET", "20000.00", a.toString(), a1.toString(), true),
            List.of(acc.i3(), "TAKEON_FIRM_INV_IN", "100000.00", "null", "null", true),
            List.of(acc.i4(), "TAKEON_INVESTMENT", "200000.00", a.toString(), a1.toString(), true));
    var signedAt =
        jdbc.queryForObject(
            ("SELECT signed_at FROM \"%s\".take_on_sign_offs WHERE batch_id = ?"
                    + " AND kind = 'TRUST_SIGN_OFF' AND revoked_at IS NULL")
                .formatted(schema),
            java.sql.Timestamp.class,
            batch.batchId());
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.get("status")).isEqualTo("APPROVED");
              assertThat(r.get("approved_by")).isEqualTo(ownerId);
              assertThat(r.get("approved_at")).isEqualTo(signedAt);
              assertThat(r.get("posted_at")).isNotNull();
              assertThat(r.get("recorded_by")).isEqualTo(ownerId);
              assertThat(((java.sql.Date) r.get("transaction_date")).toLocalDate())
                  .isEqualTo(CUTOVER);
            });
    // the outstanding items carry the file's reference (auto-match by exact reference)
    assertThat(
            jdbc.queryForList(
                ("SELECT reference FROM \"%s\".trust_transactions WHERE take_on_batch_id = ?"
                        + " AND transaction_type IN ('TAKEON_DEP_TRANSIT', 'TAKEON_PAY_UNPRES')")
                    .formatted(schema),
                String.class,
                batch.batchId()))
        .containsExactlyInAnyOrder("EFT B-0001", "PAY-778");

    // two held unidentified items: no line, their original received dates, attached
    var items =
        jdbc.queryForList(
            ("SELECT received_date, amount, status, bank_statement_line_id, deposit_transaction_id"
                    + " FROM \"%s\".unidentified_deposits WHERE take_on_batch_id = ?"
                    + " ORDER BY received_date")
                .formatted(schema),
            batch.batchId());
    assertThat(items)
        .extracting(i -> ((java.sql.Date) i.get("received_date")).toLocalDate())
        .containsExactly(LocalDate.of(2025, 6, 15), LocalDate.of(2026, 9, 3));
    assertThat(items)
        .allSatisfy(
            i -> {
              assertThat(i.get("status")).isEqualTo("HELD");
              assertThat(i.get("bank_statement_line_id")).isNull();
              assertThat(i.get("deposit_transaction_id")).isNotNull();
            });

    // two investments, each with its placement row as deposit_transaction_id
    var investments =
        jdbc.queryForList(
            ("SELECT i.investment_basis, i.status, i.trust_account_id, i.investment_account_id,"
                    + " i.customer_id, i.project_id, i.principal, i.interest_rate,"
                    + " i.accrued_interest_at_take_on, i.instruction_reference,"
                    + " i.endorsement_confirmed, t.transaction_type AS placement_type,"
                    + " t.investment_id = i.id AS placement_names_it"
                    + " FROM \"%s\".trust_investments i"
                    + " JOIN \"%s\".trust_transactions t ON t.id = i.deposit_transaction_id"
                    + " WHERE i.take_on_batch_id = ? ORDER BY i.principal")
                .formatted(schema, schema),
            batch.batchId());
    assertThat(investments).hasSize(2);
    var s863 = investments.get(0);
    assertThat(s863.get("investment_basis")).isEqualTo("FIRM_DISCRETION");
    assertThat(s863.get("placement_type")).isEqualTo("TAKEON_FIRM_INV_IN");
    assertThat(s863.get("investment_account_id")).isEqualTo(acc.i3());
    assertThat((BigDecimal) s863.get("interest_rate")).isEqualByComparingTo("0.0725");
    var s864 = investments.get(1);
    assertThat(s864.get("investment_basis")).isEqualTo("CLIENT_INSTRUCTION");
    assertThat(s864.get("placement_type")).isEqualTo("TAKEON_INVESTMENT");
    assertThat(s864.get("investment_account_id")).isEqualTo(acc.i4());
    assertThat(s864.get("customer_id")).isEqualTo(a);
    assertThat(s864.get("project_id")).isEqualTo(a1);
    assertThat((BigDecimal) s864.get("interest_rate")).isEqualByComparingTo("0.0750");
    assertThat((BigDecimal) s864.get("accrued_interest_at_take_on"))
        .isEqualByComparingTo("1234.56");
    assertThat(s864.get("instruction_reference")).isEqualTo("INSTR-A-01");
    assertThat(s864.get("endorsement_confirmed")).isEqualTo(true);
    assertThat(investments)
        .allSatisfy(
            i -> {
              assertThat(i.get("status")).isEqualTo("ACTIVE");
              assertThat(i.get("trust_account_id")).isEqualTo(g);
              assertThat(i.get("placement_names_it")).isEqualTo(true);
            });

    // G, I3 and I4 are taken on at C by this batch
    for (UUID account : List.of(g, acc.i3(), acc.i4())) {
      var taken =
          jdbc.queryForMap(
              "SELECT take_on_cutover_date, take_on_batch_id FROM \"%s\".trust_accounts WHERE id = ?"
                  .formatted(schema),
              account);
      assertThat(((java.sql.Date) taken.get("take_on_cutover_date")).toLocalDate())
          .isEqualTo(CUTOVER);
      assertThat(taken.get("take_on_batch_id")).isEqualTo(batch.batchId());
    }

    // every take-on row CREATED, naming the trust transaction it posted
    var takeOnRows =
        jdbc.queryForList(
            ("SELECT section, status, result_entity_type, result_entity_id, result_refs::text AS refs"
                    + " FROM \"%s\".take_on_rows WHERE batch_id = ?")
                .formatted(schema),
            batch.batchId());
    assertThat(takeOnRows)
        .hasSize(9)
        .allSatisfy(
            r -> {
              assertThat(r.get("status")).isEqualTo("CREATED");
              assertThat(r.get("result_entity_type")).isEqualTo("TRUST_TRANSACTION");
              assertThat(r.get("result_entity_id")).isNotNull();
            });
    assertThat(takeOnRows)
        .filteredOn(r -> "OUTSTANDING_ITEMS".equals(r.get("section")))
        .allSatisfy(r -> assertThat((String) r.get("refs")).contains("offsetTransactionId"));
    assertThat(takeOnRows)
        .filteredOn(r -> "INVESTMENTS".equals(r.get("section")))
        .allSatisfy(r -> assertThat((String) r.get("refs")).contains("investmentId"));

    // audits: signed off, report generated, committed
    var audits = TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId());
    assertThat(audits)
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .contains(
            "takeon.batch.signed_off", "takeon.batch.commit_requested", "takeon.batch.committed")
        .doesNotContain("takeon.batch.commit_failed");
    assertThat(
            audits.stream()
                .filter(e -> e.eventType().equals("takeon.batch.committed"))
                .findFirst()
                .orElseThrow()
                .details())
        .containsEntry("dataset", "TRUST_POSITION")
        .containsEntry("created_rows", 9)
        .containsEntry("skipped_rows", 0);
  }

  // 2
  @Test
  void theBooksAtTheCutoverDateAreTheSection17313Figures_toTheCent() throws Exception {
    Accounts acc = world.freshAccounts();
    world.committed(acc);

    var g = books.read(() -> trustBooks.position(acc.g(), CUTOVER));
    assertThat(g.cashbook()).isEqualByComparingTo("403500.00");
    assertThat(g.clientLedgers()).isEqualByComparingTo("499500.00");
    assertThat(g.fundLedger()).isEqualByComparingTo("1250.00");
    assertThat(g.unidentifiedDeposits()).isEqualByComparingTo("2750.00");
    assertThat(g.interAccount()).isEqualByComparingTo("-100000.00");
    assertThat(g.investmentCreditors()).isEqualByComparingTo("0.00");
    assertThat(g.creditorTotal()).isEqualByComparingTo("403500.00");
    assertThat(g.difference()).isEqualByComparingTo("0.00");
    var outstanding = books.read(() -> ledgerQueries.outstanding(acc.g(), CUTOVER));
    assertThat(outstanding.getOutstandingDeposits()).isEqualByComparingTo("5000.00");
    assertThat(outstanding.getOutstandingPayments()).isEqualByComparingTo("20000.00");

    var i3 = books.read(() -> trustBooks.position(acc.i3(), CUTOVER));
    assertThat(i3.cashbook()).isEqualByComparingTo("100000.00");
    assertThat(i3.interAccount()).isEqualByComparingTo("100000.00");
    assertThat(i3.difference()).isEqualByComparingTo("0.00");
    var i4 = books.read(() -> trustBooks.position(acc.i4(), CUTOVER));
    assertThat(i4.cashbook()).isEqualByComparingTo("200000.00");
    assertThat(i4.investmentCreditors()).isEqualByComparingTo("200000.00");
    assertThat(i4.difference()).isEqualByComparingTo("0.00");

    // the client ledgers: A1 300 000 − 20 000 + 20 000; B1 150 000 + 5 000 − 5 000; C1 49 500
    assertThat(books.clientLedger(acc.g(), client("A"))).isEqualByComparingTo("300000.00");
    assertThat(books.clientLedger(acc.g(), client("B"))).isEqualByComparingTo("150000.00");
    assertThat(books.clientLedger(acc.g(), client("C"))).isEqualByComparingTo("49500.00");
  }

  // 3
  @Test
  void theSignOffAndTheClaimAreBoundToTheValidationHash() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    TakeOnTestApi.signOff(mockMvc, world.owner(), batch.batchId(), "0".repeat(64))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HASH_MISMATCH"));
    world.signOff(batch);
    assertProblem(
        () ->
            TakeOnTestDrain.commit(
                commitService, schema, ORG_ID, ownerId, batch.batchId(), "f".repeat(64)),
        409,
        "HASH_MISMATCH");
    assertThat(batchStatus(batch.batchId())).isEqualTo("VALIDATED");
  }

  // 4
  @Test
  void aRevalidationThatChangesTheHashRevokesTheSignOff() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    assertThat((List<String>) JsonPath.read(world.batchJson(batch.batchId()), "$.allowedActions"))
        .contains("COMMIT", "REVOKE_SIGN_OFF");

    // a changed input (the acknowledgement is part of the hash) and a new dry run
    TakeOnTestApi.position(
            mockMvc,
            world.owner(),
            batch.batchId(),
            TakeOnTrustFiles.withFigure(
                TakeOnTrustFiles.section17313Position(C), "midMonthAcknowledged", true))
        .andExpect(status().isOk());
    String again =
        json(
            TakeOnTestApi.validate(mockMvc, world.owner(), batch.batchId())
                .andExpect(status().isOk()));
    String newHash = JsonPath.read(again, "$.validationHash");
    assertThat(newHash).isNotEqualTo(batch.hash());
    assertThat((List<?>) JsonPath.read(again, "$.signOffs")).isEmpty();
    assertThat((List<String>) JsonPath.read(again, "$.allowedActions"))
        .contains("SIGN_OFF")
        .doesNotContain("COMMIT");
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_reason FROM \"%s\".take_on_sign_offs WHERE batch_id = ?"
                    .formatted(schema),
                String.class,
                batch.batchId()))
        .isEqualTo("REVALIDATED");
    assertProblem(
        () ->
            TakeOnTestDrain.commit(
                commitService, schema, ORG_ID, ownerId, batch.batchId(), newHash),
        409,
        "TAKE_ON_SIGN_OFF_REQUIRED");
  }

  // 5
  @Test
  void aPostingDatedOnOrBeforeTheCutoverAfterTheClaim_rollsTheCommitBack() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);
    deposit(acc.g(), "A", "A1", "100.00", C).andExpect(status().isCreated());

    world.drain();

    String json = world.batchJson(batch.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(json, "$.lastError"))
        .isEqualTo(
            "This account already has transactions dated on or before the cutover date. A take-on"
                + " must be the first entry.");
    assertThat((List<?>) JsonPath.read(json, "$.signOffs")).hasSize(1); // still live
    assertCommitFailed(batch.batchId(), "PRECONDITION_FAILED");
    assertNothingPosted(batch.batchId(), acc);
  }

  // 6
  @Test
  void aSelfCheckMismatchRollsEverythingBack_andTheBatchReturnsToValidated() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);

    // the commit built by hand (no mock bean, no new context key) with a self-check that runs the
    // real proof — every row posted and tying — and then fails
    var b = context.getBean(TrustOpeningSelfCheck.class);
    var failing =
        new TrustOpeningSelfCheck(
            trustBooks,
            ledgerQueries,
            context.getBean(
                io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books
                    .TrustIntegrityService.class)) {
          @Override
          public TrustOpeningSelfCheck.Figures assertTies(
              TrustOpeningPlan plan,
              io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch batch,
              Map<UUID, TrustOpeningSelfCheck.Books> before) {
            b.assertTies(plan, batch, before);
            throw TakeOnCommitRefusal.selfCheckFailed();
          }
        };
    var service = handBuiltService(failing);
    var handRunner =
        new TrustTakeOnRunner(
            service,
            context.getBean(TakeOnBatchRepository.class),
            context.getBean(TakeOnAudit.class),
            transactionManager);
    var outcome = asCommitter(() -> handRunner.commit(batch.batchId()));

    assertThat(outcome).isEqualTo(TakeOnRowRunner.Outcome.RETURNED_TO_VALIDATED);
    String json = world.batchJson(batch.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(json, "$.lastError"))
        .isEqualTo(
            "The trust position did not agree with the books when it was posted, so nothing was"
                + " posted. Validate the take-on again. If it happens again, contact support.");
    assertCommitFailed(batch.batchId(), "SELF_CHECK_FAILED");
    assertNothingPosted(batch.batchId(), acc);
  }

  // 7
  @Test
  void anAccountTakesOnOnePositionOnly() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated first = world.committed(acc);
    Validated second =
        world.validated(acc.g(), world.files(acc), TakeOnTrustFiles.section17313Position(C));

    // the first committed rows are dated C, so the first failing account check is "earlier entries"
    assertThat((List<String>) JsonPath.read(second.json(), "$.summary.accountErrors[*].code"))
        .containsExactly("ACCOUNT_HAS_EARLIER_ENTRIES");
    assertThat((Integer) JsonPath.read(second.json(), "$.counts.error")).isEqualTo(1);
    assertThat((List<String>) JsonPath.read(second.json(), "$.allowedActions"))
        .doesNotContain("SIGN_OFF", "COMMIT");
    TakeOnTestApi.signOff(mockMvc, world.owner(), second.batchId(), second.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_ERRORS_REMAIN"));
    assertThat(batchStatus(first.batchId())).isEqualTo("COMPLETED");
  }

  // 8
  @Test
  void aConcurrentPostingHoldingTheAccountMakesTheCommitWait() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);

    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    // a posting's lock: the account FOR SHARE, held to its commit
    var posting =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    books.read(
                        () -> {
                          trustAccounts.findByIdForShare(acc.g()).orElseThrow();
                          held.countDown();
                          await(release);
                          return null;
                        });
                  } catch (Throwable t) {
                    failure.set(t);
                    held.countDown();
                  }
                });
    assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
    var drained = Thread.ofVirtual().start(world::drain);
    Thread.sleep(700);
    assertThat(drained.isAlive()).as("the commit waits for the account lock").isTrue();
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMMITTING");
    assertThat(takeOnRowCount(batch.batchId())).isZero();

    release.countDown();
    posting.join(30_000);
    drained.join(60_000);
    assertThat(failure.get()).isNull();
    assertThat(drained.isAlive()).isFalse();
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(takeOnRowCount(batch.batchId())).isEqualTo(13);
  }

  // 9
  @Test
  void aRedeliveredDrainSkipsABatchAnotherWorkerHolds() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);

    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker =
        Thread.ofVirtual()
            .start(
                () ->
                    books.read(
                        () -> {
                          context
                              .getBean(TakeOnBatchRepository.class)
                              .findByIdForUpdate(batch.batchId())
                              .orElseThrow();
                          held.countDown();
                          await(release);
                          return null;
                        }));
    assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
    try {
      var outcome = asCommitter(() -> runner.commit(batch.batchId()));
      assertThat(outcome).isEqualTo(TakeOnRowRunner.Outcome.ROWS_HELD_ELSEWHERE);
      assertThat(takeOnRowCount(batch.batchId())).isZero();
      assertThat(batchStatus(batch.batchId())).isEqualTo("COMMITTING");
    } finally {
      release.countDown();
      worker.join(30_000);
    }
    world.drain();
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(takeOnRowCount(batch.batchId())).isEqualTo(13);
    // a second drain finds nothing to do and posts nothing twice
    world.drain();
    assertThat(takeOnRowCount(batch.batchId())).isEqualTo(13);
  }

  // 10
  @Test
  void thePortalShowsOneBroughtForwardLinePerMatter_andTheInvestedBalance() throws Exception {
    Accounts acc = world.freshAccounts();
    var files = portalFiles(acc);
    var batch = world.validated(acc.g(), files, TakeOnTrustFiles.section17313Position(C));
    world.signOff(batch);
    world.claim(batch);
    world.drain();
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");

    for (var expected :
        List.of(
            List.of("PA", "300000.00", "200000.00"),
            List.of("PB", "150000.00", "0"),
            List.of("PC", "49500.00", "0"))) {
      UUID client = world.client(expected.get(0));
      UUID matter = world.matter(expected.get(0) + "1");
      var lines = portalTrust.findTransactions(client, matter, null, null, 50, 0);
      assertThat(lines)
          .as(expected.get(0))
          .singleElement()
          .satisfies(
              l -> {
                assertThat(l.transactionType()).isEqualTo("TAKEON_CLIENT");
                assertThat(l.amount()).isEqualByComparingTo(expected.get(1));
                assertThat(l.transactionDate()).isEqualTo(CUTOVER);
              });
      var balance = portalTrust.findBalance(client, matter).orElseThrow();
      assertThat(balance.currentBalance()).isEqualByComparingTo(expected.get(1));
      assertThat(balance.investedBalance()).isEqualByComparingTo(expected.get(2));
    }
    // no offset, item, Fund, unidentified or inter-account row reaches the portal
    var hidden =
        jdbc.queryForList(
            ("SELECT id FROM \"%s\".trust_transactions WHERE take_on_batch_id = ?"
                    + " AND transaction_type <> 'TAKEON_CLIENT'")
                .formatted(schema),
            UUID.class,
            batch.batchId());
    assertThat(hidden).hasSize(10);
    assertThat(
            portalJdbc
                .sql("SELECT count(*) FROM portal.portal_trust_transaction WHERE id = ANY (?)")
                .params((Object) hidden.toArray(new UUID[0]))
                .query(Long.class)
                .single())
        .isZero();
  }

  // 11
  @Test
  void theCommitPublishesNoPerRowEventAndSendsNoEmail() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    int mailBefore = GreenMailTestSupport.getInstance().getReceivedMessages().length;
    long recordedBefore = events.stream(TrustTransactionRecordedEvent.class).count();
    world.claim(batch);
    world.drain();

    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(events.stream(TrustTransactionRecordedEvent.class).count())
        .as("no per-row event")
        .isEqualTo(recordedBefore);
    var committed =
        events.stream(TrustDomainEvent.TakeOnPositionCommitted.class)
            .filter(e -> e.batchId().equals(batch.batchId()))
            .toList();
    assertThat(committed).hasSize(1);
    assertThat(committed.getFirst().accountIds()).containsExactly(accountOrder(acc));
    assertThat(committed.getFirst().committedBy()).isEqualTo(ownerId);
    assertThat(committed.getFirst().reportId())
        .isEqualTo(UUID.fromString(JsonPath.read(world.batchJson(batch.batchId()), "$.reportId")));
    assertThat(GreenMailTestSupport.getInstance().getReceivedMessages().length)
        .as("no email")
        .isEqualTo(mailBefore);
  }

  // 12
  @Test
  void afterTheCommitAnEntryDatedOnOrBeforeTheCutoverIsRefused() throws Exception {
    Accounts acc = world.freshAccounts();
    world.committed(acc);
    deposit(acc.g(), "A", "A1", "100.00", C)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATED_BEFORE_TAKE_ON"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This account was taken on at 31 October 2026. Entries on or before that date"
                        + " belong in the previous system's books."));
    deposit(acc.g(), "A", "A1", "100.00", "2026-11-01").andExpect(status().isCreated());
  }

  // 13
  @Test
  void aNilBalanceIsListedAndSkipped_postingNothing() throws Exception {
    Accounts acc = world.freshAccounts();
    var files =
        TakeOnTrustFiles.withRows(
            world.files(acc),
            TakeOnTrustFiles.CLIENT_LEDGER,
            List.of("A,A1,300000.00,", "B,B1,150000.00,", "C,C1,49500.00,", "D,D1,0.00,"));
    var batch = world.validated(acc.g(), files, TakeOnTrustFiles.section17313Position(C));
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(10);
    world.signOff(batch);
    world.claim(batch);
    world.drain();

    String json = world.batchJson(batch.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(json, "$.counts.created")).isEqualTo(9);
    assertThat((Integer) JsonPath.read(json, "$.counts.skipped")).isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                ("SELECT status, result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ?"
                        + " AND section = 'CLIENT_LEDGER' AND row_number = 5")
                    .formatted(schema),
                batch.batchId()))
        .containsEntry("status", "SKIPPED_ZERO")
        .containsEntry("result_entity_id", null);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".trust_transactions WHERE take_on_batch_id = ?"
                        + " AND customer_id = ?")
                    .formatted(schema),
                Long.class,
                batch.batchId(),
                world.client("D")))
        .isZero();
  }

  // 14 (D-194, R-3)
  @Test
  void aClientAnonymisedAfterTheDryRun_stopsTheSignOffAndTheCommit() throws Exception {
    // two batches naming client X: one signed off, one not yet
    Accounts signedAcc = world.freshAccounts();
    var signed =
        world.validated(signedAcc.g(), withClient(world.files(signedAcc), "X"), position());
    world.signOff(signed);
    Accounts openAcc = world.freshAccounts();
    var open = world.validated(openAcc.g(), withClient(world.files(openAcc), "X"), position());
    anonymise("X");

    assertProblem(
        () ->
            TakeOnTestDrain.commit(
                commitService, schema, ORG_ID, ownerId, signed.batchId(), signed.hash()),
        409,
        "TAKE_ON_ROWS_REDACTED");
    TakeOnTestApi.signOff(mockMvc, world.owner(), open.batchId(), open.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_ROWS_REDACTED"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "A client named in this trust take-on was anonymised after it was validated."
                        + " Validate it again, then sign it off."));
    assertThat(
            (List<String>)
                JsonPath.read(
                    json(TakeOnTestApi.signOffQueue(mockMvc, world.owner())), "$[*].batchId"))
        .doesNotContain(open.batchId().toString(), signed.batchId().toString());
    assertThat((List<String>) JsonPath.read(world.batchJson(open.batchId()), "$.allowedActions"))
        .doesNotContain("SIGN_OFF", "COMMIT");

    // claimed first, anonymised before the drain: the commit refuses under its lock
    Accounts claimedAcc = world.freshAccounts();
    var claimed =
        world.validated(claimedAcc.g(), withClient(world.files(claimedAcc), "Y"), position());
    world.signOff(claimed);
    world.claim(claimed);
    anonymise("Y");
    world.drain();
    String json = world.batchJson(claimed.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(json, "$.lastError"))
        .isEqualTo(
            "A client named in this trust take-on was anonymised after it was validated. Validate"
                + " it again, then sign it off.");
    assertCommitFailed(claimed.batchId(), "ROW_REDACTED");
    assertNothingPosted(claimed.batchId(), claimedAcc);
  }

  // 14a (D-194, CR-2): only the redaction's JSON boolean marks a row redacted
  @Test
  void aCellReadingTrueUnderARedactedHeader_isNotARedaction() throws Exception {
    Validated batch = world.validated(world.freshAccounts());
    var rows = context.getBean(TakeOnRowRepository.class);
    String table = "\"%s\".take_on_rows".formatted(schema);
    UUID rowId =
        jdbc.queryForObject(
            "SELECT id FROM " + table + " WHERE batch_id = ? ORDER BY id LIMIT 1",
            UUID.class,
            batch.batchId());
    // a source column headed "redacted" whose cell reads "true": a JSON string, in both columns
    jdbc.update(
        "UPDATE "
            + table
            + " SET raw_values = raw_values || '{\"redacted\": \"true\"}'::jsonb,"
            + " mapped_values = mapped_values || '{\"redacted\": \"true\"}'::jsonb WHERE id = ?",
        rowId);
    assertThat(books.read(() -> rows.anyRedacted(batch.batchId()))).isFalse();
    assertThat((List<String>) JsonPath.read(world.batchJson(batch.batchId()), "$.allowedActions"))
        .contains("SIGN_OFF");

    jdbc.update(
        "UPDATE " + table + " SET raw_values = '{\"redacted\": true}'::jsonb WHERE id = ?", rowId);
    assertThat(books.read(() -> rows.anyRedacted(batch.batchId()))).isTrue();
  }

  // 14b (D-194, review HIGH-A1): an anonymisation racing the commit keeps its redaction
  @Test
  void anAnonymisationRacingTheCommit_waitsForIt_andItsRedactionSurvives() throws Exception {
    Accounts acc = world.freshAccounts();
    var batch = world.validated(acc.g(), withClient(world.files(acc), "Z"), position());
    world.signOff(batch);
    world.claim(batch);
    UUID z = world.client("Z");
    var posting =
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction
                .TrustPostingService.class);
    // Z's lock anchor exists (committed), so holding it takes no lock on Z's customers row
    books.read(
        () -> {
          posting.lockClient(acc.g(), z);
          return null;
        });

    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    // another trust path holding Z's anchor: the commit stops there, after its batch, clients and
    // rows locks — the point where, unlocked, a redaction could land under it
    var holder =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    books.read(
                        () -> {
                          posting.lockClient(acc.g(), z);
                          held.countDown();
                          await(release);
                          return null;
                        });
                  } catch (Throwable t) {
                    failure.set(t);
                    held.countDown();
                  }
                });
    assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
    Thread drained;
    Thread anonymised;
    var anonymiseFailure = new AtomicReference<Throwable>();
    try {
      drained = Thread.ofVirtual().start(world::drain);
      awaitLockWaiters(1); // the commit, on Z's anchor
      anonymised =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      anonymise("Z");
                    } catch (Throwable t) {
                      anonymiseFailure.set(t);
                    }
                  });
      awaitLockWaiters(2); // the anonymisation, on Z's row (the commit's share lock)
      assertThat(anonymised.isAlive()).as("the anonymisation waits for the commit").isTrue();
      assertThat(
              portalJdbc
                  .sql("SELECT status FROM \"%s\".take_on_batches WHERE id = ?".formatted(schema))
                  .param(batch.batchId())
                  .query(String.class)
                  .single())
          .isEqualTo("COMMITTING");
    } finally {
      release.countDown();
      holder.join(30_000);
    }
    drained.join(60_000);
    anonymised.join(60_000);
    assertThat(failure.get()).isNull();
    assertThat(anonymiseFailure.get()).isNull();
    assertThat(drained.isAlive()).isFalse();
    assertThat(anonymised.isAlive()).isFalse();

    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(takeOnRowCount(batch.batchId())).isEqualTo(13);
    // Z's row was redacted after the commit marked it — and stays redacted
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                        + " AND mapped_values ->> 'clientId' = ?")
                    .formatted(schema),
                Long.class,
                batch.batchId(),
                z.toString()))
        .isZero();
    assertThat(
            jdbc.queryForList(
                ("SELECT status FROM \"%s\".take_on_rows WHERE batch_id = ?"
                        + " AND raw_values = '{\"redacted\": true}'::jsonb"
                        + " AND mapped_values = '{\"redacted\": true}'::jsonb")
                    .formatted(schema),
                String.class,
                batch.batchId()))
        .containsExactly("CREATED");
  }

  /**
   * Waits until {@code n} backends of this database wait on a lock (30 s at most). Polls through
   * the portal pool: the holder, the commit and the anonymisation take the app pool's three
   * connections between them, so a poll through it would wait for one of them to finish.
   */
  private void awaitLockWaiters(int n) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      long waiting =
          portalJdbc
              .sql(
                  "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                      + " AND wait_event_type = 'Lock'")
              .query(Long.class)
              .single();
      if (waiting >= n) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError(
        "expected "
            + n
            + " lock waiter(s); active backends: "
            + portalJdbc
                .sql(
                    "SELECT state, wait_event_type, wait_event, pg_blocking_pids(pid)::text AS"
                        + " blockers, left(query, 160) AS query FROM pg_stat_activity"
                        + " WHERE datname = current_database() AND state <> 'idle'"
                        + " AND pid <> pg_backend_pid()")
                .query()
                .listOfRows());
  }

  // 15 (D-187)
  @Test
  void anInvestmentAccountSharedWithAnotherBankingAccountAtTheSameDate_commitsAndTies()
      throws Exception {
    var other = books.takeOnSection17313(CUTOVER);
    books.completeTakeOnBatch(other.batch());
    String sharedNumber =
        books.read(() -> trustAccounts.findById(other.i4()).orElseThrow().getAccountNumber());
    Accounts fresh = world.freshAccounts();
    Accounts acc = new Accounts(fresh.g(), fresh.i3(), other.i4(), fresh.i3Number(), sharedNumber);
    Validated batch = world.committed(acc);

    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    var i4 = books.read(() -> trustBooks.position(other.i4(), CUTOVER));
    assertThat(i4.investmentCreditors()).isEqualByComparingTo("400000.00");
    assertThat(i4.cashbook()).isEqualByComparingTo("400000.00");
    assertThat(i4.difference()).isEqualByComparingTo("0.00");
  }

  // 16 (§1A #4: the interest rate is a fraction of four decimals)
  @Test
  void anInterestRateThatCannotBeStoredRefusesTheCommit_neverRounding() throws Exception {
    Accounts acc = world.freshAccounts();
    var files =
        TakeOnTrustFiles.withRows(
            world.files(acc),
            TakeOnTrustFiles.INVESTMENTS,
            List.of(
                acc.i3Number()
                    + ",S86(3),Investec,0001,100000.00,7.125%,01/03/2026,,,,,,,,100000.00",
                TakeOnTrustFiles.section864(acc.i4Number(), "INSTR-A-01")));
    var batch = world.validated(acc.g(), files, position());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.error")).isZero();
    world.signOff(batch);
    world.claim(batch);
    world.drain();

    String json = world.batchJson(batch.batchId());
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(json, "$.lastError"))
        .isEqualTo(
            "The interest rate of investment 0001 (7.125%) cannot be stored. Use at most two"
                + " decimals and no more than 100%.");
    assertCommitFailed(batch.batchId(), "REFERENCE_UNRESOLVED");
    assertNothingPosted(batch.batchId(), acc);
  }

  // --- helpers ---

  private TrustBooksFixture.Client client(String ref) {
    return new TrustBooksFixture.Client(world.client(ref), world.matter(ref + "1"), ref);
  }

  private static Map<String, Object> position() {
    return TakeOnTrustFiles.section17313Position(C);
  }

  /** The §17.3.13 files with client C (C/C1, R49 500) replaced by {@code ref}. */
  private static Map<String, List<String>> withClient(Map<String, List<String>> files, String ref) {
    return TakeOnTrustFiles.withRows(
        files,
        TakeOnTrustFiles.CLIENT_LEDGER,
        List.of("A,A1,300000.00,", "B,B1,150000.00,", ref + "," + ref + "1,49500.00,"));
  }

  /** The §17.3.13 files on clients PA, PB, PC (this test's own matters: the portal case). */
  private static Map<String, List<String>> portalFiles(Accounts acc) {
    var files = new java.util.LinkedHashMap<String, List<String>>();
    files.put(
        TakeOnTrustFiles.CLIENT_LEDGER,
        List.of("PA,PA1,300000.00,", "PB,PB1,150000.00,", "PC,PC1,49500.00,"));
    files.put(TakeOnTrustFiles.UNIDENTIFIED_DEPOSITS, TakeOnTrustFiles.section17313Unidentified());
    files.put(
        TakeOnTrustFiles.INVESTMENTS,
        List.of(
            TakeOnTrustFiles.section863(acc.i3Number(), "100000.00", "100000.00"),
            acc.i4Number()
                + ",S86(4),Investec,0002,200000.00,7.50%,01/04/2026,PA,PA1,INSTR-PA,01/04/2026,"
                + "Yes,1234.56,0.00,200000.00"));
    files.put(
        TakeOnTrustFiles.OUTSTANDING_ITEMS,
        List.of(
            "Deposit,30/10/2026,5000.00,EFT B-0001,PB,PB1",
            "Cheque,28/10/2026,20000.00,PAY-778,PA,PA1"));
    return files;
  }

  /** The accounts in the commit's lock order: G, then the investment accounts by UUID. */
  private static UUID[] accountOrder(Accounts acc) {
    var investments = new ArrayList<>(List.of(acc.i3(), acc.i4()));
    investments.sort(null);
    return new UUID[] {acc.g(), investments.get(0), investments.get(1)};
  }

  private org.springframework.test.web.servlet.ResultActions deposit(
      UUID account, String clientRef, String matterNumber, String amount, String date)
      throws Exception {
    return mockMvc.perform(
        post("/api/trust-accounts/" + account + "/transactions/deposit")
            .with(world.owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"clientId": "%s", "matterId": "%s", "amount": %s,
                 "reference": "DEP-%s", "description": "Deposit", "transactionDate": "%s"}
                """
                    .formatted(
                        world.client(clientRef),
                        world.matter(matterNumber),
                        amount,
                        UUID.randomUUID().toString().substring(0, 8),
                        date)));
  }

  private void anonymise(String ref) throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + world.client(ref) + "/anonymize")
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"confirmationName\": \"Client %s tpt\", \"reason\": \"test\"}"
                        .formatted(ref)))
        .andExpect(status().isOk());
  }

  private String batchStatus(UUID batchId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".take_on_batches WHERE id = ?".formatted(schema),
        String.class,
        batchId);
  }

  private long takeOnRowCount(UUID batchId) {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".trust_transactions WHERE take_on_batch_id = ?"
                .formatted(schema),
            Long.class,
            batchId);
    return n == null ? 0 : n;
  }

  /** The batch's commit failed with {@code reasonCode} (the last commit_failed audit). */
  private void assertCommitFailed(UUID batchId, String reasonCode) {
    var failed =
        TakeOnTestApi.auditEvents(jdbc, schema, batchId).stream()
            .filter(e -> e.eventType().equals("takeon.batch.commit_failed"))
            .toList();
    assertThat(failed).isNotEmpty();
    assertThat(failed.getLast().details())
        .containsEntry("reason_code", reasonCode)
        .containsEntry("dataset", "TRUST_POSITION");
  }

  /** Nothing of the take-on reached the books: rows, items, investments, cutover date, report. */
  private void assertNothingPosted(UUID batchId, Accounts acc) {
    assertThat(takeOnRowCount(batchId)).isZero();
    for (String table : List.of("unidentified_deposits", "trust_investments", "take_on_reports")) {
      String column = table.equals("take_on_reports") ? "batch_id" : "take_on_batch_id";
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM \"%s\".%s WHERE %s = ?".formatted(schema, table, column),
                  Long.class,
                  batchId))
          .as(table)
          .isZero();
    }
    for (UUID account : List.of(acc.g(), acc.i3(), acc.i4())) { // every account the commit marks
      assertThat(
              jdbc.queryForObject(
                  "SELECT take_on_cutover_date FROM \"%s\".trust_accounts WHERE id = ?"
                      .formatted(schema),
                  java.sql.Date.class,
                  account))
          .as("take_on_cutover_date of %s", account)
          .isNull();
    }
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                        + " AND status <> 'VALIDATED'")
                    .formatted(schema),
                Long.class,
                batchId))
        .isZero();
  }

  private static void assertProblem(Runnable call, int status, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ErrorResponseException.class)
        .satisfies(
            e -> {
              var problem = ((ErrorResponseException) e).getBody();
              assertThat(problem.getStatus()).isEqualTo(status);
              assertThat(problem.getProperties()).containsEntry("code", code);
            });
  }

  /** As the drain runs a committer: tenant, org and the committing member bound, no capability. */
  private <T> T asCommitter(java.util.function.Supplier<T> call) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .call(call::get);
  }

  private TrustOpeningPositionService handBuiltService(TrustOpeningSelfCheck selfCheck) {
    return new TrustOpeningPositionService(
        context.getBean(TakeOnBatchRepository.class),
        context.getBean(TakeOnSignOffRepository.class),
        context.getBean(TakeOnRowRepository.class),
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon
                .TrustOpeningPositionPlanner.class),
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction
                .TrustPostingService.class),
        trustAccounts,
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon
                .TrustTakeOnPreconditions.class),
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor
                .TrustCreditorAccountRepository.class),
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor
                .UnidentifiedDepositRepository.class),
        context.getBean(
            io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment
                .TrustInvestmentRepository.class),
        selfCheck,
        context.getBean(TakeOnReportService.class),
        context.getBean(TakeOnAudit.class),
        context,
        context.getBean(jakarta.persistence.EntityManager.class));
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(60, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
