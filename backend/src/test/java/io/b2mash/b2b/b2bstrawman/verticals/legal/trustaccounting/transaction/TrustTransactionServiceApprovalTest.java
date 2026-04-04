package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionServiceApprovalTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_trust_approval_test";
  private static final Set<String> APPROVER_CAPABILITIES =
      Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID recorderId; // member who records transactions
  private UUID approverId; // different member who approves
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Approval Test Org", null).schemaName();

    recorderId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_approval_recorder", "recorder@test.com", "Recorder Member", "admin"));

    approverId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_approval_approver", "approver@test.com", "Approver Member", "owner"));

    // Enable trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create trust account (single approval mode)
    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "Approval Test Trust Account",
                          "First National Bank",
                          "250655",
                          "9876543210",
                          "GENERAL",
                          true,
                          false, // requireDualApproval = false
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Trust account for approval tests");

                  var response = trustAccountService.createTrustAccount(request);
                  trustAccountId = response.id();
                }));
  }

  // --- Test 1: Payment created in AWAITING_APPROVAL ---

  @Test
  void recordPayment_createsTransactionInAwaitingApprovalStatus() {
    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Payment Client", "payment@test.com", recorderId));

                  // Fund the customer first
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-PAY-001",
                          "Funding deposit",
                          LocalDate.of(2026, 3, 1)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-001",
                              "Payment for services",
                              LocalDate.of(2026, 3, 2)));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("PAYMENT");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(response.customerId()).isEqualTo(customer.getId());
                  assertThat(response.reference()).isEqualTo("PAY-001");
                  assertThat(response.recordedBy()).isEqualTo(recorderId);

                  // Verify ledger was NOT updated (deferred to approval)
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customer.getId())
                          .orElseThrow();
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
                  assertThat(ledger.getTotalPayments()).isEqualByComparingTo(BigDecimal.ZERO);
                }));
  }

  // --- Test 2: Approve transitions to APPROVED and debits ledger ---

  @Test
  void approveTransaction_transitionsToApprovedAndDebitsLedger() {
    UUID[] txnId = new UUID[1];
    UUID[] customerId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Approve Client", "approve@test.com", recorderId));
                  customerId[0] = customer.getId();

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("8000.00"),
                          "DEP-APP-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("3000.00"),
                              "PAY-APP-001",
                              "Approve test payment",
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    // Approve as a different member
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                  assertThat(response.approvedAt()).isNotNull();

                  // Verify ledger was debited
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId[0])
                          .orElseThrow();
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(ledger.getTotalPayments())
                      .isEqualByComparingTo(new BigDecimal("3000.00"));
                }));
  }

  // --- Test 3: Self-approval prevention ---

  @Test
  void approveTransaction_selfApprovalPrevention_returns400() {
    UUID[] txnId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Self Approve Client", "selfapprove@test.com", recorderId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-SELF-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-SELF-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    // Attempt self-approval (recorder tries to approve their own transaction)
    runAsRecorderWithApproveCapability(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], recorderId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("recorder cannot be the sole approver"));
  }

  // --- Test 4: Approve with insufficient balance ---

  @Test
  void approveTransaction_insufficientBalance_returns400() {
    UUID[] txnId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Insufficient Client", "insufficient@test.com", recorderId));

                  // Deposit only 500
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("500.00"),
                          "DEP-INSUF-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  // Record payment for 2000
                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "PAY-INSUF-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Insufficient trust balance"));
  }

  // --- Test 5: Reject transitions to REJECTED with no ledger effect ---

  @Test
  void rejectTransaction_transitionsToRejectedWithNoLedgerEffect() {
    UUID[] txnId = new UUID[1];
    UUID[] customerId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reject Client", "reject@test.com", recorderId));
                  customerId[0] = customer.getId();

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("6000.00"),
                          "DEP-REJ-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "PAY-REJ-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      transactionService.rejectTransaction(
                          txnId[0], approverId, "Incorrect amount");

                  assertThat(response.status()).isEqualTo("REJECTED");
                  assertThat(response.rejectedBy()).isEqualTo(approverId);
                  assertThat(response.rejectedAt()).isNotNull();
                  assertThat(response.rejectionReason()).isEqualTo("Incorrect amount");

                  // Verify ledger was NOT affected
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId[0])
                          .orElseThrow();
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("6000.00"));
                  assertThat(ledger.getTotalPayments()).isEqualByComparingTo(BigDecimal.ZERO);
                }));
  }

  // --- Test 6: Approve non-AWAITING transaction returns 400 ---

  @Test
  void approveTransaction_nonAwaitingStatus_returns400() {
    UUID[] txnId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Already Approved Client", "already@test.com", recorderId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-DUP-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-DUP-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    // First approve should succeed
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> transactionService.approveTransaction(txnId[0], approverId)));

    // Second approve should fail
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("not in AWAITING_APPROVAL status"));
  }

  // --- Test 7: Fee transfer created with invoiceId ---

  @Test
  void recordFeeTransfer_createsTransactionWithInvoiceId() {
    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Fee Transfer Client", "feetransfer@test.com", recorderId));

                  // Fund the customer
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("15000.00"),
                          "DEP-FT-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  // Create a test invoice for the customer
                  var invoice = createTestInvoice(customer.getId(), InvoiceStatus.APPROVED);

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoice.getId(),
                              new BigDecimal("3500.00"),
                              "FT-001"));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("FEE_TRANSFER");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.invoiceId()).isEqualTo(invoice.getId());
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("3500.00"));
                  assertThat(response.customerId()).isEqualTo(customer.getId());
                }));
  }

  // --- Test 8: Refund created in AWAITING_APPROVAL ---

  @Test
  void recordRefund_createsTransactionInAwaitingApprovalStatus() {
    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Refund Client", "refund@test.com", recorderId));

                  // Fund the customer
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("7000.00"),
                          "DEP-REF-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var response =
                      transactionService.recordRefund(
                          trustAccountId,
                          new RecordRefundRequest(
                              customer.getId(),
                              new BigDecimal("2500.00"),
                              "REF-001",
                              "Client overpayment refund",
                              LocalDate.of(2026, 3, 3)));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("REFUND");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("2500.00"));
                  assertThat(response.customerId()).isEqualTo(customer.getId());
                  assertThat(response.reference()).isEqualTo("REF-001");
                }));
  }

  // --- Test 9: Approve payment emits audit event ---

  @Test
  void approveTransaction_emitsAuditEvent() {
    UUID[] txnId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Audit Approve Client", "auditapprove@test.com", recorderId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("4000.00"),
                          "DEP-AUD-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1500.00"),
                              "PAY-AUD-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long auditCountBefore = auditEventRepository.count();

                  transactionService.approveTransaction(txnId[0], approverId);

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  // --- Test 10: Rejection includes reason in response ---

  @Test
  void rejectTransaction_includesReasonInResponse() {
    UUID[] txnId = new UUID[1];

    runAsRecorder(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reason Client", "reason@test.com", recorderId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("3000.00"),
                          "DEP-RSN-001",
                          null,
                          LocalDate.of(2026, 3, 1)));

                  var payment =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-RSN-001",
                              null,
                              LocalDate.of(2026, 3, 2)));
                  txnId[0] = payment.id();
                }));

    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      transactionService.rejectTransaction(
                          txnId[0], approverId, "Client requested cancellation of payment");

                  assertThat(response.rejectionReason())
                      .isEqualTo("Client requested cancellation of payment");
                  assertThat(response.status()).isEqualTo("REJECTED");
                  assertThat(response.rejectedBy()).isEqualTo(approverId);
                }));
  }

  // --- Helpers ---

  private void runAsRecorder(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, recorderId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .where(RequestScopes.CAPABILITIES, Set.of("MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runAsRecorderWithApproveCapability(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, recorderId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .where(RequestScopes.CAPABILITIES, APPROVER_CAPABILITIES)
        .run(action);
  }

  private void runAsApprover(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, approverId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, APPROVER_CAPABILITIES)
        .run(action);
  }

  /**
   * Creates a test invoice for the given customer with the specified status. Creates as DRAFT
   * first, then transitions to APPROVED or SENT if needed.
   */
  private Invoice createTestInvoice(UUID customerId, InvoiceStatus targetStatus) {
    var invoice =
        new Invoice(
            customerId, "ZAR", "Test Customer", "test@test.com", null, "Test Org", recorderId);
    invoice = invoiceRepository.save(invoice);

    if (targetStatus == InvoiceStatus.APPROVED || targetStatus == InvoiceStatus.SENT) {
      var invoiceNumber = "INV-TEST-" + UUID.randomUUID().toString().substring(0, 8);
      invoice.approve(invoiceNumber, recorderId);
      invoice = invoiceRepository.save(invoice);
    }

    if (targetStatus == InvoiceStatus.SENT) {
      invoice.markSent();
      invoice = invoiceRepository.save(invoice);
    }

    return invoice;
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
