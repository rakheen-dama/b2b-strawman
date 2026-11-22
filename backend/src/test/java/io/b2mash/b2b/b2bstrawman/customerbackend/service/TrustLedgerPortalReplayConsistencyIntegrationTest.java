package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustTransactionView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 648D F-4 / F-5: a matter's portal trust replay ({@code
 * TrustLedgerPortalSyncService.recomputeMatter}) commits its rows and its balance snapshot together
 * (F-4), and two replays of one matter run one after the other, the later one reading the newer
 * history (F-5). Both drive the real path: a deposit recorded through {@link
 * TrustTransactionService} publishes its event, and the {@code AFTER_COMMIT} listener replays the
 * matter on the committing thread. The portal repository is a spy only to inject a failure (F-4) or
 * to park one replay between its history read and its balance write (F-5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustLedgerPortalReplayConsistencyIntegrationTest {

  private static final String ORG_ID = "org_portal_trust_replay_consistency";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerService customerService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private TrustPostingService trustPostingService;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private PortalTrustReadModelRepository portalTrustRepo;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID trustAccountId;
  private TransactionTemplate tenantTx;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Replay Consistency Org", "legal-za");
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_replay"));
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    tenantTx = new TransactionTemplate(transactionManager);
    inTenant(
        () ->
            tenantTx.executeWithoutResult(
                status -> {
                  customerId =
                      customerService
                          .createCustomer(
                              "Replay Client", "replay-client@test.com", null, null, null, memberId)
                          .getId();
                  trustAccountId =
                      trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Replay Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-RPLY",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2026, 1, 1),
                                  "Portal replay consistency tests"))
                          .getId();
                }));
  }

  // ==========================================================================
  // F-4: the rows and the balance of one replay commit or roll back together
  // ==========================================================================

  @Test
  void aBalanceWriteThatFailsLeavesNoRowOfTheSameReplayBehind() {
    UUID matter = newMatter("F-4 matter");
    UUID first = deposit(matter, "100.00", "F4-DEP-1", LocalDate.of(2026, 3, 1));
    assertThat(portalBalance(matter)).isEqualByComparingTo("100.00");

    // The next replay writes every row, then its balance write fails.
    doThrow(new IllegalStateException("injected portal balance failure"))
        .when(portalTrustRepo)
        .upsertBalance(eq(customerId), eq(matter), any(), any(), any());
    UUID second = deposit(matter, "50.00", "F4-DEP-2", LocalDate.of(2026, 3, 2));

    // Nothing of the failed replay is committed: the portal still shows the first deposit only,
    // and its latest row's running balance agrees with the stored balance.
    var rows = portalRows(matter);
    assertThat(rows)
        .as("the failed replay's new row was rolled back with its balance")
        .extracting(PortalTrustTransactionView::id)
        .containsExactly(first)
        .doesNotContain(second);
    assertThat(rows.getFirst().runningBalance()).isEqualByComparingTo(portalBalance(matter));
    assertThat(portalBalance(matter)).isEqualByComparingTo("100.00");
  }

  // ==========================================================================
  // F-5: two replays of one matter — the later one reads the newer history and writes last
  // ==========================================================================

  @Test
  void aReplayThatReadOlderHistoryNeverOverwritesANewerBalance() throws Exception {
    UUID matter = newMatter("F-5 matter");
    var parkedThread = new AtomicReference<Thread>();
    var parked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    // Replay A (the first deposit's) parks at its first portal write — after it has read the
    // history (only its own deposit), before it has written anything. Parking before any write
    // matters: a portal row A had already written would row-lock B out and hide the race.
    doAnswer(
            invocation -> {
              if (Thread.currentThread() == parkedThread.get() && parked.getCount() > 0) {
                parked.countDown();
                assertThat(release.await(60, TimeUnit.SECONDS)).as("released").isTrue();
              }
              return invocation.callRealMethod();
            })
        .when(portalTrustRepo)
        .upsertTransaction(
            any(),
            eq(customerId),
            eq(matter),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());

    var errorA = new AtomicReference<Throwable>();
    var errorB = new AtomicReference<Throwable>();
    var finishedB = new AtomicBoolean(false);
    Thread replayA =
        Thread.ofPlatform()
            .unstarted(
                () ->
                    capture(
                        () -> deposit(matter, "100.00", "F5-DEP-1", LocalDate.of(2026, 3, 1)),
                        errorA,
                        null));
    parkedThread.set(replayA);
    replayA.start();
    Thread replayB = null;
    boolean bWaitedOnTheLock;
    try {
      assertThat(parked.await(30, TimeUnit.SECONDS)).as("replay A parked").isTrue();
      // Replay B (the second deposit's) reads the history that includes both deposits.
      replayB =
          Thread.ofPlatform()
              .start(
                  () ->
                      capture(
                          () -> deposit(matter, "50.00", "F5-DEP-2", LocalDate.of(2026, 3, 2)),
                          errorB,
                          finishedB));
      bWaitedOnTheLock = awaitAdvisoryWaitOrFinished(finishedB);
    } finally {
      release.countDown();
      replayA.join(TimeUnit.SECONDS.toMillis(60));
      if (replayB != null) {
        replayB.join(TimeUnit.SECONDS.toMillis(60));
      }
    }
    assertThat(replayA.isAlive()).as("replay A finished").isFalse();
    assertThat(replayB.isAlive()).as("replay B finished").isFalse();
    assertThat(errorA.get()).as("deposit A").isNull();
    assertThat(errorB.get()).as("deposit B").isNull();

    // Both deposits are on the ledger: the portal balance must be 150, and agree with the latest
    // row. Without the per-matter lock B commits 150 and A then overwrites it with its older 100.
    var rows = portalRows(matter);
    assertThat(rows).hasSize(2);
    assertThat(portalBalance(matter))
        .as(
            "the replay that read the newer history wrote last (B waited on the lock: %s)",
            bWaitedOnTheLock)
        .isEqualByComparingTo("150.00");
    assertThat(rows.getFirst().runningBalance()).isEqualByComparingTo("150.00");
    // The interleaving really happened: B queued behind A's matter lock rather than finishing.
    assertThat(bWaitedOnTheLock).as("replay B waited on replay A's matter lock").isTrue();
  }

  // ==========================================================================
  // F-5 (read side): the replay reads committed rows, not the committed transaction's managed
  // entities — the AFTER_COMMIT replay runs on that transaction's still-open persistence context
  // ==========================================================================

  @Test
  void aReplayReadsARowItsTransactionHeldUnpostedAsPostedOnceAnotherTransactionPostedIt() {
    UUID matter = newMatter("Stale-read matter");
    UUID deposit = deposit(matter, "100.00", "SR-DEP-1", LocalDate.of(2026, 3, 1));
    UUID payment = awaitingPayment(matter, "30.00", "SR-PAY-1", LocalDate.of(2026, 3, 2));
    var requiresNew = new TransactionTemplate(transactionManager);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    inTenant(
        () ->
            tenantTx.executeWithoutResult(
                status -> {
                  // This transaction loads the payment while it is unposted: it is now a managed
                  // entity in this persistence context, effect 0.
                  var held = trustTransactionRepository.findById(payment).orElseThrow();
                  assertThat(held.getStatus()).isEqualTo("AWAITING_APPROVAL");
                  // Another transaction (its own persistence context) posts it and commits.
                  requiresNew.executeWithoutResult(
                      inner ->
                          trustPostingService.postOnApproval(
                              trustTransactionRepository.findById(payment).orElseThrow(),
                              memberId));
                  // A replay of the matter is triggered by this transaction; it runs AFTER_COMMIT
                  // on this thread, against this transaction's still-open persistence context.
                  eventPublisher.publishEvent(
                      TrustTransactionRecordedEvent.recorded(
                          deposit,
                          trustAccountId,
                          matter,
                          "DEPOSIT",
                          new BigDecimal("100.00"),
                          LocalDate.of(2026, 3, 1),
                          customerId,
                          memberId,
                          tenantSchema,
                          ORG_ID));
                }));

    // The replay saw the payment as committed (APPROVED, effect -30), not as the stale managed
    // entity (AWAITING_APPROVAL, effect 0, filtered out).
    assertThat(portalRows(matter))
        .as("the posted payment reached the portal")
        .extracting(PortalTrustTransactionView::id)
        .containsExactly(payment, deposit);
    assertThat(portalBalance(matter)).isEqualByComparingTo("70.00");
    assertThat(portalRows(matter).getFirst().runningBalance()).isEqualByComparingTo("70.00");
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /** Records a payment awaiting approval (unposted, no event) in its own committed transaction. */
  private UUID awaitingPayment(UUID matter, String amount, String reference, LocalDate date) {
    var holder = new UUID[1];
    inTenant(
        () ->
            tenantTx.executeWithoutResult(
                status -> {
                  var draft =
                      new TrustTransaction(
                          trustAccountId,
                          "PAYMENT",
                          new BigDecimal(amount),
                          customerId,
                          matter,
                          null,
                          reference,
                          "Replay payment " + reference,
                          date,
                          "AWAITING_APPROVAL",
                          memberId);
                  holder[0] = trustPostingService.recordAwaitingApproval(draft).getId();
                }));
    return holder[0];
  }

  private void capture(Runnable action, AtomicReference<Throwable> error, AtomicBoolean done) {
    try {
      action.run();
    } catch (Throwable t) {
      error.set(t);
    } finally {
      if (done != null) {
        done.set(true);
      }
    }
  }

  /**
   * Waits (bounded, 30 s) until a backend waits on the portal matter-replay advisory lock (true —
   * replay B queued behind replay A's matter lock) or {@code finished} is set (false — B ran to
   * completion without waiting). Observed in {@code pg_stat_activity}, never a sleep; only a
   * backend whose waiting statement is the matter-replay lock counts, so an unrelated advisory
   * waiter cannot release A early.
   */
  private boolean awaitAdvisoryWaitOrFinished(AtomicBoolean finished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              """
              SELECT count(*) FROM pg_stat_activity
              WHERE wait_event_type = 'Lock' AND wait_event = 'advisory'
                AND query LIKE '%portal_trust_matter%'
              """,
              Long.class);
      if (waiting != null && waiting > 0) {
        return true;
      }
      if (finished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("replay B neither waited on the matter lock nor finished in 30 s");
  }

  private UUID newMatter(String name) {
    var holder = new UUID[1];
    inTenant(
        () ->
            tenantTx.executeWithoutResult(
                status -> {
                  var project = new Project(name, "Portal replay consistency", memberId);
                  project.setCustomerId(customerId);
                  holder[0] = projectRepository.saveAndFlush(project).getId();
                }));
    return holder[0];
  }

  /** Records a deposit through the service; its replay runs on this thread after the commit. */
  private UUID deposit(UUID matter, String amount, String reference, LocalDate date) {
    var holder = new UUID[1];
    inTenant(
        () ->
            tenantTx.executeWithoutResult(
                status ->
                    holder[0] =
                        trustTransactionService
                            .recordDeposit(
                                trustAccountId,
                                new RecordDepositRequest(
                                    customerId,
                                    matter,
                                    new BigDecimal(amount),
                                    reference,
                                    "Replay deposit " + reference,
                                    date))
                            .id()));
    return holder[0];
  }

  private BigDecimal portalBalance(UUID matter) {
    return portalTrustRepo.findBalance(customerId, matter).orElseThrow().currentBalance();
  }

  /** The matter's portal rows, newest first (the portal read order). */
  private List<PortalTrustTransactionView> portalRows(UUID matter) {
    return portalTrustRepo.findTransactions(customerId, matter, null, null, 50, 0);
  }

  private void inTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(action);
  }
}
