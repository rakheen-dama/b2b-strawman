package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates.TrustBalanceZeroGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F12 (HIGH): a debit on a matter must be covered by that client's money on that matter — the
 * matter sub-ledger cell {@code (trust account, client, matter)} — not merely by the client card.
 * Before the fix only the client card was checked, so a refund of "matter + client-level interest"
 * was approved and left the matter sub-ledger at −R1,34 while the closure gate (which reads the
 * matter) and the Trust tab (which read the card) disagreed (scenario 636B).
 *
 * <p>Every row goes through {@link TrustTransactionService} (the UI path), approvals by a second
 * member. Each test seeds its own clients and matters so balances never bleed between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustMatterDebitGuardIntegrationTest {

  private static final String ORG_ID = "org_trust_matter_debit_guard";
  private static final String OWNER_SUB = "user_tmdg_owner";
  private static final String APPROVER_SUB = "user_tmdg_approver";
  private static final String INSUFFICIENT_TITLE = "Insufficient trust balance";
  private static final String MATTER_CODE = "INSUFFICIENT_MATTER_TRUST_BALANCE";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustBalanceZeroGate trustBalanceZeroGate;
  @Autowired private EntityManager entityManager;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID ownerId;
  private UUID approverId;
  private UUID trustAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Matter Debit Guard Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "tmdg_owner@test.com", "TMDG Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER_SUB, "tmdg_approver@test.com", "TMDG Approver", "owner"));
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
                            "Matter Debit Guard Account",
                            "First National Bank",
                            "250655",
                            "6289000001",
                            "GENERAL",
                            true,
                            false,
                            null,
                            LocalDate.of(2025, 1, 1),
                            "G89-F12 guard test account"))
                    .id());
  }

  // --- 636B reproduction ---

  @Test
  void refund_exceedingMatterSubLedger_rejectedEvenWhenClientCardCovers() throws Exception {
    UUID client = newClient("636B Refund Client");
    UUID matter = newMatter("636B Refund Matter");
    deposit(client, matter, "5000.00", 30);
    approve(payment(client, matter, "1250.00", 20));
    legacyUnmatteredInterestCredit(client, "1.34", 10);

    UUID refundId = refund(client, matter, "3751.34", 1);
    var body =
        approveHttp(refundId)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value(INSUFFICIENT_TITLE))
            .andExpect(jsonPath("$.code").value(MATTER_CODE))
            .andExpect(jsonPath("$.matterId").value(matter.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(money(JsonPath.read(body, "$.available"))).isEqualByComparingTo("3750.00");
    assertThat(money(JsonPath.read(body, "$.requested"))).isEqualByComparingTo("3751.34");
    assertThat((String) JsonPath.read(body, "$.detail"))
        .contains("636B Refund Matter")
        .contains("636B Refund Client")
        .contains("Available on matter: R3750.00")
        .contains("Requested: R3751.34");

    assertThat(statusOf(refundId)).isEqualTo("AWAITING_APPROVAL");
    assertThat(matterBalance(matter)).isEqualByComparingTo("3750.00");
    assertThat(cardBalance(client)).isEqualByComparingTo("3751.34");
  }

  @Test
  void refund_equalToMatterSubLedger_approved_closureGatePasses() throws Exception {
    UUID client = newClient("Equal Refund Client");
    UUID matter = newMatter("Equal Refund Matter");
    deposit(client, matter, "5000.00", 30);
    approve(payment(client, matter, "1250.00", 20));
    legacyUnmatteredInterestCredit(client, "1.34", 10);

    UUID refundId = refund(client, matter, "3750.00", 1);
    approveHttp(refundId).andExpect(status().isOk());

    assertThat(statusOf(refundId)).isEqualTo("APPROVED");
    assertThat(matterBalance(matter)).isEqualByComparingTo("0.00");
    // The unmattered interest stays a client-level credit (D7)
    assertThat(cardBalance(client)).isEqualByComparingTo("1.34");
    assertThat(gatePasses(matter)).isTrue();
  }

  // --- each debit path ---

  @Test
  void payment_onMatter_exceedingCell_rejected() throws Exception {
    UUID client = newClient("Payment Cell Client");
    UUID matterM = newMatter("Payment Cell Matter M");
    UUID matterN = newMatter("Payment Cell Matter N");
    deposit(client, matterM, "1000.00", 10);
    deposit(client, matterN, "5000.00", 10);

    UUID paymentId = payment(client, matterM, "1500.00", 1);
    approveHttp(paymentId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(INSUFFICIENT_TITLE))
        .andExpect(jsonPath("$.code").value(MATTER_CODE))
        .andExpect(jsonPath("$.matterId").value(matterM.toString()));

    assertThat(statusOf(paymentId)).isEqualTo("AWAITING_APPROVAL");
    assertThat(matterBalance(matterM)).isEqualByComparingTo("1000.00");
    assertThat(cardBalance(client)).isEqualByComparingTo("6000.00");
  }

  @Test
  void feeTransfer_inferredMatter_exceedingCell_rejected() throws Exception {
    UUID client = newClient("Fee Transfer Cell Client");
    UUID matterM = newMatter("Fee Transfer Cell Matter M");
    UUID matterN = newMatter("Fee Transfer Cell Matter N");
    deposit(client, matterM, "500.00", 10);
    deposit(client, matterN, "5000.00", 10);
    UUID invoiceId = sentFeeNoteOnMatter(client, matterM, "1150.00");

    UUID feeTransferId =
        as(
            ownerId,
            () ->
                transactionService
                    .recordFeeTransfer(
                        trustAccountId,
                        new RecordFeeTransferRequest(
                            client, invoiceId, new BigDecimal("1150.00"), "FT-CELL-" + counter))
                    .id());
    // The matter is inferred from the fee note's single line (GAP-L-69)
    assertThat(projectOf(feeTransferId)).isEqualTo(matterM);

    approveHttp(feeTransferId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(MATTER_CODE))
        .andExpect(jsonPath("$.matterId").value(matterM.toString()));

    assertThat(statusOf(feeTransferId)).isEqualTo("AWAITING_APPROVAL");
    assertThat(matterBalance(matterM)).isEqualByComparingTo("500.00");
  }

  @Test
  void coClient_cannotSpendOtherClientsMatterMoney() throws Exception {
    UUID clientA = newClient("Co-client A");
    UUID clientB = newClient("Co-client B");
    UUID matterM = newMatter("Co-client Shared Matter");
    UUID matterN = newMatter("Co-client B Other Matter");
    deposit(clientA, matterM, "5000.00", 10);
    deposit(clientB, matterM, "200.00", 10);
    deposit(clientB, matterN, "2000.00", 10);

    // B's card (2 200) and the matter total (5 200) both cover R1 000; B's own cell on M (200)
    // does not — B must not spend A's money.
    UUID paymentId = payment(clientB, matterM, "1000.00", 1);
    approveHttp(paymentId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(MATTER_CODE))
        .andExpect(jsonPath("$.matterId").value(matterM.toString()));

    assertThat(statusOf(paymentId)).isEqualTo("AWAITING_APPROVAL");
    assertThat(matterBalance(matterM)).isEqualByComparingTo("5200.00");
    assertThat(cardBalance(clientB)).isEqualByComparingTo("2200.00");
  }

  @Test
  void transfer_withMatter_exceedingSourceCell_rejected() throws Exception {
    UUID source = newClient("Transfer Source");
    UUID target = newClient("Transfer Target");
    UUID matterM = newMatter("Transfer Matter M");
    UUID matterN = newMatter("Transfer Matter N");
    deposit(source, matterM, "1000.00", 10);
    deposit(source, matterN, "5000.00", 10);

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceClientId": "%s",
                      "targetClientId": "%s",
                      "matterId": "%s",
                      "amount": 1500.00,
                      "reference": "TRF-CELL-%d",
                      "description": "Over the source cell",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(source, target, matterM, counter, today)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(INSUFFICIENT_TITLE))
        .andExpect(jsonPath("$.code").value(MATTER_CODE))
        .andExpect(jsonPath("$.matterId").value(matterM.toString()));

    assertThat(matterBalance(matterM)).isEqualByComparingTo("1000.00");
    assertThat(cardBalance(source)).isEqualByComparingTo("6000.00");
    assertThat(cardBalanceOrNull(target)).isNull();
  }

  @Test
  void reverseTransferOut_targetAlreadySpent_rejected() throws Exception {
    UUID source = newClient("Reverse Source");
    UUID target = newClient("Reverse Target");
    UUID matterM = newMatter("Reverse Matter M");
    UUID matterN = newMatter("Reverse Target Other Matter");
    deposit(source, matterM, "1000.00", 10);
    deposit(target, matterN, "5000.00", 10);

    var legs =
        as(
            ownerId,
            () ->
                transactionService.recordTransfer(
                    trustAccountId,
                    new RecordTransferRequest(
                        source,
                        target,
                        matterM,
                        new BigDecimal("1000.00"),
                        "TRF-REV-" + counter,
                        "Move to target",
                        today.minusDays(5))));
    UUID transferOutId = legs.getFirst().id();
    UUID transferInId = legs.get(1).id();
    // reverseTransaction accepts only APPROVED rows while recordTransfer writes RECORDED legs, so —
    // as TrustTransactionServiceTest.reverseTransferOut_reversesBothLegsAndUpdatesBothLedgers does
    // — the TRANSFER_OUT is marked APPROVED to reach the reversal path.
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx -> {
                  var out = transactionRepository.findById(transferOutId).orElseThrow();
                  out.setStatus("APPROVED");
                  return transactionRepository.save(out);
                }));

    // The target spends the transferred money on M. Its card (5 000) still covers R1 000, but its
    // cell on M is now empty — reversing the transfer would take M's sub-ledger negative.
    approve(payment(target, matterM, "1000.00", 2));

    mockMvc
        .perform(
            post("/api/trust-transactions/" + transferOutId + "/reverse")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Transferred in error"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(INSUFFICIENT_TITLE))
        .andExpect(jsonPath("$.code").value(MATTER_CODE))
        .andExpect(jsonPath("$.matterId").value(matterM.toString()));

    // Nothing reversed: both legs keep their status, no ledger card moved
    assertThat(statusOf(transferOutId)).isEqualTo("APPROVED");
    assertThat(statusOf(transferInId)).isEqualTo("RECORDED");
    assertThat(cardBalance(source)).isEqualByComparingTo("0.00");
    assertThat(cardBalance(target)).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(matterM)).isEqualByComparingTo("0.00");
  }

  @Test
  void unmatteredRefund_guardedByClientCardOnly() throws Exception {
    UUID client = newClient("Unmattered Refund Client");
    deposit(client, null, "1000.00", 10);

    UUID refundId = refund(client, null, "800.00", 2);
    approveHttp(refundId).andExpect(status().isOk());
    assertThat(cardBalance(client)).isEqualByComparingTo("200.00");

    UUID overRefundId = refund(client, null, "300.00", 1);
    approveHttp(overRefundId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(INSUFFICIENT_TITLE))
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(statusOf(overRefundId)).isEqualTo("AWAITING_APPROVAL");
  }

  // --- Helpers ---

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
                                name, "tmdg_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private UUID newMatter(String name) throws Exception {
    return UUID.fromString(TestEntityHelper.createProject(mockMvc, ownerJwt(), name));
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
                    matter,
                    new BigDecimal(amount),
                    "TMDG-DEP-" + n,
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
                        matter,
                        new BigDecimal(amount),
                        "TMDG-PAY-" + n,
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
                        matter,
                        new BigDecimal(amount),
                        "TMDG-REF-" + n,
                        "Refund",
                        today.minusDays(daysAgo)))
                .id());
  }

  /** Single-approval mode refuses self-approval — approve as the second member. */
  private void approve(UUID transactionId) {
    as(approverId, () -> transactionService.approveTransaction(transactionId, approverId));
  }

  private ResultActions approveHttp(UUID transactionId) throws Exception {
    return mockMvc.perform(
        post("/api/trust-transactions/" + transactionId + "/approve").with(approverJwt()));
  }

  /**
   * An unmattered INTEREST_CREDIT exactly as {@code InterestService.postInterestRun} wrote it
   * before G89-F22 (project_id null, RECORDED, card credited) — the 636B ledger shape.
   */
  private void legacyUnmatteredInterestCredit(UUID client, String amount, int daysAgo) {
    int n = ++counter;
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx -> {
                  var date = today.minusDays(daysAgo);
                  transactionRepository.save(
                      new TrustTransaction(
                          trustAccountId,
                          "INTEREST_CREDIT",
                          new BigDecimal(amount),
                          client,
                          null,
                          null,
                          "INT-CREDIT-LEGACY-" + n,
                          "Interest credit (legacy, client-level)",
                          date,
                          "RECORDED",
                          ownerId));
                  var card =
                      ledgerCardRepository
                          .findByAccountAndCustomerForUpdate(trustAccountId, client)
                          .orElseThrow();
                  card.addInterestCredit(new BigDecimal(amount), date);
                  return ledgerCardRepository.save(card);
                }));
  }

  /** A SENT fee note with one line on {@code matter} (so a fee transfer infers that matter). */
  private UUID sentFeeNoteOnMatter(UUID client, UUID matter, String total) {
    UUID invoiceId = UUID.randomUUID();
    int n = ++counter;
    as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx -> {
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                              subtotal, tax_amount, total, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, :number, 'SENT', 'ZAR',
                              :total, 0, :total, :dueDate,
                              'Fee Transfer Cell Client', 'Test Org', :memberId, now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", client)
                      .setParameter("number", "INV-TMDG-" + n)
                      .setParameter("total", new BigDecimal(total))
                      .setParameter("dueDate", today.plusDays(30))
                      .setParameter("memberId", ownerId)
                      .executeUpdate();
                  return entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Legal services', 1, :total, :total, 0, false, now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", matter)
                      .setParameter("total", new BigDecimal(total))
                      .executeUpdate();
                }));
    return invoiceId;
  }

  private String statusOf(UUID transactionId) {
    return as(
        ownerId, () -> transactionRepository.findById(transactionId).orElseThrow().getStatus());
  }

  private UUID projectOf(UUID transactionId) {
    return as(
        ownerId, () -> transactionRepository.findById(transactionId).orElseThrow().getProjectId());
  }

  /** The matter sub-ledger — the closure gate's source. */
  private BigDecimal matterBalance(UUID matter) {
    return as(ownerId, () -> transactionRepository.calculateBalanceByProjectId(matter));
  }

  private BigDecimal cardBalance(UUID client) {
    var balance = cardBalanceOrNull(client);
    assertThat(balance).as("ledger card of %s", client).isNotNull();
    return balance;
  }

  private BigDecimal cardBalanceOrNull(UUID client) {
    return as(
        ownerId,
        () ->
            ledgerCardRepository
                .findByTrustAccountIdAndCustomerId(trustAccountId, client)
                .map(card -> card.getBalance())
                .orElse(null));
  }

  private boolean gatePasses(UUID matter) {
    return as(
        ownerId,
        () ->
            transactionTemplate.execute(
                tx ->
                    trustBalanceZeroGate
                        .evaluate(projectRepository.findById(matter).orElseThrow())
                        .passed()));
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

  private static JwtRequestPostProcessor approverJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB);
  }
}
