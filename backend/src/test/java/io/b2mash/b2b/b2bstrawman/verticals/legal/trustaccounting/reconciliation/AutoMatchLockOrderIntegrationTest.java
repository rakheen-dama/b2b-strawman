package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 follow-up CF-3 (register rows CF-3a / CF-3b; 647A found.md #7, 648B F-10, 652A CF-3):
 * auto-match against every concurrent writer of a statement line or its candidate rows. Each test
 * holds one row on a gate connection outside the pool, starts the first operation and waits until
 * it is blocked by the gate, starts the second and waits until it is blocked or has finished —
 * observed in {@code pg_stat_activity}, never a sleep — then releases the gate and joins both.
 *
 * <p>Auto-match takes its locks in the order every other line writer uses (line → transaction →
 * statement): the statement's UNMATCHED lines first, then the rows it matches (in id order), then
 * the statement — and it reads each of them fresh under its lock. So a concurrent record, manual
 * match or second auto-match is never overwritten (no line flipped over RECORDED, no lost {@code
 * matched_count}, no row linked to two lines), and none of them deadlocks against it.
 *
 * <p>One harness tenant, a fresh account per test; the class ends with {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoMatchLockOrderIntegrationTest {

  private static final String ORG_ID = "org_trust_automatch_locks";

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private StatementLinePostingService statementLinePostingService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Auto-Match Locks Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_tal_owner", "tal@test.com", "TAL Owner", "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- CF-3a: a concurrent write is never overwritten ------------------------------------------

  /**
   * A line recorded (as Fund interest) while auto-match is matching the same line to a deposit.
   * Auto-match is gated on the deposit row; the record runs meanwhile. RED on main: auto-match read
   * the line UNMATCHED before the record committed and then wrote it AUTO_MATCHED over RECORDED —
   * the line points at the deposit while the recorded Fund row still points at the line. Now the
   * line is locked first, so the record waits and is refused as a sequential record is.
   */
  @Test
  void recordFromLine_duringAutoMatchOfTheSameLine_isNeverOverwritten() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("record during auto-match");
    var depositId = deposit(account, client, "100.00", "CF3-REC", LocalDate.of(2026, 3, 1));
    var statement =
        importStatement(account, "cf3-record.csv", "01/03/2026,Deposit,100.00,100.00,CF3-REC");
    var lineId = statement.lines().getFirst().id();

    var outcome =
        gatedOnRow(
            "trust_transactions",
            depositId,
            () -> reconciliationService.autoMatchStatement(statement.id()),
            () ->
                statementLinePostingService.recordFromLine(
                    lineId,
                    new RecordFromLineRequest(
                        StatementLineClassification.FUND_INTEREST,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

    assertThat(outcome.first()).as("auto-match").isNull();
    assertThat(rowsLinkedTo(lineId)).as("rows linked to the line").isEqualTo(1L);
    if (outcome.second() == null) {
      assertThat(matchStatus(lineId))
          .as("a successful record stays RECORDED")
          .isEqualTo("RECORDED");
      assertThat(bankLineOf(depositId)).as("the deposit stays unmatched").isNull();
    } else {
      assertRefusedAsInvalidState(outcome.second());
      assertThat(matchStatus(lineId)).isEqualTo("AUTO_MATCHED");
      assertThat(bankLineOf(depositId)).isEqualTo(lineId);
    }
    assertMatchedCountAgreesWithLines(statement.id());
    assertThat(matchedCount(statement.id())).isEqualTo(1);
  }

  /**
   * A manual match of a second line of the statement (to a row outside auto-match's window) while
   * auto-match is matching the first. RED on main: auto-match wrote {@code matched_count =
   * alreadyMatched + autoMatched} from its stale read, losing the manual match's increment (1, not
   * 2). Now the manual match's line is one of the UNMATCHED lines auto-match locks first (step 1),
   * so the manual match waits behind the whole auto-match and then counts from the committed value
   * — this test is guarded by the line lock. The statement's locking re-read (step 3) is pinned by
   * {@link #unmatchOfAnAlreadyMatchedLine_duringAutoMatch_isNotOverwrittenByAStaleCount}.
   */
  @Test
  void manualMatchOfAnotherLine_duringAutoMatch_keepsBothCounts() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("count during auto-match");
    var autoTarget = deposit(account, client, "100.00", "CF3-CNT-A", LocalDate.of(2026, 3, 1));
    // outside the statement's window (period ±7 days): never an auto-match candidate
    var manualTarget = deposit(account, client, "250.00", "CF3-CNT-M", LocalDate.of(2026, 1, 15));
    var statement =
        importStatement(
            account,
            "cf3-count.csv",
            "01/03/2026,Deposit,100.00,100.00,CF3-CNT-A",
            "02/03/2026,Old deposit,250.00,350.00,");
    var autoLine = statement.lines().get(0).id();
    var manualLine = statement.lines().get(1).id();

    var outcome =
        gatedOnRow(
            "trust_transactions",
            autoTarget,
            () -> reconciliationService.autoMatchStatement(statement.id()),
            () -> reconciliationService.manualMatch(manualLine, manualTarget));

    assertThat(outcome.first()).as("auto-match").isNull();
    assertThat(outcome.second()).as("manual match").isNull();
    assertThat(matchStatus(autoLine)).isEqualTo("AUTO_MATCHED");
    assertThat(matchStatus(manualLine)).isEqualTo("MANUALLY_MATCHED");
    assertThat(bankLineOf(autoTarget)).isEqualTo(autoLine);
    assertThat(bankLineOf(manualTarget)).isEqualTo(manualLine);
    assertThat(matchedCount(statement.id())).as("both matches counted").isEqualTo(2);
    assertMatchedCountAgreesWithLines(statement.id());
  }

  /**
   * A manual match of another statement's line to a row auto-match is about to take. The manual
   * match is gated on its own statement (it holds its line and the row); auto-match then waits on
   * the row. RED on main: auto-match locked the row but kept the stale copy it read before the
   * manual match committed, so it linked the row a second time — two matched lines, one row. Now
   * the row is read fresh under its lock and skipped.
   */
  @Test
  void manualMatchOnAnotherStatement_beforeAutoMatchTakesTheRow_isNotDoubleLinked()
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("cross-statement manual");
    var depositId = deposit(account, client, "300.00", "CF3-X", LocalDate.of(2026, 3, 1));
    var march =
        importStatement(account, "cf3-x-march.csv", "01/03/2026,Deposit,300.00,300.00,CF3-X");
    var duplicate = importStatement(account, "cf3-x-dup.csv", "03/03/2026,Deposit,300.00,300.00,");
    var autoLine = march.lines().getFirst().id();
    var manualLine = duplicate.lines().getFirst().id();

    var outcome =
        gatedOnRow(
            "bank_statements",
            duplicate.id(),
            () -> reconciliationService.manualMatch(manualLine, depositId),
            () -> reconciliationService.autoMatchStatement(march.id()));

    assertThat(outcome.first()).as("manual match").isNull();
    assertThat(outcome.second()).as("auto-match").isNull();
    assertThat(bankLineOf(depositId)).isEqualTo(manualLine);
    assertThat(matchStatus(manualLine)).isEqualTo("MANUALLY_MATCHED");
    assertThat(matchStatus(autoLine))
        .as("the row was taken: left unmatched")
        .isEqualTo("UNMATCHED");
    assertThat(linesMatchedTo(depositId)).as("lines matched to the row").isEqualTo(1L);
    assertMatchedCountAgreesWithLines(march.id());
    assertMatchedCountAgreesWithLines(duplicate.id());
  }

  /**
   * Two auto-matches (two statements of one account, the same row a tier-1 candidate of both), both
   * waiting on the row. RED on main: the second kept its stale copy of the row and linked it again.
   * Now it reads the row fresh under its lock, sees it taken and leaves its line unmatched.
   */
  @Test
  void twoAutoMatchesWantingTheSameRow_linkItOnce() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("double auto-match");
    var depositId = deposit(account, client, "400.00", "CF3-DBL", LocalDate.of(2026, 3, 1));
    var first =
        importStatement(account, "cf3-dbl-1.csv", "01/03/2026,Deposit,400.00,400.00,CF3-DBL");
    var second =
        importStatement(account, "cf3-dbl-2.csv", "01/03/2026,Deposit,400.00,400.00,CF3-DBL");

    var outcome =
        gatedOnRow(
            "trust_transactions",
            depositId,
            () -> reconciliationService.autoMatchStatement(first.id()),
            () -> reconciliationService.autoMatchStatement(second.id()));

    assertThat(outcome.first()).as("first auto-match").isNull();
    assertThat(outcome.second()).as("second auto-match").isNull();
    assertThat(linesMatchedTo(depositId)).as("lines matched to the row").isEqualTo(1L);
    assertThat(bankLineOf(depositId)).isEqualTo(first.lines().getFirst().id());
    assertThat(matchStatus(second.lines().getFirst().id())).isEqualTo("UNMATCHED");
    assertMatchedCountAgreesWithLines(first.id());
    assertMatchedCountAgreesWithLines(second.id());
  }

  // --- CF-3b: no deadlock -----------------------------------------------------------------------

  /**
   * Manual match of line L to row T while auto-match matches L to T. Auto-match is gated on the
   * statement; the manual match then starts. RED on main: auto-match held T (locked when it
   * matched) and wrote L afterwards, while manual match holds L and waits for T — Postgres aborts
   * one with 40P01 (a generic server error). Now auto-match locks L before T, so the manual match
   * waits on L and is refused as a sequential one is.
   */
  @Test
  void manualMatch_duringAutoMatchOfTheSameLineAndRow_noDeadlock() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("manual vs auto");
    var depositId = deposit(account, client, "500.00", "CF3-DL", LocalDate.of(2026, 3, 1));
    var statement =
        importStatement(account, "cf3-deadlock.csv", "01/03/2026,Deposit,500.00,500.00,CF3-DL");
    var lineId = statement.lines().getFirst().id();

    var outcome =
        gatedOnRow(
            "bank_statements",
            statement.id(),
            () -> reconciliationService.autoMatchStatement(statement.id()),
            () -> reconciliationService.manualMatch(lineId, depositId));

    assertThat(outcome.first()).as("auto-match").isNull();
    assertRefusedAsInvalidState(outcome.second());
    assertThat(matchStatus(lineId)).isEqualTo("AUTO_MATCHED");
    assertThat(bankLineOf(depositId)).isEqualTo(lineId);
    assertThat(matchedCount(statement.id())).isEqualTo(1);
  }

  /**
   * Two auto-matches of two statements of one account whose lines take the same two rows in
   * opposite match orders: statement 1 matches {@code lo} then {@code hi}, statement 2 {@code hi}
   * then {@code lo} ({@code lo} < {@code hi} in the order auto-match sorts its planned rows by,
   * {@code UUID.compareTo}). The gate holds row {@code lo}, so both auto-matches get past their own
   * (disjoint) line locks and block in step 2, behind the gate. Released, the first takes {@code
   * lo} then {@code hi}; the second, also locking in id order, waits on {@code lo} holding nothing
   * auto-match #1 needs, then finds both rows taken and leaves its lines unmatched. Without the
   * id-order sort the second would take {@code hi} (its first match) and then wait on {@code lo},
   * while the first, released, waits on {@code hi} — 40P01 ({@code mutation-sort.txt}). RED on main
   * (rows locked in match order, gate on a line then): 40P01.
   */
  @Test
  void twoAutoMatchesTakingTwoRowsInOppositeOrders_lockThemInIdOrder_noDeadlock() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("crossed auto-matches");
    var rowA = deposit(account, client, "110.00", "CF3-CR-A", LocalDate.of(2026, 3, 1));
    var rowB = deposit(account, client, "220.00", "CF3-CR-B", LocalDate.of(2026, 3, 1));
    boolean aIsLo = rowA.compareTo(rowB) < 0;
    var lo = aIsLo ? rowA : rowB;
    var hi = aIsLo ? rowB : rowA;
    var loLine = aIsLo ? "Deposit A,110.00,%s,CF3-CR-A" : "Deposit B,220.00,%s,CF3-CR-B";
    var hiLine = aIsLo ? "Deposit B,220.00,%s,CF3-CR-B" : "Deposit A,110.00,%s,CF3-CR-A";
    var first =
        importStatement(
            account,
            "cf3-cross-1.csv",
            "01/03/2026," + loLine.formatted("1000.00"),
            "02/03/2026," + hiLine.formatted("2000.00"));
    var second =
        importStatement(
            account,
            "cf3-cross-2.csv",
            "01/03/2026," + hiLine.formatted("1000.00"),
            "02/03/2026," + loLine.formatted("2000.00"));

    var outcome =
        gatedOnRow(
            "trust_transactions",
            lo,
            () -> reconciliationService.autoMatchStatement(first.id()),
            () -> reconciliationService.autoMatchStatement(second.id()));

    assertThat(outcome.secondBlocked())
        .as("the second auto-match blocked behind the gate as well")
        .isTrue();
    assertThat(outcome.first()).as("first auto-match (no 40P01)").isNull();
    assertThat(outcome.second()).as("second auto-match (no 40P01)").isNull();
    assertThat(linesMatchedTo(lo)).as("lines matched to lo").isEqualTo(1L);
    assertThat(linesMatchedTo(hi)).as("lines matched to hi").isEqualTo(1L);
    assertThat(bankLineOf(lo)).isEqualTo(first.lines().get(0).id());
    assertThat(bankLineOf(hi)).isEqualTo(first.lines().get(1).id());
    assertMatchedCountAgreesWithLines(first.id());
    assertMatchedCountAgreesWithLines(second.id());
    assertThat(matchedCount(first.id())).isEqualTo(2);
    assertThat(matchedCount(second.id())).isZero();
  }

  // --- the statement is re-read under its lock ---------------------------------------------------

  /**
   * Line X of the statement is already matched (manually); auto-match then runs for line Y, gated
   * on Y's row, so it has read the statement ({@code matched_count} 1) and blocks in step 2.
   * Meanwhile X is unmatched to completion — it does not block: auto-match holds only the UNMATCHED
   * lines (X is not one) and has not locked the statement yet — so the committed count drops to 0.
   * Released, auto-match matches Y and must count from the statement as now committed (0 + 1), not
   * from its stale copy (1 + 1 = 2, with only Y matched). Pins step 3's locking re-read ({@code
   * mutation-refresh.txt}).
   */
  @Test
  void unmatchOfAnAlreadyMatchedLine_duringAutoMatch_isNotOverwrittenByAStaleCount()
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("stale statement count");
    var rowX = deposit(account, client, "150.00", "CF3-ST-X", LocalDate.of(2026, 3, 1));
    var rowY = deposit(account, client, "175.00", "CF3-ST-Y", LocalDate.of(2026, 3, 2));
    var statement =
        importStatement(
            account,
            "cf3-stale.csv",
            "01/03/2026,Deposit X,150.00,150.00,",
            "02/03/2026,Deposit Y,175.00,325.00,CF3-ST-Y");
    var lineX = statement.lines().get(0).id();
    var lineY = statement.lines().get(1).id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineX, rowX));
    assertThat(matchedCount(statement.id())).as("X matched before the run").isEqualTo(1);

    var outcome =
        gatedOnRow(
            "trust_transactions",
            rowY,
            () -> reconciliationService.autoMatchStatement(statement.id()),
            () -> reconciliationService.unmatch(lineX));

    assertThat(outcome.secondBlocked()).as("the unmatch ran without blocking").isFalse();
    assertThat(outcome.first()).as("auto-match").isNull();
    assertThat(outcome.second()).as("unmatch").isNull();
    assertThat(matchStatus(lineX)).isEqualTo("UNMATCHED");
    assertThat(bankLineOf(rowX)).isNull();
    assertThat(matchStatus(lineY)).isEqualTo("AUTO_MATCHED");
    assertThat(bankLineOf(rowY)).isEqualTo(lineY);
    assertMatchedCountAgreesWithLines(statement.id());
    assertThat(matchedCount(statement.id())).isEqualTo(1);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /**
   * Each side's failure (null = succeeded), and whether the second was seen blocked behind the gate
   * (false: it finished without blocking).
   */
  private record Gated(Throwable first, Throwable second, boolean secondBlocked) {}

  /**
   * Holds {@code table}'s row {@code id} {@code FOR NO KEY UPDATE} on a gate connection outside the
   * pool, starts {@code first} and waits until it is blocked by the gate, starts {@code second} and
   * waits (bounded) until it is blocked behind the gate too (on the gate itself, or on the first)
   * or has finished — observed in {@code pg_stat_activity}, not a sleep — then releases the gate
   * and joins both. A lock order that let one side hold a lock the other needs while waiting for
   * one the other holds deadlocks here (Postgres aborts one, 40P01).
   */
  private Gated gatedOnRow(String table, UUID id, Runnable first, Runnable second)
      throws Exception {
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    boolean secondBlocked;
    Thread firstThread = null;
    Thread secondThread = null;
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
              "SELECT id FROM \"%s\".%s WHERE id = ? FOR NO KEY UPDATE"
                  .formatted(books.schema(), table))) {
        ps.setObject(1, id);
        ps.executeQuery().close();
      }
      try {
        firstThread = Thread.ofPlatform().start(() -> capture(first, firstError, null));
        assertThat(awaitBlockedBehind(gatePid, 1, null))
            .as("the first blocked on the gate")
            .isTrue();
        secondThread =
            Thread.ofPlatform().start(() -> capture(second, secondError, secondFinished));
        secondBlocked = awaitBlockedBehind(gatePid, 2, secondFinished);
      } finally {
        gate.rollback();
      }
    } finally {
      if (firstThread != null) {
        firstThread.join(TimeUnit.SECONDS.toMillis(60));
        assertThat(firstThread.isAlive()).as("first finished").isFalse();
      }
      if (secondThread != null) {
        secondThread.join(TimeUnit.SECONDS.toMillis(60));
        assertThat(secondThread.isAlive()).as("second finished").isFalse();
      }
    }
    return new Gated(firstError.get(), secondError.get(), secondBlocked);
  }

  private void capture(Runnable action, AtomicReference<Throwable> error, AtomicBoolean done) {
    try {
      books.as(memberId, action);
    } catch (Throwable t) {
      error.set(t);
    } finally {
      if (done != null) {
        done.set(true);
      }
    }
  }

  /**
   * Waits (bounded, 30 s) until at least {@code expected} backends wait, directly or transitively,
   * on the gate backend {@code gatePid} (true), or {@code finished} is set (false). Only this
   * test's waiters are counted: a backend blocked by anything outside the gate's wait tree is not.
   */
  private boolean awaitBlockedBehind(int gatePid, long expected, AtomicBoolean finished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              """
              WITH RECURSIVE behind(pid) AS (
                SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                UNION
                SELECT a.pid FROM pg_stat_activity a
                  JOIN behind b ON b.pid = ANY(pg_blocking_pids(a.pid))
              )
              SELECT count(*) FROM behind
              """,
              Long.class,
              gatePid);
      if (waiting != null && waiting >= expected) {
        return true;
      }
      if (finished != null && finished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("operation not blocked behind the gate within 30 s");
  }

  /** Refused as the sequential operation is: a 400 on the line's state, not a lock failure. */
  private static void assertRefusedAsInvalidState(Throwable error) {
    assertThat(error)
        .as("refused on the line's state (not a deadlock or lock failure)")
        .isInstanceOfSatisfying(
            InvalidStateException.class, e -> assertThat(e.getBody().getStatus()).isEqualTo(400));
  }

  /** A RECORDED deposit of {@code amount} with its own reference and date; its id. */
  private UUID deposit(
      UUID account,
      TrustBooksFixture.Client client,
      String amount,
      String referencePrefix,
      LocalDate date) {
    var reference = referencePrefix + "-" + SEQUENCE.incrementAndGet();
    var id = new UUID[1];
    books.as(
        memberId,
        () ->
            id[0] =
                transactionService
                    .recordDeposit(
                        account,
                        new RecordDepositRequest(
                            client.id(),
                            client.matter(),
                            new BigDecimal(amount),
                            reference,
                            "Deposit",
                            date))
                    .id());
    references.put(referencePrefix, reference);
    return id[0];
  }

  /** The unique reference each {@link #deposit} prefix was given (lines cite the prefix). */
  private final Map<String, String> references = new ConcurrentHashMap<>();

  /**
   * Imports an FNB statement through the production import (as the owner). A line's trailing
   * reference names a {@link #deposit} prefix and is replaced by that deposit's reference.
   */
  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    var resolved = new String[lines.length];
    for (int i = 0; i < lines.length; i++) {
      var cut = lines[i].lastIndexOf(',');
      var prefix = lines[i].substring(cut + 1);
      resolved[i] =
          prefix.isEmpty()
              ? lines[i]
              : lines[i].substring(0, cut + 1) + references.getOrDefault(prefix, prefix);
    }
    var csv =
        """
        FNB Trust Account Statement - Account 62400000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", resolved));
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  private String matchStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status FROM \"%s\".bank_statement_lines WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        lineId);
  }

  private UUID bankLineOf(UUID transactionId) {
    return jdbc.queryForObject(
        "SELECT bank_statement_line_id FROM \"%s\".trust_transactions WHERE id = ?"
            .formatted(books.schema()),
        UUID.class,
        transactionId);
  }

  /** Trust rows whose {@code bank_statement_line_id} is {@code lineId}. */
  private long rowsLinkedTo(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_transactions WHERE bank_statement_line_id = ?"
            .formatted(books.schema()),
        Long.class,
        lineId);
  }

  /** Lines matched (auto or manually) to {@code transactionId}. */
  private long linesMatchedTo(UUID transactionId) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM "%s".bank_statement_lines
        WHERE trust_transaction_id = ? AND match_status IN ('AUTO_MATCHED', 'MANUALLY_MATCHED')
        """
            .formatted(books.schema()),
        Long.class,
        transactionId);
  }

  private int matchedCount(UUID statementId) {
    return jdbc.queryForObject(
        "SELECT matched_count FROM \"%s\".bank_statements WHERE id = ?".formatted(books.schema()),
        Integer.class,
        statementId);
  }

  /** {@code matched_count} = the statement's matched and recorded lines. */
  private void assertMatchedCountAgreesWithLines(UUID statementId) {
    var resolved =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM "%s".bank_statement_lines WHERE bank_statement_id = ?
              AND match_status IN ('AUTO_MATCHED', 'MANUALLY_MATCHED', 'RECORDED')
            """
                .formatted(books.schema()),
            Integer.class,
            statementId);
    assertThat(matchedCount(statementId))
        .as("matched_count of statement %s agrees with its lines", statementId)
        .isEqualTo(resolved);
  }
}
