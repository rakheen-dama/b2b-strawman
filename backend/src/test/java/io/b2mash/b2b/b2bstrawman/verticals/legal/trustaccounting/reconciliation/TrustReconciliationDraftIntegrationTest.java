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
    var completed = complete(draft.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.monthlyListId()).isNotNull();

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
    assertThat(after.completedAt()).isEqualTo(completed.completedAt());
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
          awaitBlockedOnInsertHeldByOrFinished(firstBackendPid.get(), secondFinished);
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

  private TrustReconciliationResponse complete(UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId, () -> recon[0] = reconciliationService.completeReconciliation(reconciliationId));
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
   * trust_reconciliations statement (true — the insert waits on the unique index) or the waiter
   * finishes without having been seen blocked (false). Fails if neither happens.
   */
  private boolean awaitBlockedOnInsertHeldByOrFinished(
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
    throw new AssertionError("the second create neither blocked nor finished within 30 s");
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
          recon[0] = reconciliationService.calculateReconciliation(created.id());
        });
    return recon[0];
  }
}
