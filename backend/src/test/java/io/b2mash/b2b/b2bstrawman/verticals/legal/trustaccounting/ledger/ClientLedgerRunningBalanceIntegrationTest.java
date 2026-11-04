package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F23 + the /history filter LOW (Phase 89 slice 630C, arch §14.3.3, ADR-361): the client-ledger
 * {@code /history} rows carry a server-computed {@code runningBalance} over POSTED rows in posting
 * order, and the {@code type/status/dateFrom/dateTo} filters narrow the page without changing any
 * balance.
 *
 * <p>Every row is created through {@link TrustTransactionService} — the UI path, including the
 * approval step (by a different member) for the payment-type rows. Each test seeds its own client
 * so per-client counts are isolated (count-bleed rule).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerRunningBalanceIntegrationTest {

  private static final String ORG_ID = "org_ledger_running_balance";
  private static final String OWNER_SUB = "user_ledger_rb_owner";
  private static final Set<String> CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");
  private static final Set<String> DEBIT_TYPES =
      Set.of("PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_LPFF");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID ownerId;
  private UUID approverId;
  private UUID trustAccountId;
  private LocalDate today;
  private int clientCounter = 0;

  /** The seeded ledger of one client: ids by role. */
  private record Ledger(
      UUID clientId,
      UUID depositId,
      UUID refundId,
      UUID feeTransferId,
      UUID rejectedPaymentId,
      UUID reversedPaymentId,
      UUID awaitingPaymentId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Ledger Running Balance Test Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "ledger_rb_owner@test.com", "RB Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_ledger_rb_approver",
                "ledger_rb_approver@test.com",
                "RB Approver",
                "owner"));
    today = LocalDate.now(clock);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    trustAccountId =
        as(
            ownerId,
            () ->
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "Running Balance Trust Account",
                            "First National Bank",
                            "250655",
                            "6240000001",
                            "GENERAL",
                            true,
                            false,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "630C running-balance test account"))
                    .id());
  }

  // --- Invariant (ADR-361 / arch §14.3.3) ---

  @Test
  void history_runningBalance_matchesBalanceAsOfAndStatement_andNonPostedRowsAreNull()
      throws Exception {
    var ledger = seedLedger();
    var rows = history(ledger.clientId(), "size=50");

    assertThat(rows).hasSize(7);
    rows.forEach(r -> assertThat(r).containsKey("runningBalance"));

    // Order: transaction_date DESC, created_at DESC (id DESC is the final tie-break).
    for (int i = 1; i < rows.size(); i++) {
      var prev = rows.get(i - 1);
      var cur = rows.get(i);
      var prevDate = LocalDate.parse((String) prev.get("transactionDate"));
      var curDate = LocalDate.parse((String) cur.get("transactionDate"));
      assertThat(prevDate).isAfterOrEqualTo(curDate);
      if (prevDate.equals(curDate)) {
        assertThat(Instant.parse((String) prev.get("createdAt")))
            .isAfterOrEqualTo(Instant.parse((String) cur.get("createdAt")));
      }
    }

    // First POSTED row == balance-as-of(today) == /statement's last running balance.
    var firstPosted = rows.stream().filter(this::isPosted).findFirst().orElseThrow();
    assertThat(firstPosted.get("id")).isEqualTo(ledger.feeTransferId().toString());
    BigDecimal balanceAsOfToday =
        as(
            ownerId,
            () ->
                transactionTemplate.execute(
                    tx ->
                        transactionRepository.calculateClientBalanceAsOfDate(
                            ledger.clientId(), trustAccountId, today)));
    assertThat(money(firstPosted.get("runningBalance")))
        .isEqualByComparingTo(balanceAsOfToday)
        .isEqualByComparingTo("7850.00");

    var statementBody =
        mockMvc
            .perform(
                get(
                        "/api/trust-accounts/{acc}/client-ledgers/{client}/statement",
                        trustAccountId,
                        ledger.clientId())
                    .param("startDate", "1970-01-01")
                    .param("endDate", today.toString())
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Object> statementBalances =
        JsonPath.read(statementBody, "$.transactions[*].runningBalance");
    assertThat(money(statementBalances.getLast()))
        .isEqualByComparingTo(money(firstPosted.get("runningBalance")));

    // Walking the page oldest → newest reproduces every intermediate balance.
    BigDecimal running = BigDecimal.ZERO;
    for (int i = rows.size() - 1; i >= 0; i--) {
      var row = rows.get(i);
      if (isPosted(row)) {
        var amount = money(row.get("amount"));
        String type = (String) row.get("transactionType");
        if (CREDIT_TYPES.contains(type)) {
          running = running.add(amount);
        } else if (DEBIT_TYPES.contains(type)) {
          running = running.subtract(amount);
        }
        assertThat(money(row.get("runningBalance")))
            .as("running balance of %s %s", type, row.get("id"))
            .isEqualByComparingTo(running);
      } else {
        assertThat(row.get("runningBalance"))
            .as(
                "non-posted / REVERSAL row %s (%s, %s) must carry null",
                row.get("id"), row.get("transactionType"), row.get("status"))
            .isNull();
      }
    }

    var byId = indexById(rows);
    assertThat(money(byId.get(ledger.depositId().toString()).get("runningBalance")))
        .isEqualByComparingTo("10000.00");
    assertThat(money(byId.get(ledger.refundId().toString()).get("runningBalance")))
        .isEqualByComparingTo("9000.00");
    assertThat(byId.get(ledger.rejectedPaymentId().toString()).get("status")).isEqualTo("REJECTED");
    assertThat(byId.get(ledger.rejectedPaymentId().toString()).get("runningBalance")).isNull();
    assertThat(byId.get(ledger.reversedPaymentId().toString()).get("status")).isEqualTo("REVERSED");
    assertThat(byId.get(ledger.reversedPaymentId().toString()).get("runningBalance")).isNull();
    assertThat(byId.get(ledger.awaitingPaymentId().toString()).get("status"))
        .isEqualTo("AWAITING_APPROVAL");
    assertThat(byId.get(ledger.awaitingPaymentId().toString()).get("runningBalance")).isNull();
    var reversalRow =
        rows.stream().filter(r -> "REVERSAL".equals(r.get("transactionType"))).findFirst();
    assertThat(reversalRow).isPresent();
    assertThat(reversalRow.get()).containsKey("runningBalance");
    assertThat(reversalRow.get().get("runningBalance")).isNull();
  }

  // --- Filters bind after the window ---

  @Test
  void history_filters_narrowThePage_withoutChangingAnyBalance() throws Exception {
    var ledger = seedLedger();
    var unfiltered = indexById(history(ledger.clientId(), "size=50"));

    // type=REFUND → exactly the refund row, balance unchanged.
    var refundOnly = historyPage(ledger.clientId(), "size=50&type=REFUND");
    assertThat(rowsOf(refundOnly)).hasSize(1);
    assertThat(totalElements(refundOnly)).isEqualTo(1);
    assertThat(rowsOf(refundOnly).getFirst().get("id")).isEqualTo(ledger.refundId().toString());
    assertSameBalances(rowsOf(refundOnly), unfiltered);
    assertThat(money(rowsOf(refundOnly).getFirst().get("runningBalance")))
        .isEqualByComparingTo("9000.00");

    // status=APPROVED → refund + fee transfer (the approved-then-reversed payment is REVERSED).
    var approved = historyPage(ledger.clientId(), "size=50&status=APPROVED");
    assertThat(rowsOf(approved))
        .extracting(r -> r.get("id"))
        .containsExactly(ledger.feeTransferId().toString(), ledger.refundId().toString());
    assertThat(totalElements(approved)).isEqualTo(2);
    assertSameBalances(rowsOf(approved), unfiltered);

    // dateFrom/dateTo → the four rows dated today-5 … today-1.
    var window =
        historyPage(
            ledger.clientId(),
            "size=50&dateFrom=" + today.minusDays(5) + "&dateTo=" + today.minusDays(1));
    assertThat(rowsOf(window))
        .extracting(r -> r.get("id"))
        .containsExactly(
            ledger.awaitingPaymentId().toString(),
            ledger.reversedPaymentId().toString(),
            ledger.rejectedPaymentId().toString(),
            ledger.refundId().toString());
    assertThat(totalElements(window)).isEqualTo(4);
    assertSameBalances(rowsOf(window), unfiltered);

    // dateTo alone → the opening deposit keeps its balance.
    var early = historyPage(ledger.clientId(), "size=50&dateTo=" + today.minusDays(6));
    assertThat(rowsOf(early))
        .extracting(r -> r.get("id"))
        .containsExactly(ledger.depositId().toString());
    assertSameBalances(rowsOf(early), unfiltered);
    assertThat(money(rowsOf(early).getFirst().get("runningBalance")))
        .isEqualByComparingTo("10000.00");

    // dateFrom alone → today's two rows.
    var late = historyPage(ledger.clientId(), "size=50&dateFrom=" + today);
    assertThat(totalElements(late)).isEqualTo(2);
    assertSameBalances(rowsOf(late), unfiltered);

    // Combination: type + status → the rejected payment only (balance null).
    var rejected = historyPage(ledger.clientId(), "size=50&type=PAYMENT&status=REJECTED");
    assertThat(rowsOf(rejected))
        .extracting(r -> r.get("id"))
        .containsExactly(ledger.rejectedPaymentId().toString());
    assertThat(totalElements(rejected)).isEqualTo(1);
    assertThat(rowsOf(rejected).getFirst()).containsKey("runningBalance");
    assertThat(rowsOf(rejected).getFirst().get("runningBalance")).isNull();
  }

  // --- Paging: balances on every page, not just page 0 ---

  @Test
  void history_laterPages_carryTheSameBalances() throws Exception {
    var ledger = seedLedger();
    var unfiltered = indexById(history(ledger.clientId(), "size=50"));

    var collected = new ArrayList<Map<String, Object>>();
    for (int page = 0; page < 4; page++) {
      var body = historyPage(ledger.clientId(), "size=2&page=" + page);
      assertThat(totalElements(body)).isEqualTo(7);
      var rows = rowsOf(body);
      rows.forEach(r -> assertThat(r).containsKey("runningBalance"));
      assertSameBalances(rows, unfiltered);
      collected.addAll(rows);
    }
    assertThat(collected).hasSize(7);

    // Page 3 (size 2) holds only the opening deposit — its balance is shown, not "—".
    var lastPage = rowsOf(historyPage(ledger.clientId(), "size=2&page=3"));
    assertThat(lastPage).hasSize(1);
    assertThat(lastPage.getFirst().get("id")).isEqualTo(ledger.depositId().toString());
    assertThat(money(lastPage.getFirst().get("runningBalance"))).isEqualByComparingTo("10000.00");
  }

  /** Risk R5: the frontend sends {@code sort=transactionDate,desc}; it must not break the query. */
  @Test
  void history_toleratesTheFrontendSortParam() throws Exception {
    var ledger = seedLedger();
    var plain = history(ledger.clientId(), "size=50");
    var sorted = history(ledger.clientId(), "size=50&page=0&sort=transactionDate,desc");

    assertThat(sorted)
        .extracting(r -> r.get("id"))
        .containsExactlyElementsOf(plain.stream().map(r -> r.get("id")).toList());
    assertThat(sorted).allSatisfy(r -> assertThat(r).containsKey("runningBalance"));
  }

  // --- OQ #3: record the plan of the production window query ---

  /** A named bind ({@code :name}) that is not part of a Postgres {@code ::} cast. */
  private static final Pattern NAMED_BIND = Pattern.compile("(?<!:):([A-Za-z_]\\w*)\\b");

  @Test
  void explainWindowQuery_cteEvaluatedOnceOnColdStatistics() throws Exception {
    Optional<Method> method =
        Arrays.stream(TrustTransactionRepository.class.getMethods())
            .filter(m -> m.getName().equals("findClientHistoryWithRunningBalance"))
            .findFirst();
    assertThat(method)
        .as("TrustTransactionRepository.findClientHistoryWithRunningBalance (ADR-361)")
        .isPresent();
    String sql = method.get().getAnnotation(Query.class).value();

    var ledger = seedLedger();
    Map<String, String> binds =
        Map.of(
            "customerId", "CAST('" + ledger.clientId() + "' AS uuid)",
            "accountId", "CAST('" + trustAccountId + "' AS uuid)",
            "type", "NULL",
            "status", "NULL",
            "dateFrom", "NULL",
            "dateTo", "NULL");
    String literalSql =
        NAMED_BIND
            .matcher(sql)
            .replaceAll(
                m -> {
                  String literal = binds.get(m.group(1));
                  assertThat(literal).as("literal for bind :%s", m.group(1)).isNotNull();
                  return Matcher.quoteReplacement(literal);
                });

    // Cold statistics first (a freshly provisioned tenant — the case where an inlined CTE let the
    // planner re-run the window subquery per outer row), then after ANALYZE (a live tenant once
    // autovacuum has run). Both must evaluate the materialised CTE exactly once.
    List<String> coldPlan;
    List<String> analyzedPlan;
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      try {
        stmt.execute("SET search_path TO " + tenantSchema);
        coldPlan = explain(stmt, literalSql);
        stmt.execute("ANALYZE trust_transactions");
        analyzedPlan = explain(stmt, literalSql);
      } finally {
        stmt.execute("RESET search_path");
      }
    }
    printPlan("no statistics", coldPlan);
    printPlan("after ANALYZE trust_transactions", analyzedPlan);
    assertCteEvaluatedOnce("no statistics", coldPlan);
    assertCteEvaluatedOnce("after ANALYZE", analyzedPlan);
  }

  private static void assertCteEvaluatedOnce(String label, List<String> plan) {
    String text = String.join("\n", plan);
    assertThat(text).as("%s: CTE materialised and scanned", label).contains("CTE Scan on posted");
    List<String> windowAggs = plan.stream().filter(l -> l.contains("WindowAgg")).toList();
    assertThat(windowAggs).as("%s: one WindowAgg node", label).hasSize(1);
    assertThat(windowAggs.getFirst())
        .as("%s: window evaluated once, not per outer row", label)
        .contains("loops=1)");
  }

  private static List<String> explain(Statement stmt, String sql) throws Exception {
    var plan = new ArrayList<String>();
    try (ResultSet rs = stmt.executeQuery("EXPLAIN (ANALYZE, FORMAT TEXT) " + sql)) {
      while (rs.next()) {
        plan.add(rs.getString(1));
      }
    }
    return plan;
  }

  private static void printPlan(String label, List<String> plan) {
    System.out.println(
        "=== 630C OQ #3 EXPLAIN (ANALYZE, FORMAT TEXT) findClientHistoryWithRunningBalance — "
            + label
            + " ===");
    plan.forEach(System.out::println);
    System.out.println("=== end EXPLAIN ===");
  }

  // --- Seeding through the service (the UI path) ---

  /**
   * One client on the shared account: deposit R10 000 (today-10, RECORDED) → refund R1 000
   * (today-5, approved) → fee transfer R1 150 (server-dated today, approved) → payment R500
   * (today-3, rejected) → payment R700 (today-2, approved then reversed: original REVERSED + a
   * RECORDED REVERSAL row dated today) → payment R300 (today-1, left AWAITING_APPROVAL). Posted
   * balances: 10 000 → 9 000 → 7 850.
   */
  private Ledger seedLedger() {
    int n = ++clientCounter;
    UUID clientId =
        as(
            ownerId,
            () ->
                transactionTemplate.execute(
                    tx ->
                        customerRepository
                            .save(
                                TestCustomerFactory.createActiveCustomer(
                                    "RB Client " + n, "rb_client_" + n + "@test.com", ownerId))
                            .getId()));

    UUID depositId =
        as(
            ownerId,
            () ->
                transactionService
                    .recordDeposit(
                        trustAccountId,
                        new RecordDepositRequest(
                            clientId,
                            null,
                            new BigDecimal("10000.00"),
                            "RB-DEP-" + n,
                            "Opening deposit",
                            today.minusDays(10)))
                    .id());

    UUID refundId =
        as(
            ownerId,
            () ->
                transactionService
                    .recordRefund(
                        trustAccountId,
                        new RecordRefundRequest(
                            clientId,
                            null,
                            new BigDecimal("1000.00"),
                            "RB-REF-" + n,
                            "Partial refund",
                            today.minusDays(5)))
                    .id());
    approve(refundId);

    UUID invoiceId = UUID.randomUUID();
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    entityManager
                        .createNativeQuery(
                            """
                            INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                                subtotal, tax_amount, total, due_date,
                                customer_name, org_name, created_by, created_at, updated_at)
                            VALUES (:id, :customerId, :number, 'SENT', 'ZAR',
                                1000.00, 150.00, 1150.00, :dueDate,
                                :customerName, 'Test Org', :memberId, now(), now())
                            """)
                        .setParameter("id", invoiceId)
                        .setParameter("customerId", clientId)
                        .setParameter("number", "INV-RB-" + n)
                        .setParameter("dueDate", today.plusDays(30))
                        .setParameter("customerName", "RB Client " + n)
                        .setParameter("memberId", ownerId)
                        .executeUpdate()));
    UUID feeTransferId =
        as(
            ownerId,
            () ->
                transactionService
                    .recordFeeTransfer(
                        trustAccountId,
                        new RecordFeeTransferRequest(
                            clientId, invoiceId, new BigDecimal("1150.00"), "RB-FT-" + n))
                    .id());
    approve(feeTransferId);

    UUID rejectedPaymentId = recordPayment(clientId, "500.00", "RB-PAY-REJ-" + n, 3);
    as(
        approverId,
        () -> transactionService.rejectTransaction(rejectedPaymentId, approverId, "Duplicate"));

    UUID reversedPaymentId = recordPayment(clientId, "700.00", "RB-PAY-REV-" + n, 2);
    approve(reversedPaymentId);
    as(ownerId, () -> transactionService.reverseTransaction(reversedPaymentId, "Paid in error"));

    UUID awaitingPaymentId = recordPayment(clientId, "300.00", "RB-PAY-AWT-" + n, 1);

    return new Ledger(
        clientId,
        depositId,
        refundId,
        feeTransferId,
        rejectedPaymentId,
        reversedPaymentId,
        awaitingPaymentId);
  }

  private UUID recordPayment(UUID clientId, String amount, String reference, int daysAgo) {
    return as(
        ownerId,
        () ->
            transactionService
                .recordPayment(
                    trustAccountId,
                    new RecordPaymentRequest(
                        clientId,
                        null,
                        new BigDecimal(amount),
                        reference,
                        "Payment " + reference,
                        today.minusDays(daysAgo)))
                .id());
  }

  /** Single-approval mode refuses self-approval — approve as the second member. */
  private void approve(UUID transactionId) {
    as(approverId, () -> transactionService.approveTransaction(transactionId, approverId));
  }

  private <T> T as(UUID memberId, Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .call(action::get);
  }

  // --- /history reads ---

  private String historyPage(UUID clientId, String query) throws Exception {
    return mockMvc
        .perform(
            get("/api/trust-accounts/"
                    + trustAccountId
                    + "/client-ledgers/"
                    + clientId
                    + "/history?"
                    + query)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<Map<String, Object>> history(UUID clientId, String query) throws Exception {
    return rowsOf(historyPage(clientId, query));
  }

  private static List<Map<String, Object>> rowsOf(String body) {
    return JsonPath.read(body, "$.content");
  }

  private static long totalElements(String body) {
    return ((Number) JsonPath.read(body, "$.page.totalElements")).longValue();
  }

  private static Map<String, Map<String, Object>> indexById(List<Map<String, Object>> rows) {
    var byId = new HashMap<String, Map<String, Object>>();
    rows.forEach(r -> byId.put((String) r.get("id"), r));
    return byId;
  }

  /** Every row's runningBalance equals the same row's value in the unfiltered response. */
  private static void assertSameBalances(
      List<Map<String, Object>> rows, Map<String, Map<String, Object>> unfiltered) {
    for (var row : rows) {
      assertThat(row).containsKey("runningBalance");
      var expected = unfiltered.get((String) row.get("id")).get("runningBalance");
      var actual = row.get("runningBalance");
      if (expected == null) {
        assertThat(actual).as("row %s", row.get("id")).isNull();
      } else {
        assertThat(money(actual)).as("row %s", row.get("id")).isEqualByComparingTo(money(expected));
      }
    }
  }

  private boolean isPosted(Map<String, Object> row) {
    var status = row.get("status");
    return ("RECORDED".equals(status) || "APPROVED".equals(status))
        && !"REVERSAL".equals(row.get("transactionType"));
  }

  private static BigDecimal money(Object jsonNumber) {
    assertThat(jsonNumber).as("money value").isNotNull();
    return new BigDecimal(String.valueOf(jsonNumber));
  }
}
