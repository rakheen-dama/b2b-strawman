package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.CustomerContextBuilder;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementOfAccountContextBuilder;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustSnapshotReads;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 FU-3 (648F R-1, 650A F-6, 652D F-652D-CR1; founder ruling R-1 option a): every trust
 * figure on one client document or trust report comes from one database snapshot, so a trust
 * posting that commits while the document is being read is either wholly on it or wholly off it.
 *
 * <p>Each race test arms a one-shot hook on a trust reader (a spy on {@link ClientLedgerService} /
 * {@link TrustIntegrityService}): at the armed read, a concurrent posting — a placement approval,
 * an investment return, a deposit — runs on another thread and commits before the read goes on. The
 * posting moves money between two of the figures the document reads separately, so a document that
 * read the two on different snapshots disagrees with itself (the placement counted twice in {@code
 * total_held}; an account chosen as holding nothing). The invariants asserted hold on either side
 * of the posting; only a torn read breaks them.
 *
 * <p>The three document tests failed before FU-3 (qa_cycle/evidence/phase92-followups/FU-3/
 * red-run.txt). The two report tests passed before it too: every report already runs in one
 * read-only REPEATABLE_READ transaction opened by {@code ReportExportService} (650C D-38), so they
 * are guards that keep it so.
 *
 * <p>One harness tenant, a fresh funding account per test; the class ends with {@code
 * assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustSnapshotReadsIntegrationTest {

  private static final String ORG_ID = "org_trust_snapshot_reads_test";
  private static final String OWNER = "user_tsr_owner";
  private static final String APPROVER = "user_tsr_approver";
  private static final Set<String> TRUST_READER =
      Set.of("VIEW_TRUST", "MANAGE_TRUST", "VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
  private static final Locale ZA = VariableFormatter.resolveLocale("ZAR");
  private static final LocalDate MARCH_1 = TrustBooksFixture.POSTING_DATE;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private CustomerContextBuilder customerContextBuilder;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private TrustSnapshotReads snapshotReads;
  @Autowired private EntityManager entityManager;

  @MockitoSpyBean private ClientLedgerService clientLedgerService;
  @MockitoSpyBean private TrustIntegrityService integrityService;

  /** The armed concurrent posting and where it may fire; null = disarmed. */
  private final AtomicReference<Armed> armed = new AtomicReference<>();

  private record Armed(Predicate<StackWalker> where, Runnable posting) {}

  private String schema;
  private UUID ownerId;
  private UUID approverId;
  private TrustBooksFixture.Tenant books;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Snapshot Reads Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tsr_owner@test.com", "TSR Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER, "tsr_approver@test.com", "TSR Approver", "admin"));
    // The statement endpoint needs `disbursements`, the trust blocks `trust_accounting`
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("disbursements", "trust_accounting"));
                      settings.updateCurrency("ZAR");
                      orgSettingsRepository.save(settings);
                    }));
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  /** The spies are reset after every test: re-install the hooks (inert until armed). */
  @BeforeEach
  void installHooks() {
    armed.set(null);
    doAnswer(
            inv -> {
              fireIfArmed();
              return inv.callRealMethod();
            })
        .when(clientLedgerService)
        .getClientInvestedStatement(any(), any(), any(), any());
    doAnswer(
            inv -> {
              fireIfArmed();
              return inv.callRealMethod();
            })
        .when(clientLedgerService)
        .getClientInvestedBalanceAsOfDate(any(), any(), any());
    // After the position is read, before the creditor lines are (TrustMonthlyListService.liveList)
    doAnswer(
            inv -> {
              Object report = inv.callRealMethod();
              fireIfArmed();
              return report;
            })
        .when(integrityService)
        .check(any(), any(), any());
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- documents ---------------------------------------------------------------------------

  /**
   * Statement of Account: the context {@code StatementService.generate} renders and stores, built
   * inside a writing transaction (that method's propagation and isolation). A placement approved
   * between the general and the invested read must not leave the R30 000 in both the trust closing
   * balance and the invested balance ({@code total_held} R80 000).
   */
  @Test
  @SuppressWarnings("unchecked")
  void statementOfAccount_placementApprovedBetweenTheReads_storedStatementCountsItOnce()
      throws Exception {
    var s = pendingPlacement();
    armAt(anywhere(), () -> approve(s));

    Map<String, Object> context =
        asOwner(
            () ->
                transactionTemplate.execute(
                    tx ->
                        statementContextBuilder.build(
                            s.client().matter(), MARCH_1, books.today())));

    assertFired();
    var trust = (Map<String, Object>) context.get("trust");
    BigDecimal general = amount(trust.get("closing_balance"));
    var invested = (Map<String, Object>) trust.get("invested");
    BigDecimal investedClosing =
        invested == null ? BigDecimal.ZERO : amount(invested.get("closing_balance"));
    assertThat(general.add(investedClosing))
        .as("general %s + invested %s: R50 000 is all the client has", general, investedClosing)
        .isEqualByComparingTo("50000.00");
    if (invested != null) {
      assertThat(amount(invested.get("total_held"))).isEqualByComparingTo("50000.00");
    }
  }

  /**
   * Client Trust Statement through {@code GeneratedDocumentService.generateDocument} (the writing
   * generate transaction): the stored PDF's HTML — the context {@code generatePdf} builds — reads
   * the general and invested balances from one snapshot.
   */
  @Test
  void clientTrustStatement_placementApprovedBetweenTheReads_storedDocumentCountsItOnce()
      throws Exception {
    var s = pendingPlacement();
    armAt(calledFrom(PdfRenderingService.class, "generatePdf"), () -> approve(s));

    UUID ctsId =
        inTenant(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "client-trust-statement")
                    .getFirst()
                    .getId());
    String html =
        asOwner(
            () ->
                generatedDocumentService
                    .generateDocument(ctsId, s.client().id(), false, true, List.of(), ownerId)
                    .pdfResult()
                    .htmlPreview());

    assertFired();
    assertThat(html)
        .contains(currency("50000.00"))
        .as("the placed R30 000 is counted once in the total held")
        .doesNotContain(currency("80000.00"));
  }

  /**
   * 652D account choice (F-652D-CR1): the client's money on A is all invested; A and B both hold
   * money for it, so the statement prints A (the first id, neither is the primary). An investment
   * return committing between A's general and invested read must not make A look empty — the choice
   * would then fall to B. The context is built inside a writing transaction, as generation runs.
   */
  @Test
  @SuppressWarnings("unchecked")
  void clientTrustStatement_investmentReturnedDuringTheAccountChoice_choosesTheAccountHoldingIt()
      throws Exception {
    var accounts =
        List.of(books.freshAccount(false), books.freshAccount(false)).stream()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
    UUID a = accounts.get(0);
    UUID b = accounts.get(1);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    Client client = books.newClient("account choice");
    books.deposit(a, client, "30000.00");
    var placed = books.requestPlacement(a, investmentAccount, client, "30000.00");
    books.approvePlacement(
        a,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverId);
    books.deposit(b, client, "1000.00");
    assertThat(
            inTenant(
                () ->
                    trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(
                        client.id())))
        .as("candidates in id order: A first")
        .containsExactly(a, b);
    String aName =
        inTenant(() -> trustAccountRepository.findById(a).orElseThrow().getAccountName());

    armAt(
        anywhere(),
        () ->
            books.withdraw(
                a,
                Expected.cashbookAndClient("30000.00"),
                investmentAccount,
                Expected.cashbookAndInvestment("-30000.00"),
                placed.id(),
                books.today()));

    Map<String, Object> context =
        asOwner(
            () ->
                transactionTemplate.execute(
                    tx -> customerContextBuilder.buildContext(client.id(), ownerId)));

    assertFired();
    var trust = (Map<String, Object>) context.get("trust");
    assertThat(trust.get("account_name"))
        .as("A holds R30 000 for the client before and after the return")
        .isEqualTo(aName);
  }

  // --- reports -----------------------------------------------------------------------------

  /**
   * {@code client-ledger-statement} (POST /api/report-definitions/{slug}/execute): the general
   * closing balance and the invested block come from one snapshot.
   */
  @Test
  @SuppressWarnings("unchecked")
  void clientLedgerStatementReport_placementApprovedBetweenTheReads_countsItOnce()
      throws Exception {
    var s = pendingPlacement();
    armAt(anywhere(), () -> approve(s));

    String body =
        executeReport(
            "client-ledger-statement",
            """
            {"trust_account_id": "%s", "customer_id": "%s", "dateFrom": "%s", "dateTo": "%s"}
            """
                .formatted(s.funding(), s.client().id(), MARCH_1, books.today()));

    assertFired();
    Map<String, Object> summary = JsonPath.read(body, "$.summary");
    BigDecimal general = amount(summary.get("closingBalance"));
    var invested = (Map<String, Object>) summary.get("invested");
    BigDecimal investedClosing =
        invested == null ? BigDecimal.ZERO : amount(invested.get("closingBalance"));
    assertThat(general.add(investedClosing))
        .as("general %s + invested %s", general, investedClosing)
        .isEqualByComparingTo("50000.00");
  }

  /**
   * Rule 54.15 creditors list ({@code client-trust-balances}, 650A F-6): the position (cashbook,
   * from the integrity check) and the creditor lines come from one snapshot, so a deposit committed
   * between them does not open a difference between the creditors and the money side.
   */
  @Test
  void rule5415Report_depositCommittedBetweenThePositionAndTheLines_listAgreesWithItself()
      throws Exception {
    UUID account = books.freshAccount(false);
    Client client = books.newClient("rule 54.15");
    books.deposit(account, client, "5000.00");
    armAt(anywhere(), () -> books.deposit(account, client, "1000.00"));

    String body =
        executeReport("client-trust-balances", "{\"trust_account_id\": \"%s\"}".formatted(account));

    assertFired();
    BigDecimal creditorTotal = amount(JsonPath.read(body, "$.summary.creditorTotal"));
    BigDecimal bankTotal = amount(JsonPath.read(body, "$.summary.bankTotal"));
    assertThat(creditorTotal)
        .as("creditors %s vs money side %s", creditorTotal, bankTotal)
        .isEqualByComparingTo(bankTotal);
    assertThat(amount(JsonPath.read(body, "$.summary.difference"))).isEqualByComparingTo("0");
  }

  // --- the runner --------------------------------------------------------------------------

  /**
   * {@link TrustSnapshotReads} inside a writing caller transaction (how generation calls it): a
   * second connection, checked out for the tenant still bound on the thread (the multitenant
   * connection provider sets {@code search_path} from {@code RequestScopes.TENANT_ID}), read-only
   * at REPEATABLE READ; the caller's transaction keeps its own connection and isolation.
   */
  @Test
  void snapshotRead_insideAWritingTransaction_ownTenantConnection_readOnlyRepeatableRead() {
    var seen = new Object[3][];
    asOwner(
        () ->
            transactionTemplate.execute(
                tx -> {
                  seen[0] = sessionRow();
                  seen[1] = snapshotReads.read(this::sessionRow);
                  seen[2] = sessionRow();
                  return null;
                }));
    var outer = seen[0];
    var inner = seen[1];

    assertThat(outer[1]).as("outer search_path").isEqualTo(schema);
    assertThat(outer[2]).isEqualTo("read committed");
    assertThat(outer[3]).isEqualTo("off");

    assertThat(inner[0]).as("a second connection, not the caller's").isNotEqualTo(outer[0]);
    assertThat(inner[1]).as("the tenant's schema on the new connection").isEqualTo(schema);
    assertThat(inner[2]).isEqualTo("repeatable read");
    assertThat(inner[3]).as("read-only").isEqualTo("on");

    assertThat(seen[2]).as("the caller's transaction goes on, unchanged").containsExactly(outer);
  }

  /** Backend pid, schema, isolation and read-only flag of the current transaction's connection. */
  private Object[] sessionRow() {
    return (Object[])
        entityManager
            .createNativeQuery(
                "select pg_backend_pid(), current_schema()::text,"
                    + " current_setting('transaction_isolation'),"
                    + " current_setting('transaction_read_only')")
            .getSingleResult();
  }

  // --- the race harness ----------------------------------------------------------------------

  /** A client with R50 000 on a fresh account and a R30 000 placement awaiting approval. */
  private record PendingPlacement(
      UUID funding, UUID investmentAccount, Client client, UUID placementRowId) {}

  private PendingPlacement pendingPlacement() {
    UUID funding = books.freshAccount(false);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    Client client = books.newClient("snapshot");
    books.deposit(funding, client, "50000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "30000.00");
    return new PendingPlacement(funding, investmentAccount, client, placed.depositTransactionId());
  }

  private void approve(PendingPlacement s) {
    books.approvePlacement(
        s.funding(),
        Expected.cashbookAndClient("-30000.00"),
        s.investmentAccount(),
        Expected.cashbookAndInvestment("30000.00"),
        s.placementRowId(),
        approverId);
  }

  private void armAt(Predicate<StackWalker> where, Runnable posting) {
    armed.set(new Armed(where, posting));
  }

  private static Predicate<StackWalker> anywhere() {
    return walker -> true;
  }

  /** Only at a read made (transitively) from {@code type.method}. */
  private static Predicate<StackWalker> calledFrom(Class<?> type, String method) {
    return walker ->
        walker.walk(
            frames ->
                frames.anyMatch(
                    f ->
                        f.getClassName().equals(type.getName())
                            && f.getMethodName().equals(method)));
  }

  /**
   * At an armed read: runs the posting on another thread, in a transaction of its own, and waits
   * for that transaction to commit; then lets the read go on. The reading thread holds no locks, so
   * the posting never waits on it.
   *
   * <p>The read is released at the commit, not when the thread ends: the posting's after-commit
   * listeners (e.g. {@code PortalEmailNotificationChannel}, {@code REQUIRES_NEW}) take a further
   * connection, and the test pool (3) is then held by the reader's two (caller + snapshot) and the
   * posting's one. {@link #assertFired} joins the thread.
   */
  private void fireIfArmed() throws Exception {
    var current = armed.get();
    if (current == null || !current.where().test(StackWalker.getInstance())) {
      return;
    }
    if (!armed.compareAndSet(current, null)) {
      return;
    }
    var committed = new CountDownLatch(1);
    postingFailure.set(null);
    postingThread =
        Thread.ofPlatform()
            .name("fu3-concurrent-posting")
            .start(
                () -> {
                  try {
                    ScopedValue.where(RequestScopes.TENANT_ID, schema)
                        .run(
                            () ->
                                transactionTemplate.executeWithoutResult(
                                    tx -> {
                                      TransactionSynchronizationManager.registerSynchronization(
                                          new TransactionSynchronization() {
                                            @Override
                                            public void afterCommit() {
                                              committed.countDown();
                                            }
                                          });
                                      current.posting().run();
                                    }));
                  } catch (Throwable e) {
                    postingFailure.set(e);
                  }
                });
    long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
    while (!committed.await(100, TimeUnit.MILLISECONDS)) {
      if (!postingThread.isAlive()) {
        throw new AssertionError("the concurrent posting failed", postingFailure.get());
      }
      if (System.nanoTime() > deadline) {
        throw new AssertionError("the concurrent posting did not commit within 90s");
      }
    }
  }

  private final AtomicReference<Throwable> postingFailure = new AtomicReference<>();
  private volatile Thread postingThread;

  /** The posting ran (and committed) between the reads, and its thread has ended cleanly. */
  private void assertFired() throws InterruptedException {
    var thread = postingThread;
    postingThread = null;
    assertThat(thread).as("the concurrent posting ran between the reads").isNotNull();
    assertThat(thread.join(Duration.ofSeconds(90))).as("posting thread ended").isTrue();
    if (postingFailure.get() != null) {
      throw new AssertionError("the concurrent posting failed", postingFailure.get());
    }
  }

  // --- helpers -------------------------------------------------------------------------------

  private String executeReport(String slug, String parameters) throws Exception {
    return mockMvc
        .perform(
            post("/api/report-definitions/" + slug + "/execute")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"parameters\": %s, \"page\": 0, \"size\": 100}"
                        .formatted(parameters.strip())))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static BigDecimal amount(Object value) {
    return new BigDecimal(String.valueOf(value));
  }

  private static String currency(String amount) {
    return VariableFormatter.format(new BigDecimal(amount), "currency", ZA);
  }

  /** In the tenant, in its own transaction. */
  private <T> T inTenant(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  /** As the owner (a trust reader), with no transaction of the test's own. */
  private <T> T asOwner(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, TRUST_READER)
        .call(body::get);
  }
}
