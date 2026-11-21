package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 652C (task 652.7, architecture §16.3.12): one reconciliation per trust account and
 * period end. The wizard (`reconciliation/new/page.tsx`) imports the statement, auto-matches it,
 * then on "Review Matches" calls {@code createReconciliation} and {@code calculateReconciliation}.
 * Any reset (re-upload, a changed account or period, a fresh wizard) drops its handle to the draft,
 * so the next "Review Matches" creates again. The second create must return the first draft, not
 * write a second row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconciliationDraftIntegrationTest {

  private static final String ORG_ID = "org_trust_recon_draft";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Recon Draft Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trd_owner", "trd@test.com", "TRD Owner", "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  /**
   * The wizard's sequence twice for one account and period end (the second after a reset dropped
   * the wizard's handle to the draft): import → auto-match → create → calculate, then create →
   * calculate again. One row for (account, period end); the second create returns the first id.
   */
  @Test
  void secondCreateForSameAccountAndPeriod_returnsTheDraft() {
    var account = books.freshAccount(false);
    var client = books.newClient("draft");
    books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "draft-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-DEP");
    books.as(memberId, () -> reconciliationService.autoMatchStatement(march.id()));

    var first = createAndCalculate(account, MARCH_END, march.id());
    var second = createAndCalculate(account, MARCH_END, march.id());

    assertThat(reconciliationRows(account, MARCH_END))
        .as("one reconciliation row for (account, period end)")
        .isEqualTo(1);
    assertThat(second.id()).as("the second create returns the first draft").isEqualTo(first.id());
    assertThat(second.status()).isEqualTo("DRAFT");
  }

  /** Asking again with the statement the draft already points at changes nothing worth auditing. */
  @Test
  void sameStatementAgain_isNotAudited() {
    var account = books.freshAccount(false);
    var client = books.newClient("same statement");
    books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "draft-same.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-SAME");

    var first = createAndCalculate(account, MARCH_END, march.id());
    var again = create(account, MARCH_END, march.id());

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(again.bankStatementId()).isEqualTo(march.id());
    assertThat(auditCount(first.id(), "trust_reconciliation.created")).isEqualTo(1L);
    assertThat(auditCount(first.id(), "trust_reconciliation.statement_changed"))
        .as("no statement change when the statement is the same")
        .isZero();
  }

  /**
   * A corrected re-upload (§16.3.12): create with the new statement returns the same draft,
   * re-pointed to it and recalculated in the create (no calculate call). Matches belong to the
   * statement lines (D-5): A's matched line is untouched, B's lines start unmatched.
   */
  @Test
  void reUpload_returnsTheDraftRepointedToTheNewStatement_andRecalculated() {
    var account = books.freshAccount(false);
    var client = books.newClient("re-upload");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "draft-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-A");
    match(a, 0, deposit);
    var draft = createAndCalculate(account, MARCH_END, a.id());
    assertThat(draft.bankBalance()).isEqualByComparingTo("1000.00");

    var b =
        importStatement(
            account,
            "draft-b.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,TRD-A",
            "02/03/2026,Bank charge,-10.00,990.00,TRD-B");
    var repointed = create(account, MARCH_END, b.id());

    assertThat(repointed.id()).as("the same draft").isEqualTo(draft.id());
    assertThat(repointed.status()).isEqualTo("DRAFT");
    assertThat(repointed.bankStatementId()).as("re-pointed").isEqualTo(b.id());
    assertThat(repointed.bankBalance())
        .as("recalculated in the create: B's closing balance")
        .isEqualByComparingTo("990.00");
    assertThat(repointed.unresolvedLineCount()).as("B's two lines are unmatched").isEqualTo(2);
    assertThat(reconciliationRows(account, MARCH_END)).isEqualTo(1L);
    assertThat(matchStatus(a.lines().getFirst().id()))
        .as("A's matched line is untouched")
        .isEqualTo("MANUALLY_MATCHED");

    assertThat(auditCount(draft.id(), "trust_reconciliation.created")).isEqualTo(1L);
    assertThat(auditCount(draft.id(), "trust_reconciliation.statement_changed")).isEqualTo(1L);
    assertThat(auditDetail(draft.id(), "previous_bank_statement_id")).isEqualTo(a.id().toString());
    assertThat(auditDetail(draft.id(), "bank_statement_id")).isEqualTo(b.id().toString());
    assertThat(auditDetail(draft.id(), "period_end")).isEqualTo("2026-03-31");
  }

  /**
   * A period already reconciled refuses at create (409, §16.3.12) and nothing changes: one row,
   * still COMPLETED, the same completedAt, one monthly list. Also through HTTP.
   */
  @Test
  void createForACompletedPeriod_is409_andChangesNothing() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("completed");
    var deposit = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "draft-done.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-DONE");
    match(march, 0, deposit);
    var draft = createAndCalculate(account, MARCH_END, march.id());
    var completed = complete(draft.id(), march.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.monthlyListId()).isNotNull();
    // Snapshot the stored row: the service response carries the clock's full precision (ns on
    // Linux), the database keeps microseconds — compare stored with stored.
    var before = getReconciliation(draft.id());

    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () ->
                        reconciliationService.createReconciliation(account, MARCH_END, march.id()))
                .isInstanceOfSatisfying(
                    ResourceConflictException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(409);
                      assertThat(e.getBody().getTitle())
                          .isEqualTo("This period is already reconciled");
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "This trust account was reconciled to 31 March 2026. A completed"
                                  + " reconciliation cannot be changed; choose a later period"
                                  + " end.");
                    }));

    mockMvc
        .perform(
            post("/api/trust-accounts/{id}/reconciliations", account)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"periodEnd": "2026-03-31", "bankStatementId": "%s"}
                    """
                        .formatted(march.id())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("This period is already reconciled"));

    assertThat(reconciliationRows(account, MARCH_END)).isEqualTo(1L);
    var after = getReconciliation(draft.id());
    assertThat(after.status()).isEqualTo("COMPLETED");
    assertThat(after.completedAt()).isNotNull().isEqualTo(before.completedAt());
    assertThat(after.monthlyListId()).isEqualTo(completed.monthlyListId());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".trust_monthly_lists WHERE trust_account_id = ?"
                    .formatted(books.schema()),
                Long.class,
                account))
        .isEqualTo(1L);
  }

  /**
   * A re-upload that does not fit the period end is refused by the period rules (400) and the
   * create rolls back: the draft still points at the statement it had.
   */
  @Test
  void reUploadThatDoesNotFit_isRefused_andTheDraftKeepsItsStatement() {
    var account = books.freshAccount(false);
    var client = books.newClient("no fit");
    books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "draft-fit-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-FA");
    var draft = createAndCalculate(account, MARCH_END, a.id());

    var late =
        importStatement(
            account,
            "draft-fit-b.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,TRD-FA",
            "01/04/2026,Bank charge,-10.00,990.00,TRD-FB");
    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () -> reconciliationService.createReconciliation(account, MARCH_END, late.id()))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(400);
                      assertThat(e.getBody().getTitle())
                          .isEqualTo("Statement does not fit the period end");
                    }));

    assertThat(reconciliationRows(account, MARCH_END)).isEqualTo(1L);
    var after = getReconciliation(draft.id());
    assertThat(after.bankStatementId()).as("rolled back: still A").isEqualTo(a.id());
    assertThat(auditCount(draft.id(), "trust_reconciliation.statement_changed")).isZero();
  }

  /**
   * D-3: two first creates for one account and period end race on uq_trust_recon_account_period.
   * The first holds its insert uncommitted; the second's insert blocks on the index, then violates
   * it when the first commits — a clean 409, never a 500. One row.
   */
  @Test
  void concurrentFirstCreates_oneRow_theLoserGets409NotA500() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("race");
    books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "draft-race.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-RACE");

    var firstDone = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstResult = new AtomicReference<TrustReconciliationResponse>();
    var firstError = new AtomicReference<Throwable>();
    var firstBackendPid = new AtomicReference<Integer>();
    var secondResult = new AtomicReference<TrustReconciliationResponse>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);

    Thread firstThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        memberId,
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  firstResult.set(
                                      reconciliationService.createReconciliation(
                                          account, MARCH_END, march.id()));
                                  firstBackendPid.set(
                                      ((Number)
                                              entityManager
                                                  .createNativeQuery("SELECT pg_backend_pid()")
                                                  .getSingleResult())
                                          .intValue());
                                  firstDone.countDown();
                                  await(releaseFirst);
                                }));
                  } catch (Throwable t) {
                    firstError.set(t);
                    firstDone.countDown();
                  }
                });
    assertThat(firstDone.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(firstError.get()).as("the first create").isNull();

    Thread secondThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        memberId,
                        () ->
                            secondResult.set(
                                reconciliationService.createReconciliation(
                                    account, MARCH_END, march.id())));
                  } catch (Throwable t) {
                    secondError.set(t);
                  } finally {
                    secondFinished.set(true);
                  }
                });
    boolean secondBlockedOnFirst;
    try {
      secondBlockedOnFirst =
          awaitBlockedOnReconciliationsHeldByOrFinished(firstBackendPid.get(), secondFinished);
    } finally {
      releaseFirst.countDown();
      join(firstThread);
      join(secondThread);
    }

    assertThat(firstError.get()).as("the first create (committed)").isNull();
    assertThat(secondBlockedOnFirst)
        .as("the second create blocked on the first's uncommitted insert")
        .isTrue();
    assertThat(secondResult.get()).as("the second create succeeded").isNull();
    assertThat(secondError.get())
        .as("the second create, refused with a 409")
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(409);
              assertThat(e.getBody().getTitle())
                  .isEqualTo("Reconciliation started at the same time");
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "Another member started the reconciliation of this trust account to 31"
                          + " March 2026 at the same time. Choose Review Matches again to"
                          + " continue it.");
            });
    assertThat(reconciliationRows(account, MARCH_END)).isEqualTo(1L);

    // asking again after the race returns the winner's draft
    assertThat(create(account, MARCH_END, march.id()).id()).isEqualTo(firstResult.get().id());
  }

  // --- a stale statement after a continuation (CodeRabbit round 1) ------------------------------

  private static final String STALE_TITLE = "This reconciliation now uses a different statement";
  private static final String STALE_DETAIL =
      "Since you opened it, this reconciliation was continued with another bank statement."
          + " Reload the reconciliation and review the statement it now uses before you continue.";

  /**
   * Finding 2: a wizard in another tab still holds statement A after a continuation re-pointed the
   * draft to B (balanced, completable). Its complete names A — refused 409 (service and HTTP), and
   * nothing changes: still DRAFT on B, neither statement RECONCILED, no monthly list, no completed
   * audit. A body without the statement is a 400. Completing with B — the statement the draft uses
   * — succeeds as before.
   */
  @Test
  void completeNamingAStatementTheDraftNoLongerUses_is409_andChangesNothing() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("stale complete");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "draft-stale-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-SA");
    var draft = createAndCalculate(account, MARCH_END, a.id());
    // A day later than A: two statements of one account may not end on the same date (FU-6b).
    // The refusal below comes from the expected-statement check, before any period rule.
    var b =
        importStatement(
            account, "draft-stale-b.csv", "02/03/2026,Deposit received,1000.00,1000.00,TRD-SA");
    match(b, 0, deposit);
    var repointed = create(account, MARCH_END, b.id()); // the other tab's continuation
    assertThat(repointed.id()).isEqualTo(draft.id());
    assertThat(repointed.isBalanced()).as("B is completable").isTrue();
    var before = getReconciliation(draft.id());

    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () -> reconciliationService.completeReconciliation(draft.id(), a.id()))
                .isInstanceOfSatisfying(
                    ResourceConflictException.class,
                    TrustReconciliationDraftIntegrationTest::assertStaleStatement));
    postWithExpectedStatement("complete", draft.id(), a.id())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value(STALE_TITLE))
        .andExpect(jsonPath("$.detail").value(STALE_DETAIL));
    mockMvc
        .perform(
            post("/api/trust-reconciliations/{id}/complete", draft.id())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    var after = getReconciliation(draft.id());
    assertThat(after.status()).isEqualTo("DRAFT");
    assertThat(after.bankStatementId()).as("still B").isEqualTo(b.id());
    assertThat(after.updatedAt()).as("not saved").isEqualTo(before.updatedAt());
    assertThat(statementStatus(a.id())).isNotEqualTo("RECONCILED");
    assertThat(statementStatus(b.id())).isNotEqualTo("RECONCILED");
    assertThat(monthlyLists(account)).as("no list locked").isZero();
    assertThat(auditCount(draft.id(), "trust_reconciliation.completed")).isZero();

    var completed = complete(draft.id(), b.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.bankStatementId()).isEqualTo(b.id());
    assertThat(completed.monthlyListId()).isNotNull();
    assertThat(statementStatus(b.id())).isEqualTo("RECONCILED");
    assertThat(statementStatus(a.id())).isNotEqualTo("RECONCILED");
  }

  /**
   * Finding 2: a stale calculate naming A after the draft was re-pointed to B is refused 409
   * (service and HTTP); the stored figures are B's, unsaved, and no calculation is audited.
   * Calculating with B succeeds as before.
   */
  @Test
  void calculateNamingAStatementTheDraftNoLongerUses_is409_andTheDraftIsUnchanged()
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("stale calculate");
    books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "draft-stalec-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-SC");
    var draft = createAndCalculate(account, MARCH_END, a.id());
    var b =
        importStatement(
            account,
            "draft-stalec-b.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,TRD-SC",
            "02/03/2026,Bank charge,-10.00,990.00,TRD-SCB");
    create(account, MARCH_END, b.id());
    var before = getReconciliation(draft.id());
    long calculatedBefore = auditCount(draft.id(), "trust_reconciliation.calculated");

    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () -> reconciliationService.calculateReconciliation(draft.id(), a.id()))
                .isInstanceOfSatisfying(
                    ResourceConflictException.class,
                    TrustReconciliationDraftIntegrationTest::assertStaleStatement));
    postWithExpectedStatement("calculate", draft.id(), a.id())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value(STALE_TITLE));

    var after = getReconciliation(draft.id());
    assertThat(after.bankStatementId()).isEqualTo(b.id());
    assertThat(after.bankBalance()).as("B's figures").isEqualByComparingTo("990.00");
    assertThat(after.updatedAt()).as("not saved").isEqualTo(before.updatedAt());
    assertThat(auditCount(draft.id(), "trust_reconciliation.calculated"))
        .isEqualTo(calculatedBefore);

    var calculated = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> calculated[0] = reconciliationService.calculateReconciliation(draft.id(), b.id()));
    assertThat(calculated[0].bankBalance()).isEqualByComparingTo("990.00");
    assertThat(auditCount(draft.id(), "trust_reconciliation.calculated"))
        .isEqualTo(calculatedBefore + 1);
  }

  /**
   * Finding 1: a continuation re-pointing the draft from A to B holds its transaction open (the
   * reconciliation row locked, B not committed) when a complete naming A arrives — A is balanced,
   * so before the fix the complete read A unlocked, completed it and then saved A back over B. Now
   * the complete's first read is the row lock: it blocks (observed in pg_stat_activity, not a
   * sleep), reads B once the continuation commits, and is refused 409. The draft keeps B, nothing
   * is completed, no statement is RECONCILED, no list is locked.
   */
  @Test
  void completeDuringAContinuation_waitsOnTheRowLock_thenRefusesTheStaleStatement()
      throws Exception {
    var race =
        staleCallDuringContinuation(
            "race-complete",
            (draftId, statementId) ->
                reconciliationService.completeReconciliation(draftId, statementId));

    assertThat(race.blocked())
        .as("the complete blocked on the continuation's reconciliation row lock")
        .isTrue();
    assertThat(race.finishedWhileHeld()).as("finished only after the release").isFalse();
    assertThat(race.error())
        .as("the stale complete")
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            TrustReconciliationDraftIntegrationTest::assertStaleStatement);
    var after = getReconciliation(race.draftId());
    assertThat(after.status()).isEqualTo("DRAFT");
    assertThat(after.bankStatementId()).as("the continuation's statement").isEqualTo(race.b());
    assertThat(statementStatus(race.a())).isNotEqualTo("RECONCILED");
    assertThat(statementStatus(race.b())).isNotEqualTo("RECONCILED");
    assertThat(monthlyLists(race.account())).isZero();
    assertThat(auditCount(race.draftId(), "trust_reconciliation.completed")).isZero();
  }

  /** Finding 1, calculate: the same race — the calculate waits, reads B, and is refused 409. */
  @Test
  void calculateDuringAContinuation_waitsOnTheRowLock_thenRefusesTheStaleStatement()
      throws Exception {
    var race =
        staleCallDuringContinuation(
            "race-calculate",
            (draftId, statementId) ->
                reconciliationService.calculateReconciliation(draftId, statementId));

    assertThat(race.blocked())
        .as("the calculate blocked on the continuation's reconciliation row lock")
        .isTrue();
    assertThat(race.finishedWhileHeld()).as("finished only after the release").isFalse();
    assertThat(race.error())
        .as("the stale calculate")
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            TrustReconciliationDraftIntegrationTest::assertStaleStatement);
    var after = getReconciliation(race.draftId());
    assertThat(after.bankStatementId()).as("the continuation's statement").isEqualTo(race.b());
    assertThat(after.bankBalance()).as("B's figures, not A's").isEqualByComparingTo("990.00");
  }

  /** The outcome of {@link #staleCallDuringContinuation}. */
  private record ContinuationRace(
      UUID account,
      UUID draftId,
      UUID a,
      UUID b,
      boolean blocked,
      boolean finishedWhileHeld,
      Throwable error) {}

  /**
   * A draft on statement A (matched, balanced); a continuation re-points it to B (two lines, bank
   * R990 — its last line on A's last line date, so A still fits the period end and only the lock
   * stands between the stale call and A) inside a transaction held open; meanwhile {@code
   * staleCall(draft, A)} runs on another thread. Returns whether the stale call blocked on the
   * continuation, whether it finished before the release, and what it threw.
   */
  private ContinuationRace staleCallDuringContinuation(
      String label, BiFunction<UUID, UUID, TrustReconciliationResponse> staleCall)
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient(label);
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, label + "-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-RA");
    match(a, 0, deposit);
    var draft = createAndCalculate(account, MARCH_END, a.id());
    assertThat(draft.isBalanced()).as("A is completable").isTrue();
    var b =
        importStatement(
            account,
            label + "-b.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,TRD-RA",
            "02/03/2026,Bank charge,-10.00,990.00,TRD-RB");
    // FU-6b refuses a second statement ending on A's date at import, so B is imported a day later
    // and then moved onto A's date — a same-day pair imported before FU-6b, which the database can
    // still hold. A then still fits the period end, so only the lock stands between the stale call
    // and A.
    legacySameDay(b.id(), LocalDate.of(2026, 3, 1));

    var continuationDone = new CountDownLatch(1);
    var releaseContinuation = new CountDownLatch(1);
    var continuationError = new AtomicReference<Throwable>();
    var continuationPid = new AtomicReference<Integer>();
    var staleError = new AtomicReference<Throwable>();
    var staleFinished = new AtomicBoolean(false);

    Thread continuation =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        memberId,
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  reconciliationService.createReconciliation(
                                      account, MARCH_END, b.id());
                                  continuationPid.set(
                                      ((Number)
                                              entityManager
                                                  .createNativeQuery("SELECT pg_backend_pid()")
                                                  .getSingleResult())
                                          .intValue());
                                  continuationDone.countDown();
                                  await(releaseContinuation);
                                }));
                  } catch (Throwable t) {
                    continuationError.set(t);
                    continuationDone.countDown();
                  }
                });
    assertThat(continuationDone.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(continuationError.get()).as("the continuation").isNull();

    Thread stale =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(memberId, () -> staleCall.apply(draft.id(), a.id()));
                  } catch (Throwable t) {
                    staleError.set(t);
                  } finally {
                    staleFinished.set(true);
                  }
                });
    boolean blocked;
    boolean finishedWhileHeld;
    try {
      blocked = awaitBlockedOnReconciliationsHeldByOrFinished(continuationPid.get(), staleFinished);
      finishedWhileHeld = staleFinished.get();
    } finally {
      releaseContinuation.countDown();
      join(continuation);
      join(stale);
    }
    assertThat(continuationError.get()).as("the continuation (committed)").isNull();
    return new ContinuationRace(
        account, draft.id(), a.id(), b.id(), blocked, finishedWhileHeld, staleError.get());
  }

  private static void assertStaleStatement(ResourceConflictException e) {
    assertThat(e.getBody().getStatus()).isEqualTo(409);
    assertThat(e.getBody().getTitle()).isEqualTo(STALE_TITLE);
    assertThat(e.getBody().getDetail()).isEqualTo(STALE_DETAIL);
  }

  private ResultActions postWithExpectedStatement(
      String action, UUID reconciliationId, UUID expectedStatementId) throws Exception {
    return mockMvc.perform(
        post("/api/trust-reconciliations/{id}/" + action, reconciliationId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trd_owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"expectedBankStatementId": "%s"}
                """
                    .formatted(expectedStatementId)));
  }

  private String statementStatus(UUID statementId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".bank_statements WHERE id = ?".formatted(books.schema()),
        String.class,
        statementId);
  }

  private long monthlyLists(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_monthly_lists WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /** The wizard's create alone (no calculate). */
  private TrustReconciliationResponse create(UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            recon[0] = reconciliationService.createReconciliation(account, periodEnd, statementId));
    return recon[0];
  }

  private TrustReconciliationResponse complete(UUID reconciliationId, UUID expectedStatementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            recon[0] =
                reconciliationService.completeReconciliation(
                    reconciliationId, expectedStatementId));
    return recon[0];
  }

  private TrustReconciliationResponse getReconciliation(UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(memberId, () -> recon[0] = reconciliationService.getReconciliation(reconciliationId));
    return recon[0];
  }

  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  private String matchStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status FROM \"%s\".bank_statement_lines WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        lineId);
  }

  private long auditCount(UUID entityId, String eventType) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".audit_events WHERE entity_id = ? AND event_type = ?"
            .formatted(books.schema()),
        Long.class,
        entityId,
        eventType);
  }

  /** One detail of the reconciliation's single statement_changed audit event. */
  private String auditDetail(UUID entityId, String key) {
    return jdbc.queryForObject(
        "SELECT details ->> ? FROM \"%s\".audit_events WHERE entity_id = ?"
                .formatted(books.schema())
            + " AND event_type = 'trust_reconciliation.statement_changed'",
        String.class,
        key,
        entityId);
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} is running a
   * trust_reconciliations statement (true — an insert waits on the unique index, or a locking read
   * on the row lock) or the waiter finishes without having been seen blocked (false). Fails if
   * neither happens.
   */
  private boolean awaitBlockedOnReconciliationsHeldByOrFinished(
      int holderPid, AtomicBoolean waiterFinished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE '%trust_reconciliations%'",
              Long.class, holderPid);
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (waiterFinished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the waiter neither blocked nor finished within 30 s");
  }

  private static void join(Thread thread) throws InterruptedException {
    thread.join(30_000);
    assertThat(thread.isAlive()).as("thread finished within 30 s").isFalse();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private long reconciliationRows(UUID account, LocalDate periodEnd) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_reconciliations WHERE trust_account_id = ? AND period_end = ?"
            .formatted(books.schema()),
        Long.class,
        account,
        periodEnd);
  }

  /**
   * Moves every line of {@code statementId} onto {@code date} (and its period end): a same-end-date
   * pair imported before FU-6b refused one at import (ruling F-646C-1).
   */
  private void legacySameDay(UUID statementId, LocalDate date) {
    jdbc.update(
        "UPDATE \"%s\".bank_statement_lines SET transaction_date = ? WHERE bank_statement_id = ?"
            .formatted(books.schema()),
        date,
        statementId);
    jdbc.update(
        "UPDATE \"%s\".bank_statements SET period_end = ? WHERE id = ?".formatted(books.schema()),
        date,
        statementId);
  }

  /** Imports an FNB statement through the production import (as the maker). */
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
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  /** The wizard's "Review Matches": create, then calculate (production service calls). */
  private TrustReconciliationResponse createAndCalculate(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] =
              reconciliationService.calculateReconciliation(
                  created.id(), created.bankStatementId());
        });
    return recon[0];
  }
}
