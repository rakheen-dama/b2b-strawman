package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import javax.sql.DataSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR #1757 security review (MEDIUM, race / TOCTOU), 648A: an investment withdrawal and a reversal
 * of its placement each check the other's effect. Both paths lock the placement row first (the
 * withdrawal then locks the investment row), so they serialise. Since 648A the placement ({@code
 * INV_PLACE}) is refused reversal on its type (registry {@code reversal = NONE}, replacing
 * G89-F86's investment lookup) — under the placement row lock, which a reversal therefore waits for
 * while a withdrawal holds it.
 *
 * <p>Each test holds one operation's transaction open (latch) after it has done its work, starts
 * the other operation on a second thread, waits until it is either blocked by the first's backend
 * (observed in {@code pg_stat_activity}, not a sleep) or finished, then commits the first. In both
 * orders the reversal is refused, the withdrawal succeeds, and the client is credited exactly once.
 *
 * <p>650A F-10 / F-11 (the global lock order, {@code TrustPostingService}): a placement request
 * locks the client's anchor, then the trust account, then the investment account; a withdrawal and
 * a client's posting must take the same locks in the same order, or the two deadlock (40P01). Each
 * lock-order test holds the trust account row on a gate connection, lets the placement block on it,
 * starts the other operation and lets it block too (behind the placement or the gate), then
 * releases the gate: in the reverse order the two would each hold a lock the other waits for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentWithdrawReverseRaceIntegrationTest {

  private static final String ORG_ID = "org_trust_investment_withdraw_reverse_race";
  private static final String OWNER_SUB = "user_tiwr_owner";
  private static final String APPROVER_SUB = "user_tiwr_approver";
  private static final String PLACEMENT_NOT_REVERSIBLE =
      "This entry placed a trust investment. Withdraw the investment instead of reversing its"
          + " placement.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustLedgerQueryRepository trustLedgerQueryRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;

  private String tenantSchema;
  private UUID ownerId;
  private UUID trustAccountId;
  private UUID investmentAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Withdraw Reverse Race Org", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "tiwr_owner@test.com", "TIWR Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "tiwr_approver@test.com", "TIWR Approver", "owner");
    today = LocalDate.now(clock);
    trustAccountId =
        as(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                      return trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Withdraw Reverse Race Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-TIWR",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2025, 1, 1),
                                  "PR #1757 race tests"))
                          .getId();
                    }));
    investmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
            "Withdraw Reverse Race s86(4)",
            "6200-000-TIWR-I",
            InvestmentBasis.CLIENT_INSTRUCTION);
  }

  /**
   * The reversal attempted first is refused on the type at once (and releases its placement lock);
   * the withdrawal then succeeds. Exactly one credit.
   */
  @Test
  void reversalAttemptFirst_thenWithdrawal_reversalRefused_withdrawalSucceeds_creditedOnce()
      throws Exception {
    Placed placed = placeAndApprove("Reverse First");

    var outcome =
        race(
            () ->
                transactionService.reverseTransaction(
                    placed.placementId(), new ReverseRequest("Placed in error", null)),
            () -> withdraw(placed));

    assertThat(outcome.first())
        .as("the reversal of an investment's placement (648A: refused on the type)")
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot reverse transaction")
        .hasMessageContaining(PLACEMENT_NOT_REVERSIBLE);
    assertThat(outcome.second()).as("the withdrawal").isNull();
    assertCreditedOnce(placed);
  }

  @Test
  void withdrawalInFlight_thenReversal_onlyTheWithdrawalSucceeds_creditedOnce() throws Exception {
    Placed placed = placeAndApprove("Withdraw First");

    var outcome =
        race(
            () -> withdraw(placed),
            () ->
                transactionService.reverseTransaction(
                    placed.placementId(), new ReverseRequest("Placed in error", null)));

    assertThat(outcome.first()).as("the withdrawal (committed first)").isNull();
    // The lock order is still exercised: the reversal waited for the placement row lock the
    // withdrawal held (its refusal on the type is read under that lock), it did not run ahead
    assertThat(outcome.secondBlockedOnFirst())
        .as("the reversal blocked on a trust_transactions row lock the withdrawal held")
        .isTrue();
    assertThat(outcome.secondFinishedBeforeCommit())
        .as("the reversal finished while the withdrawal still held the placement row lock")
        .isFalse();
    assertThat(outcome.second())
        .as("the reversal, which waited for the placement row lock")
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot reverse transaction")
        .hasMessageContaining(PLACEMENT_NOT_REVERSIBLE);
    assertCreditedOnce(placed);
  }

  /**
   * 650A F-10: a placement from the trust account into the investment account and a withdrawal of
   * another investment back the other way. Both take the trust account before the investment
   * account — the withdrawal locks both before its first leg posts — so the withdrawal waits for
   * the placement instead of holding the investment account the placement needs.
   */
  @Test
  void lockOrder_placementAndWithdrawalOnTheSameAccounts_bothComplete_noDeadlock()
      throws Exception {
    Placed held = placeAndApprove("Lock Order Withdraw");
    UUID client = newClient("Lock Order Place Client");
    UUID matter = newMatter(client, "Lock Order Place Matter");
    deposit(client, matter);

    var outcome =
        gatedOnTrustAccount(
            () -> investmentService.placeInvestment(trustAccountId, placement(client, matter)),
            () -> withdraw(held));

    assertThat(outcome.secondBlocked()).as("both blocked before the gate opened").isTrue();
    assertThat(outcome.first()).as("the placement").isNull();
    assertThat(outcome.second()).as("the withdrawal").isNull();
    assertCreditedOnce(held);
    assertThat(clientBalance(client))
        .as("the placement awaits approval")
        .isEqualByComparingTo("5000.00");
  }

  /**
   * 650A F-11: a section 86(4) placement and a deposit for the same client on the trust account.
   * The placement takes the client's anchor before the account rows, as the deposit does (anchor,
   * then the account's posting lock), so the deposit waits for the placement instead of holding the
   * anchor the placement needs.
   */
  @Test
  void lockOrder_placementAndAPostingForTheSameClient_bothComplete_noDeadlock() throws Exception {
    UUID client = newClient("Lock Order Same Client");
    UUID matter = newMatter(client, "Lock Order Same Matter");
    deposit(client, matter);

    var outcome =
        gatedOnTrustAccount(
            () -> investmentService.placeInvestment(trustAccountId, placement(client, matter)),
            () ->
                transactionService.recordDeposit(
                    trustAccountId,
                    new RecordDepositRequest(
                        client,
                        matter,
                        new BigDecimal("100.00"),
                        "TIWR-LO-DEP-" + (++counter),
                        "Deposit",
                        today)));

    assertThat(outcome.secondBlocked()).as("both blocked before the gate opened").isTrue();
    assertThat(outcome.first()).as("the placement").isNull();
    assertThat(outcome.second()).as("the deposit").isNull();
    assertThat(clientBalance(client))
        .as("the deposit posted; the placement awaits approval")
        .isEqualByComparingTo("5100.00");
  }

  private TrustInvestmentService.PlaceInvestmentRequest placement(UUID client, UUID matter) {
    return new TrustInvestmentService.PlaceInvestmentRequest(
        investmentAccountId,
        client,
        matter,
        "Test Bank",
        "TIWR-LO-" + (++counter),
        new BigDecimal("1000.00"),
        new BigDecimal("0.07"),
        today.minusDays(4),
        null,
        null,
        InvestmentBasis.CLIENT_INSTRUCTION);
  }

  private TrustInvestmentService.TrustInvestmentResponse withdraw(Placed placed) {
    return investmentService.withdrawInvestment(
        placed.investmentId(), new TrustInvestmentService.WithdrawInvestmentRequest(today));
  }

  // --- race harness ---

  /**
   * Each side's failure (null = succeeded); whether {@code second} was observed blocked by {@code
   * first}'s backend on a trust_transactions query; and whether {@code second} had already finished
   * when {@code first} was released to commit (false = it was blocked on a lock {@code first}
   * held).
   */
  private record Outcome(
      Throwable first,
      Throwable second,
      boolean secondBlockedOnFirst,
      boolean secondFinishedBeforeCommit) {}

  /**
   * Runs {@code first} in a transaction that stays open after it returns, starts {@code second} on
   * another thread, waits (bounded) until it is either blocked by {@code first}'s backend or
   * finished, then commits {@code first}. If {@code first} failed its transaction is already rolled
   * back and holds nothing, so there is nothing to wait for. Returns each side's failure (null =
   * succeeded).
   */
  private Outcome race(Supplier<?> first, Supplier<?> second) throws Exception {
    var firstDone = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    var firstBackendPid = new AtomicReference<Integer>();

    Thread firstThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    as(
                        () ->
                            transactionTemplate.execute(
                                tx -> {
                                  first.get();
                                  firstBackendPid.set(
                                      ((Number)
                                              entityManager
                                                  .createNativeQuery("SELECT pg_backend_pid()")
                                                  .getSingleResult())
                                          .intValue());
                                  firstDone.countDown();
                                  await(releaseFirst);
                                  return null;
                                }));
                  } catch (Throwable t) {
                    firstError.set(t);
                    firstDone.countDown();
                  }
                });
    assertThat(firstDone.await(30, TimeUnit.SECONDS)).isTrue();

    Thread secondThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    as(second);
                  } catch (Throwable t) {
                    secondError.set(t);
                  } finally {
                    secondFinished.set(true);
                  }
                });
    boolean secondBlockedOnFirst;
    boolean secondFinishedBeforeCommit;
    try {
      secondBlockedOnFirst =
          firstError.get() == null
              && awaitBlockedOnTrustTransactionLockHeldByOrFinished(
                  firstBackendPid.get(), secondFinished);
      secondFinishedBeforeCommit = secondFinished.get();
    } finally {
      releaseFirst.countDown();
      join(firstThread);
      join(secondThread);
    }
    return new Outcome(
        firstError.get(), secondError.get(), secondBlockedOnFirst, secondFinishedBeforeCommit);
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} is running a
   * trust_transactions query (true — the placement row lock is contested) or the waiter finishes
   * without having been seen blocked (false). Fails if neither happens.
   */
  private boolean awaitBlockedOnTrustTransactionLockHeldByOrFinished(
      int holderPid, AtomicBoolean waiterFinished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE '%trust_transactions%'",
              Long.class, holderPid);
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (waiterFinished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the second operation neither blocked nor finished within 30 s");
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

  /**
   * R5 000 deposited, R1 000 placed and approved, then exactly one R1 000 credit back to the
   * placement's matter, and the investment account emptied.
   */
  private void assertCreditedOnce(Placed placed) {
    assertThat(clientBalance(placed.client())).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("5000.00");
    assertThat(
            as(
                () ->
                    trustLedgerQueryRepository.investmentBalance(
                        placed.investmentId(), investmentAccountId)))
        .isEqualByComparingTo("0");
  }

  /** Each side's failure (null = succeeded); whether {@code second} was seen blocked. */
  private record Gated(Throwable first, Throwable second, boolean secondBlocked) {}

  /**
   * Holds the trust account row {@code FOR NO KEY UPDATE} (the lock a placement request takes) on a
   * gate connection outside the pool, starts {@code first} and waits until it is blocked by the
   * gate, starts {@code second} and waits until it is blocked too (behind {@code first} or the
   * gate) — observed in {@code pg_stat_activity}, not a sleep — then releases the gate. {@code
   * first} is then first in line for the account row; a lock order that let {@code second} hold a
   * lock {@code first} needs next would deadlock here (Postgres aborts one with 40P01). Returns
   * each side's failure.
   */
  private Gated gatedOnTrustAccount(Supplier<?> first, Supplier<?> second) throws Exception {
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    Thread firstThread = null;
    Thread secondThread = null;
    boolean secondBlocked;
    // Outside the pool (3 connections: the two operations and the pg_stat_activity polls)
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
              "SELECT id FROM \""
                  + tenantSchema
                  + "\".trust_accounts WHERE id = ? FOR NO KEY UPDATE")) {
        ps.setObject(1, trustAccountId);
        ps.executeQuery().close();
      }
      try {
        firstThread = Thread.ofPlatform().start(() -> capture(first, firstError, null));
        assertThat(awaitWaiters(1, gatePid, null)).as("the first blocked on the gate").isTrue();
        secondThread =
            Thread.ofPlatform().start(() -> capture(second, secondError, secondFinished));
        secondBlocked = awaitWaiters(2, null, secondFinished);
      } finally {
        gate.rollback();
      }
    } finally {
      if (firstThread != null) {
        join(firstThread);
      }
      if (secondThread != null) {
        join(secondThread);
      }
    }
    return new Gated(firstError.get(), secondError.get(), secondBlocked);
  }

  private void capture(Supplier<?> action, AtomicReference<Throwable> error, AtomicBoolean done) {
    try {
      as(action);
    } catch (Throwable t) {
      error.set(t);
    } finally {
      if (done != null) {
        done.set(true);
      }
    }
  }

  /**
   * Waits (bounded, 30 s) until {@code count} backends are blocked on a trust account or client
   * anchor query — by {@code holderPid} when given — (true) or {@code finished} is set (false).
   */
  private boolean awaitWaiters(int count, Integer holderPid, AtomicBoolean finished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid)) > 0"
                  + " AND (CAST(? AS int) IS NULL OR CAST(? AS int) = ANY(pg_blocking_pids(pid)))"
                  + " AND (query ILIKE '%trust_accounts%' OR query ILIKE '%client_ledger_cards%')",
              Long.class, holderPid, holderPid);
      if (waiting != null && waiting >= count) {
        return true;
      }
      if (finished != null && finished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError(count + " operation(s) not blocked on a lock within 30 s");
  }

  // --- fixtures ---

  private record Placed(UUID client, UUID matter, UUID investmentId, UUID placementId) {}

  /** R5 000 deposited for the client on the matter, five days ago. */
  private void deposit(UUID client, UUID matter) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId": "%s", "matterId": "%s", "amount": 5000.00,
                     "reference": "TIWR-DEP-%d", "transactionDate": "%s"}
                    """
                        .formatted(client, matter, ++counter, today.minusDays(5))))
        .andExpect(status().isCreated());
  }

  private Placed placeAndApprove(String name) throws Exception {
    UUID client = newClient(name + " Client");
    UUID matter = newMatter(client, name + " Matter");
    deposit(client, matter);
    String placed =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"investmentAccountId": "%s", "clientId": "%s", "matterId": "%s",
                         "institution": "Test Bank", "accountNumber": "TIWR-%d",
                         "principal": 1000.00, "interestRate": 0.07, "depositDate": "%s",
                         "investmentBasis": "CLIENT_INSTRUCTION"}
                        """
                            .formatted(
                                investmentAccountId,
                                client,
                                matter,
                                ++counter,
                                today.minusDays(4))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID placementId = UUID.fromString(JsonPath.read(placed, "$.depositTransactionId"));
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());
    assertThat(clientBalance(client)).isEqualByComparingTo("4000.00");
    return new Placed(client, matter, UUID.fromString(JsonPath.read(placed, "$.id")), placementId);
  }

  private UUID newClient(String name) {
    int n = ++counter;
    return as(
        () ->
            transactionTemplate.execute(
                tx ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                name, "tiwr_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private UUID newMatter(UUID client, String name) {
    return as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var project = new Project(name, "PR #1757", ownerId);
                  project.setCustomerId(client);
                  return projectRepository.saveAndFlush(project).getId();
                }));
  }

  private BigDecimal matterBalance(UUID matter) {
    var balance = as(() -> trustLedgerQueryRepository.matterBalance(matter));
    return balance == null ? BigDecimal.ZERO : balance;
  }

  private BigDecimal clientBalance(UUID client) {
    return as(() -> trustLedgerQueryRepository.clientBalance(trustAccountId, client, null));
  }

  private <T> T as(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .call(action::get);
  }
}
