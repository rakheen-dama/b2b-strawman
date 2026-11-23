package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.InvestmentInterestResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 residual §1.3 (CF-3b class; CF-3 notes "residual risk"): the hypothesis that {@link
 * TrustInvestmentService#reverseInterest} — which locked "the net row, then the Fund-share row", a
 * fixed order that is not id order — deadlocks against an auto-match that plans both rows and locks
 * them in id order.
 *
 * <p><b>Not reproducible: a reversible entry has one row.</b> {@code reverseInterest} refuses an
 * entry recorded from a statement line before it locks any row; an entry with no line is a bank
 * advice, a bank advice is always {@code NET} ({@code GROSS} without a line is refused), and a
 * {@code NET} entry has no Fund-share row ({@code chk_invest_interest_fund_txn}). So {@code
 * reverseInterest} locks exactly one trust row, and with auto-match (which takes no investment,
 * entry, anchor or account row) it shares only that row: no cycle is possible. {@link
 * #aReversibleInterestEntryHasExactlyOneRow} pins the chain.
 *
 * <p>The two concurrency tests are the guard, with the house gate pattern ({@code
 * AutoMatchLockOrderIntegrationTest}): a gate connection outside the pool holds one row, and
 * blocking is observed in {@code pg_stat_activity} / {@code pg_blocking_pids}, never a sleep. Each
 * order of the two operations on the shared row ends without a 40P01, and the loser is refused or
 * skipped as it is when the two run one after the other.
 *
 * <p>One tenant, a fresh investment per test; the class ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReverseInterestAutoMatchLockOrderIntegrationTest {

  private static final String ORG_ID = "org_trust_reverse_interest_locks";
  private static final String REASON = "Bank advice captured twice";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Reverse Interest Locks Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_ril_owner", "ril_owner@test.com", "RIL Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_ril_approver",
                "ril_approver@test.com",
                "RIL Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * The hypothesis needs a reversible entry with two rows. There is none: a bank-advice entry is
   * NET with one row; GROSS from a bank advice is refused; a GROSS entry (two rows) comes only from
   * a statement line, and {@code reverseInterest} refuses a line-recorded entry before it locks a
   * row.
   */
  @Test
  void aReversibleInterestEntryHasExactlyOneRow() {
    var held = s86_4Investment("5000.00");
    var date = books.today().minusDays(10);

    var advice = recordNet(held, date, "100.00");
    assertThat(advice.presentation()).isEqualTo("NET");
    assertThat(advice.bankStatementLineId()).isNull();
    assertThat(advice.fundShareTransactionId()).as("a bank advice has no Fund-share row").isNull();

    books.as(
        ownerId,
        () ->
            assertThatThrownBy(
                    () ->
                        investmentService.recordInterest(
                            held.investmentId(),
                            new RecordInvestmentInterestRequest(
                                date,
                                new BigDecimal("100.00"),
                                null,
                                InterestPresentation.GROSS,
                                null)))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e ->
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "A gross interest credit is recorded from the investment account's"
                                    + " statement line (the Fund's 5% leaves in a separate"
                                    + " debit).")));

    var line = books.unmatchedLine(held.investmentAccount(), date, "400.00", "RIL-GROSS");
    var gross =
        books.recordInterest(
            held.investmentAccount(),
            new Expected(
                new BigDecimal("400.00"),
                BigDecimal.ZERO,
                new BigDecimal("380.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            held.investmentId(),
            new RecordInvestmentInterestRequest(
                date, new BigDecimal("400.00"), null, InterestPresentation.GROSS, line.lineId()));
    assertThat(gross.fundShareTransactionId()).as("a gross entry has two rows").isNotNull();
    books.as(
        ownerId,
        () ->
            assertThatThrownBy(() -> reverseInterest(gross.id()))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e ->
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "This interest was recorded from a bank statement line. Unmatch"
                                    + " the line to reverse it.")));
    assertThat(rowStatus(gross.netTransactionId())).isEqualTo("RECORDED");
    assertThat(rowStatus(gross.fundShareTransactionId())).isEqualTo("RECORDED");
  }

  /**
   * Auto-match first: it holds the statement's line and waits (on the gate) for the entry's row;
   * the reversal, holding the investment and the entry, then waits for the same row. Released,
   * auto-match links the row; the reversal reads the row fresh under its lock and is refused as a
   * sequential one is (the row is matched to a line). No 40P01.
   */
  @Test
  void autoMatchHoldingTheLine_thenReverseInterestOfItsRow_noDeadlock() throws Exception {
    var held = s86_4Investment("3000.00");
    var date = books.today().minusDays(10);
    var entry = recordNet(held, date, "100.00");
    var netRow = entry.netTransactionId();
    var line = books.unmatchedLine(held.investmentAccount(), date, "95.00", referenceOf(netRow));

    var outcome =
        gatedOnRow(
            "trust_transactions",
            netRow,
            () -> reconciliationService.autoMatchStatement(line.statementId()),
            () -> reverseInterest(entry.id()));

    assertThat(outcome.secondBlocked()).as("the reversal blocked behind the gate").isTrue();
    assertThat(outcome.first()).as("auto-match (no 40P01)").isNull();
    assertThat(outcome.second())
        .as("refused on the row's state (not a deadlock or lock failure)")
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "This interest is matched to a bank statement line. Unmatch the line before"
                          + " reversing it.");
            });
    assertThat(lineStatus(line.lineId())).isEqualTo("AUTO_MATCHED");
    assertThat(bankLineOf(netRow)).isEqualTo(line.lineId());
    assertThat(rowStatus(netRow)).isEqualTo("RECORDED");
    assertThat(entryStatus(entry.id())).isEqualTo("POSTED");
  }

  /**
   * The reversal first: it holds the investment, the entry and the row, and waits (on the gate) for
   * the investment account's posting lock; auto-match, holding the line, then waits for the row.
   * Released, the reversal posts its contra; auto-match reads the row fresh under its lock, sees it
   * REVERSED and skips it (the line stays unmatched). No 40P01.
   */
  @Test
  void reverseInterestHoldingTheRow_thenAutoMatchOfIt_noDeadlock() throws Exception {
    var held = s86_4Investment("4000.00");
    var date = books.today().minusDays(10);
    var entry = recordNet(held, date, "100.00");
    var netRow = entry.netTransactionId();
    var line = books.unmatchedLine(held.investmentAccount(), date, "95.00", referenceOf(netRow));

    var outcome =
        gatedOnRow(
            "trust_accounts",
            held.investmentAccount(),
            () -> reverseInterest(entry.id()),
            () -> reconciliationService.autoMatchStatement(line.statementId()));

    assertThat(outcome.secondBlocked()).as("auto-match blocked behind the gate").isTrue();
    assertThat(outcome.first()).as("the reversal (no 40P01)").isNull();
    assertThat(outcome.second()).as("auto-match (no 40P01)").isNull();
    assertThat(entryStatus(entry.id())).isEqualTo("REVERSED");
    assertThat(rowStatus(netRow)).isEqualTo("REVERSED");
    assertThat(bankLineOf(netRow)).as("the reversed row was not linked").isNull();
    assertThat(lineStatus(line.lineId())).isEqualTo("UNMATCHED");
  }

  // --- helpers -----------------------------------------------------------------------------------

  private record Held(UUID funding, UUID investmentAccount, Client client, UUID investmentId) {}

  /** A section 86(4) investment of {@code principal}, placed and approved. */
  private Held s86_4Investment(String principal) {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("reverse-interest locks");
    books.deposit(funding, client, principal);
    var placed = books.requestPlacement(funding, investmentAccount, client, principal);
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-" + principal),
        investmentAccount,
        Expected.cashbookAndInvestment(principal),
        placed.depositTransactionId(),
        approverId);
    return new Held(funding, investmentAccount, client, placed.id());
  }

  /** A NET bank-advice entry of {@code gross} (the client's 95% posts). */
  private InvestmentInterestResponse recordNet(Held held, LocalDate date, String gross) {
    var net = new BigDecimal(gross).multiply(new BigDecimal("0.95"));
    return books.recordInterest(
        held.investmentAccount(),
        Expected.cashbookAndInvestment(net.setScale(2).toPlainString()),
        held.investmentId(),
        new RecordInvestmentInterestRequest(
            date, new BigDecimal(gross), null, InterestPresentation.NET, null));
  }

  private void reverseInterest(UUID interestId) {
    investmentService.reverseInterest(
        interestId, new TrustInvestmentService.ReverseInterestRequest(REASON));
  }

  private String referenceOf(UUID transactionId) {
    return jdbc.queryForObject(
        "SELECT reference FROM \"%s\".trust_transactions WHERE id = ?".formatted(books.schema()),
        String.class,
        transactionId);
  }

  private String rowStatus(UUID transactionId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".trust_transactions WHERE id = ?".formatted(books.schema()),
        String.class,
        transactionId);
  }

  private UUID bankLineOf(UUID transactionId) {
    return jdbc.queryForObject(
        "SELECT bank_statement_line_id FROM \"%s\".trust_transactions WHERE id = ?"
            .formatted(books.schema()),
        UUID.class,
        transactionId);
  }

  private String lineStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status FROM \"%s\".bank_statement_lines WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        lineId);
  }

  private String entryStatus(UUID interestId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".trust_investment_interest WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        interestId);
  }

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
   * and joins both (the house pattern, {@code AutoMatchLockOrderIntegrationTest}).
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
      books.as(ownerId, action);
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
   * on the gate backend {@code gatePid} (true), or {@code finished} is set (false).
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
}
