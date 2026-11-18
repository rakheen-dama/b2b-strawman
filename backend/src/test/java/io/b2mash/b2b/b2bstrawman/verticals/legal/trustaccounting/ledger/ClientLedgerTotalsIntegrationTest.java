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
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.WithdrawInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.ClientTrustBalancesQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F13: the client-ledger totals must reconcile to the balance — Deposits + Transfers in −
 * Payments − Fee transfers − Refunds − Transfers out = Balance. Before the fix the card's running
 * counters were shown: refunds and transfers had no total at all, and a reversed payment stayed in
 * "Payments" (636B: R5 000 + R1,34 − R1 250 − R3 751,34 = 0 rendered as an unexplained R0,00).
 * Phase 92 (646A) removed the interest component: interest-credit postings no longer exist
 * (ADR-378), so the 636B case reconciles without the R1,34. Phase 92 (648E) adds a section 86(4)
 * placement and its return as totals of their own, so the identity reads: Deposits + Transfers in +
 * Returned from section 86(4) investments − Payments − Fee transfers − Refunds − Transfers out −
 * Placed in section 86(4) investments = Balance (the general balance); the invested balance sits
 * beside it.
 *
 * <p>Rows go through {@link TrustTransactionService}; approvals by a second member. Reads go
 * through the HTTP API (single ledger and the list). Each test seeds its own clients.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerTotalsIntegrationTest {

  private static final String ORG_ID = "org_client_ledger_totals";
  private static final String OWNER_SUB = "user_clt_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private Clock clock;

  @Autowired private ClientTrustBalancesQuery clientTrustBalancesQuery;

  private String tenantSchema;
  private UUID ownerId;
  private UUID approverId;
  private UUID trustAccountId;
  private UUID investmentAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Client Ledger Totals Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "clt_owner@test.com", "CLT Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_clt_approver",
                "clt_approver@test.com",
                "CLT Approver",
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
                            "Ledger Totals Account",
                            "First National Bank",
                            "250655",
                            "6292000001",
                            "GENERAL",
                            true,
                            false,
                            null,
                            LocalDate.of(2025, 1, 1),
                            "G89-F13 totals test account"))
                    .id());
    investmentAccountId =
        as(
            ownerId,
            () ->
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "Ledger Totals s86(4) Investments",
                            "Investec",
                            "580105",
                            "6292000002",
                            "INVESTMENT",
                            false,
                            false,
                            null,
                            LocalDate.of(2025, 1, 1),
                            "648E",
                            InvestmentBasis.CLIENT_INSTRUCTION))
                    .id());
  }

  @Test
  void totals_reconcile_5000_minus_1250_minus_3750_equals_0() throws Exception {
    UUID client = newClient("636B Totals Client");
    UUID matter = trustMatterOf(client);
    deposit(client, matter, "5000.00", 30);
    approve(payment(client, matter, "1250.00", 20));
    approve(refund(client, matter, "3750.00", 1));

    var ledger = ledger(client);
    assertThat(money(ledger.get("balance"))).isEqualByComparingTo("0.00");
    assertThat(money(ledger.get("totalDeposits"))).isEqualByComparingTo("5000.00");
    assertThat(ledger).doesNotContainKey("totalInterestCredited");
    assertThat(money(ledger.get("totalPayments"))).isEqualByComparingTo("1250.00");
    assertThat(money(ledger.get("totalFeeTransfers"))).isEqualByComparingTo("0.00");
    assertThat(money(ledger.get("totalRefunds"))).isEqualByComparingTo("3750.00");
    assertThat(money(ledger.get("totalTransfersIn"))).isEqualByComparingTo("0.00");
    assertThat(money(ledger.get("totalTransfersOut"))).isEqualByComparingTo("0.00");
    assertReconciles(ledger);

    // The list endpoint carries the same derived totals
    var row = listRow(client);
    assertThat(money(row.get("totalRefunds"))).isEqualByComparingTo("3750.00");
    assertThat(row).doesNotContainKey("totalInterestCredited");
    assertReconciles(row);
  }

  @Test
  void reversedPayment_excludedFromTotalPayments() throws Exception {
    UUID client = newClient("Reversed Payment Client");
    deposit(client, null, "1000.00", 10);
    UUID paymentId = payment(client, null, "300.00", 5);
    approve(paymentId);
    as(
        ownerId,
        () ->
            transactionService.reverseTransaction(
                paymentId, new ReverseRequest("Paid in error", null)));

    var ledger = ledger(client);
    assertThat(money(ledger.get("balance"))).isEqualByComparingTo("1000.00");
    assertThat(money(ledger.get("totalPayments"))).isEqualByComparingTo("0.00");
    assertReconciles(ledger);
  }

  @Test
  void transfers_inAndOut_reported() throws Exception {
    UUID source = newClient("Totals Transfer Source");
    UUID target = newClient("Totals Transfer Target");
    deposit(source, null, "1000.00", 10);
    as(
        ownerId,
        () ->
            transactionService.recordTransfer(
                trustAccountId,
                new RecordTransferRequest(
                    source,
                    trustMatterOf(source),
                    target,
                    trustMatterOf(target),
                    new BigDecimal("400.00"),
                    "CLT-TRF-" + (++counter),
                    "Inter-client transfer",
                    today.minusDays(3))));

    var sourceLedger = ledger(source);
    assertThat(money(sourceLedger.get("balance"))).isEqualByComparingTo("600.00");
    assertThat(money(sourceLedger.get("totalTransfersOut"))).isEqualByComparingTo("400.00");
    assertThat(money(sourceLedger.get("totalTransfersIn"))).isEqualByComparingTo("0.00");
    assertReconciles(sourceLedger);

    var targetLedger = ledger(target);
    assertThat(money(targetLedger.get("balance"))).isEqualByComparingTo("400.00");
    assertThat(money(targetLedger.get("totalTransfersIn"))).isEqualByComparingTo("400.00");
    assertThat(money(targetLedger.get("totalDeposits"))).isEqualByComparingTo("0.00");
    assertReconciles(targetLedger);
  }

  @Test
  void clientTrustBalancesReport_asOfDate_totalsCutAtTheSameDateAsTheBalance() {
    UUID client = newClient("Report As-Of Client");
    deposit(client, null, "1000.00", 20);
    approve(payment(client, null, "400.00", 5));

    // As of ten days ago the payment has not happened: balance and totals both exclude it
    var asOf = reportRow(client, today.minusDays(10));
    assertThat(money(asOf.get("balance"))).isEqualByComparingTo("1000.00");
    assertThat(money(asOf.get("totalDeposits"))).isEqualByComparingTo("1000.00");
    assertThat(money(asOf.get("totalPayments"))).isEqualByComparingTo("0.00");

    // Without a date the row is the current derived balance with all posted totals
    var current = reportRow(client, null);
    assertThat(money(current.get("balance"))).isEqualByComparingTo("600.00");
    assertThat(money(current.get("totalPayments"))).isEqualByComparingTo("400.00");
  }

  @Test
  void section86_4Placement_andItsReturn_areTheirOwnTotals_andTheCardReconcilesToTheGeneralBalance()
      throws Exception {
    UUID client = newClient("Totals s86(4) Investor");
    UUID matter = trustMatterOf(client);
    deposit(client, matter, "50000.00", 30);
    int n = ++counter;
    var placed =
        as(
            ownerId,
            () ->
                investmentService.placeInvestment(
                    trustAccountId,
                    new PlaceInvestmentRequest(
                        investmentAccountId,
                        client,
                        matter,
                        "Investec",
                        "CLT-FD-" + n,
                        new BigDecimal("30000.00"),
                        new BigDecimal("0.0650"),
                        today.minusDays(20),
                        null,
                        null,
                        InvestmentBasis.CLIENT_INSTRUCTION)));
    approve(placed.depositTransactionId());

    // Approved: placed, not transferred out; the invested figure sits beside the general balance
    for (var ledger : List.of(ledger(client), listRow(client))) {
      assertThat(money(ledger.get("balance"))).isEqualByComparingTo("20000.00");
      assertThat(money(ledger.get("totalInvestmentPlacements"))).isEqualByComparingTo("30000.00");
      assertThat(money(ledger.get("totalInvestmentReturns"))).isEqualByComparingTo("0.00");
      assertThat(money(ledger.get("totalTransfersOut"))).isEqualByComparingTo("0.00");
      assertThat(money(ledger.get("investedBalance"))).isEqualByComparingTo("30000.00");
      assertReconciles(ledger);
    }

    as(
        ownerId,
        () ->
            investmentService.withdrawInvestment(
                placed.id(), new WithdrawInvestmentRequest(today.minusDays(5))));

    // Withdrawn: returned, not transferred in; nothing is invested any more
    for (var ledger : List.of(ledger(client), listRow(client))) {
      assertThat(money(ledger.get("balance"))).isEqualByComparingTo("50000.00");
      assertThat(money(ledger.get("totalInvestmentPlacements"))).isEqualByComparingTo("30000.00");
      assertThat(money(ledger.get("totalInvestmentReturns"))).isEqualByComparingTo("30000.00");
      assertThat(money(ledger.get("totalTransfersIn"))).isEqualByComparingTo("0.00");
      assertThat(money(ledger.get("investedBalance"))).isEqualByComparingTo("0.00");
      assertReconciles(ledger);
    }
  }

  // --- Helpers ---

  private Map<String, Object> reportRow(UUID client, LocalDate asOfDate) {
    var params = new HashMap<String, Object>();
    params.put("trust_account_id", trustAccountId.toString());
    if (asOfDate != null) {
      params.put("asOfDate", asOfDate.toString());
    }
    var result =
        as(
            ownerId,
            () -> transactionTemplate.execute(tx -> clientTrustBalancesQuery.executeAll(params)));
    var rows = result.rows().stream().filter(r -> client.equals(r.get("customerId"))).toList();
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  /**
   * Deposits + Transfers in + Returned from section 86(4) investments − Payments − Fee transfers −
   * Refunds − Transfers out − Placed in section 86(4) investments = Balance (general, 648E).
   */
  private static void assertReconciles(Map<String, Object> ledger) {
    var derived =
        money(ledger.get("totalDeposits"))
            .add(money(ledger.get("totalTransfersIn")))
            .add(money(ledger.get("totalInvestmentReturns")))
            .subtract(money(ledger.get("totalPayments")))
            .subtract(money(ledger.get("totalFeeTransfers")))
            .subtract(money(ledger.get("totalRefunds")))
            .subtract(money(ledger.get("totalTransfersOut")))
            .subtract(money(ledger.get("totalInvestmentPlacements")));
    assertThat(derived)
        .as("totals reconcile to the balance")
        .isEqualByComparingTo(money(ledger.get("balance")));
  }

  private Map<String, Object> ledger(UUID client) throws Exception {
    var body =
        mockMvc
            .perform(
                get("/api/trust-accounts/" + trustAccountId + "/client-ledgers/" + client)
                    .with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$");
  }

  private Map<String, Object> listRow(UUID client) throws Exception {
    var body =
        mockMvc
            .perform(
                get("/api/trust-accounts/" + trustAccountId + "/client-ledgers")
                    .param("size", "200")
                    .with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> rows =
        JsonPath.read(body, "$.content[?(@.clientId == '" + client + "')]");
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private UUID newClient(String name) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                name, "clt_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private void deposit(UUID client, UUID matter, String amount, int daysAgo) {
    int n = ++counter;
    as(
        ownerId,
        () ->
            transactionService.recordDeposit(
                trustAccountId,
                new RecordDepositRequest(
                    client,
                    matter != null ? matter : trustMatterOf(client),
                    new BigDecimal(amount),
                    "CLT-DEP-" + n,
                    "Deposit",
                    today.minusDays(daysAgo))));
  }

  private UUID payment(UUID client, UUID matter, String amount, int daysAgo) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionService
                .recordPayment(
                    trustAccountId,
                    new RecordPaymentRequest(
                        client,
                        matter != null ? matter : trustMatterOf(client),
                        new BigDecimal(amount),
                        "CLT-PAY-" + n,
                        "Payment",
                        today.minusDays(daysAgo)))
                .id());
  }

  private UUID refund(UUID client, UUID matter, String amount, int daysAgo) {
    int n = ++counter;
    return as(
        ownerId,
        () ->
            transactionService
                .recordRefund(
                    trustAccountId,
                    new RecordRefundRequest(
                        client,
                        matter != null ? matter : trustMatterOf(client),
                        new BigDecimal(amount),
                        "CLT-REF-" + n,
                        "Refund",
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

  private static BigDecimal money(Object jsonNumber) {
    assertThat(jsonNumber).as("money value").isNotNull();
    return new BigDecimal(jsonNumber.toString());
  }

  private static JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
  }

  private final java.util.Map<UUID, UUID> trustMatters =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(UUID clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                id,
                "Trust matter " + id,
                ownerId));
  }
}
