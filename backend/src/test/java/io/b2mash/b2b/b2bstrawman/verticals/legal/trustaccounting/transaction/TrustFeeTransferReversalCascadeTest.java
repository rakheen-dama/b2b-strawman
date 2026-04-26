package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentPartiallyReversedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentReversedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceTransitionService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import jakarta.persistence.EntityManager;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GAP-L-70: cascading FEE_TRANSFER trust transaction reversals back into the
 * invoice domain. Confirms that the trust ledger and the linked invoice always tie out — when a
 * trust reversal succeeds, the invoice either flips PAID -> SENT (single-payment) or stays PAID
 * with one payment_event removed (multi-payment partial reversal). Both writes share the
 * same @Transactional boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustFeeTransferReversalCascadeTest {

  private static final String ORG_ID = "org_l70_cascade_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private InvoiceTransitionService invoiceTransitionService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditService auditService;
  @Autowired private EntityManager entityManager;
  @Autowired private ApplicationEvents applicationEvents;

  private String tenantSchema;
  private UUID memberId;
  private UUID approverId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "L70 Cascade Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_l70_owner", "l70@test.com", "L70 Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_l70_approver",
                "l70_approver@test.com",
                "L70 Approver",
                "owner"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "L70 Trust",
                          "FNB",
                          "250655",
                          "L70-ACC",
                          "GENERAL",
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "L70 cascade test account");
                  var response = trustAccountService.createTrustAccount(request);
                  trustAccountId = response.id();
                }));
  }

  @Test
  void singlePaymentReversal_flipsInvoiceFromPaidToSentAndDeletesPaymentEvent() {
    UUID[] invoiceIdHolder = new UUID[1];
    UUID[] feeTransferTxnIdHolder = new UUID[1];
    UUID[] customerIdHolder = new UUID[1];
    UUID[] projectIdHolder = new UUID[1];
    String reference = "FT-L70-SINGLE";

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  UUID projectId = UUID.randomUUID();
                  insertProject(projectId, "L70 Single Project");
                  projectIdHolder[0] = projectId;

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L70 Single Client", "l70_single@test.com", memberId));
                  customerIdHolder[0] = customer.getId();

                  // Fund the trust ledger
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectId,
                          new BigDecimal("5000.00"),
                          "DEP-L70-SINGLE",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  // Create a SENT invoice with a project-bound line
                  UUID invoiceId = UUID.randomUUID();
                  invoiceIdHolder[0] = invoiceId;
                  insertInvoice(invoiceId, customer.getId(), "INV-L70-SINGLE");
                  insertInvoiceLine(invoiceId, projectId, new BigDecimal("1150.00"));
                  entityManager.flush();

                  // Record the fee transfer (lands in AWAITING_APPROVAL)
                  var ftResponse =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(), invoiceId, new BigDecimal("1150.00"), reference));
                  feeTransferTxnIdHolder[0] = ftResponse.id();
                }));

    // Approve as a different member to trigger the invoice transition + payment_event write
    runAsApproverWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  transactionService.approveTransaction(feeTransferTxnIdHolder[0], approverId);

                  // Sanity: invoice flipped to PAID, payment_event was created
                  var invoice = invoiceRepository.findById(invoiceIdHolder[0]).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                  assertThat(invoice.getPaidAt()).isNotNull();
                  assertThat(invoice.getPaymentReference()).isEqualTo(reference);

                  var paymentEvents =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          invoiceIdHolder[0], PaymentEventStatus.COMPLETED);
                  assertThat(paymentEvents).hasSize(1);
                  assertThat(paymentEvents.get(0).getPaymentReference()).isEqualTo(reference);
                }));

    // Reverse the FEE_TRANSFER — invoice should snap back to SENT
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  applicationEvents.clear();

                  transactionService.reverseTransaction(
                      feeTransferTxnIdHolder[0], "Filed against wrong invoice");

                  var invoice = invoiceRepository.findById(invoiceIdHolder[0]).orElseThrow();
                  assertThat(invoice.getStatus())
                      .as("invoice flips PAID -> SENT")
                      .isEqualTo(InvoiceStatus.SENT);
                  assertThat(invoice.getPaidAt()).as("paid_at cleared").isNull();
                  assertThat(invoice.getPaymentReference())
                      .as("payment_reference cleared")
                      .isNull();

                  var remaining =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          invoiceIdHolder[0], PaymentEventStatus.COMPLETED);
                  assertThat(remaining).as("payment_event row deleted").isEmpty();
                }));

    // Verify the InvoicePaymentReversedEvent was published
    var reversedEvents = applicationEvents.stream(InvoicePaymentReversedEvent.class).toList();
    assertThat(reversedEvents).as("InvoicePaymentReversedEvent published").hasSize(1);
    var event = reversedEvents.get(0);
    assertThat(event.entityId()).isEqualTo(invoiceIdHolder[0]);
    assertThat(event.customerId()).isEqualTo(customerIdHolder[0]);
    assertThat(event.projectId())
        .as("projectId inferred from invoice line")
        .isEqualTo(projectIdHolder[0]);
    assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("1150.00"));

    // Verify INVOICE_PAYMENT_REVERSED audit row written (event-type: invoice.payment_reversed)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var auditPage =
                      auditService.findEvents(
                          new AuditEventFilter(
                              "invoice",
                              invoiceIdHolder[0],
                              null,
                              "invoice.payment_reversed",
                              null,
                              null),
                          PageRequest.of(0, 10));
                  assertThat(auditPage.getContent())
                      .as("invoice.payment_reversed audit event written")
                      .hasSize(1);
                  var auditEvent = auditPage.getContent().get(0);
                  assertThat(auditEvent.getEventType()).isEqualTo("invoice.payment_reversed");
                  assertThat(auditEvent.getEntityId()).isEqualTo(invoiceIdHolder[0]);
                  assertThat(auditEvent.getDetails())
                      .containsEntry("partial", "false")
                      .containsEntry("amount", "1150.00")
                      .containsEntry("project_id", projectIdHolder[0].toString());
                }));
  }

  @Test
  void multiPaymentReversal_keepsInvoicePaidAndDeletesOnlyOnePaymentEvent() {
    UUID[] invoiceIdHolder = new UUID[1];
    UUID[] feeTransferTxnIdHolder = new UUID[1];
    UUID extraPaymentEventIdHolder[] = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  UUID projectId = UUID.randomUUID();
                  insertProject(projectId, "L70 Multi Project");

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L70 Multi Client", "l70_multi@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectId,
                          new BigDecimal("5000.00"),
                          "DEP-L70-MULTI",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  UUID invoiceId = UUID.randomUUID();
                  invoiceIdHolder[0] = invoiceId;
                  insertInvoice(invoiceId, customer.getId(), "INV-L70-MULTI");
                  insertInvoiceLine(invoiceId, projectId, new BigDecimal("2000.00"));
                  entityManager.flush();

                  // Seed an extra COMPLETED payment_event (e.g. earlier card payment) so the
                  // post-reversal count is non-zero — this is the multi-payment scenario.
                  var extraEvent =
                      new PaymentEvent(
                          invoiceId,
                          "manual",
                          null,
                          PaymentEventStatus.COMPLETED,
                          new BigDecimal("500.00"),
                          "ZAR",
                          "OPERATING");
                  extraEvent.setPaymentReference("PRIOR-CARD-PAYMENT");
                  paymentEventRepository.save(extraEvent);
                  extraPaymentEventIdHolder[0] = extraEvent.getId();

                  // Fee transfer for the residual — lands in AWAITING_APPROVAL
                  var ftResponse =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("2000.00"),
                              "FT-L70-MULTI"));
                  feeTransferTxnIdHolder[0] = ftResponse.id();
                }));

    // Approve as a different member to flip invoice and write payment_event
    runAsApproverWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  transactionService.approveTransaction(feeTransferTxnIdHolder[0], approverId);

                  // Confirm two completed payment_events exist before reversal
                  assertThat(
                          paymentEventRepository.findByInvoiceIdAndStatus(
                              invoiceIdHolder[0], PaymentEventStatus.COMPLETED))
                      .hasSize(2);
                }));

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  applicationEvents.clear();

                  transactionService.reverseTransaction(
                      feeTransferTxnIdHolder[0], "Trust transfer was wrong amount");

                  // Invoice stays PAID — the prior card payment still satisfies the balance
                  var invoice = invoiceRepository.findById(invoiceIdHolder[0]).orElseThrow();
                  assertThat(invoice.getStatus())
                      .as("invoice stays PAID — other payment events remain")
                      .isEqualTo(InvoiceStatus.PAID);

                  // Only the trust fee_transfer payment_event was deleted; the prior one survives
                  var remaining =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          invoiceIdHolder[0], PaymentEventStatus.COMPLETED);
                  assertThat(remaining).hasSize(1);
                  assertThat(remaining.get(0).getId()).isEqualTo(extraPaymentEventIdHolder[0]);
                  assertThat(remaining.get(0).getPaymentReference())
                      .isEqualTo("PRIOR-CARD-PAYMENT");
                }));

    // Verify InvoicePaymentPartiallyReversedEvent was published (NOT InvoicePaymentReversedEvent)
    assertThat(applicationEvents.stream(InvoicePaymentPartiallyReversedEvent.class).toList())
        .as("InvoicePaymentPartiallyReversedEvent published")
        .hasSize(1);
    assertThat(applicationEvents.stream(InvoicePaymentReversedEvent.class).toList())
        .as("InvoicePaymentReversedEvent NOT published in multi-payment case")
        .isEmpty();

    // Verify INVOICE_PAYMENT_PARTIALLY_REVERSED audit row was written
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var auditPage =
                      auditService.findEvents(
                          new AuditEventFilter(
                              "invoice",
                              invoiceIdHolder[0],
                              null,
                              "invoice.payment_partially_reversed",
                              null,
                              null),
                          PageRequest.of(0, 10));
                  assertThat(auditPage.getContent())
                      .as("invoice.payment_partially_reversed audit event written")
                      .hasSize(1);
                  assertThat(auditPage.getContent().get(0).getDetails())
                      .containsEntry("partial", "true");
                }));
  }

  @Test
  void reversalOfNonFeeTransferDebitWithNoInvoiceLink_isNoOpOnInvoiceDomain() {
    UUID[] paymentTxnIdHolder = new UUID[1];

    // A non-FEE_TRANSFER debit (PAYMENT) — no invoice involvement at all
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L70 NoInv Client", "l70_noinv@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-L70-NOINV",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  var paymentResponse =
                      transactionService.recordPayment(
                          trustAccountId,
                          new TrustTransactionService.RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-L70-NOINV",
                              "Direct payment, no invoice",
                              LocalDate.of(2026, 4, 25)));
                  paymentTxnIdHolder[0] = paymentResponse.id();

                  // Approve the payment
                  var txn = transactionRepository.findById(paymentTxnIdHolder[0]).orElseThrow();
                  txn.setStatus("APPROVED");
                  transactionRepository.save(txn);
                }));

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  applicationEvents.clear();

                  // Reverse — should succeed without touching the invoice domain
                  transactionService.reverseTransaction(paymentTxnIdHolder[0], "Bank error");

                  // No invoice events published
                  assertThat(applicationEvents.stream(InvoicePaymentReversedEvent.class).toList())
                      .isEmpty();
                  assertThat(
                          applicationEvents.stream(InvoicePaymentPartiallyReversedEvent.class)
                              .toList())
                      .isEmpty();
                }));
  }

  @Test
  void reversingAlreadyReversedPaymentEvent_throwsInvalidStateException() {
    UUID[] invoiceIdHolder = new UUID[1];
    UUID[] paymentEventIdHolder = new UUID[1];

    // Seed invoice + completed payment_event in its own committed tx
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  UUID projectId = UUID.randomUUID();
                  insertProject(projectId, "L70 Idempotent Project");

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L70 Idem Client", "l70_idem@test.com", memberId));

                  UUID invoiceId = UUID.randomUUID();
                  invoiceIdHolder[0] = invoiceId;
                  insertInvoice(invoiceId, customer.getId(), "INV-L70-IDEM");
                  insertInvoiceLine(invoiceId, projectId, new BigDecimal("500.00"));
                  // Flip invoice to PAID via direct entity manipulation
                  entityManager
                      .createNativeQuery(
                          "UPDATE invoices SET status = 'PAID', paid_at = now(),"
                              + " payment_reference = 'IDEM-REF' WHERE id = :id")
                      .setParameter("id", invoiceId)
                      .executeUpdate();

                  var paymentEvent =
                      new PaymentEvent(
                          invoiceId,
                          "trust_fee_transfer",
                          null,
                          PaymentEventStatus.COMPLETED,
                          new BigDecimal("500.00"),
                          "ZAR",
                          "TRUST");
                  paymentEvent.setPaymentReference("IDEM-REF");
                  paymentEventRepository.save(paymentEvent);
                  entityManager.flush();
                  paymentEventIdHolder[0] = paymentEvent.getId();
                }));

    // First reversal — runs in its own @Transactional inside the service. Invoice -> SENT.
    runInTenantWithCapabilities(
        () -> invoiceTransitionService.reversePayment(invoiceIdHolder[0], paymentEventIdHolder[0]));

    // Second reversal of the same payment_event ID — the payment_event row is gone AND the
    // invoice is now SENT. The status guard rejects first with InvalidStateException (NOT
    // silent re-success). assertThatThrownBy outside any tx template so the assertion does not
    // get rolled back.
    runInTenantWithCapabilities(
        () ->
            assertThatThrownBy(
                    () ->
                        invoiceTransitionService.reversePayment(
                            invoiceIdHolder[0], paymentEventIdHolder[0]))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("paid invoices"));

    // Also exercise: a fresh COMPLETED payment_event on a non-PAID invoice still rejects.
    UUID[] orphanEventIdHolder = new UUID[1];
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var orphanEvent =
                      new PaymentEvent(
                          invoiceIdHolder[0],
                          "manual",
                          null,
                          PaymentEventStatus.COMPLETED,
                          new BigDecimal("500.00"),
                          "ZAR",
                          "OPERATING");
                  orphanEvent.setPaymentReference("ORPHAN-REF");
                  paymentEventRepository.save(orphanEvent);
                  entityManager.flush();
                  orphanEventIdHolder[0] = orphanEvent.getId();
                }));

    runInTenantWithCapabilities(
        () ->
            assertThatThrownBy(
                    () ->
                        invoiceTransitionService.reversePayment(
                            invoiceIdHolder[0], orphanEventIdHolder[0]))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("paid invoices"));
  }

  // --- helpers ---

  private void insertProject(UUID projectId, String name) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO projects (id, name, description, status, created_by, created_at, updated_at)
            VALUES (:id, :name, 'L70 test', 'ACTIVE', :memberId, now(), now())
            """)
        .setParameter("id", projectId)
        .setParameter("name", name)
        .setParameter("memberId", memberId)
        .executeUpdate();
  }

  private void insertInvoice(UUID invoiceId, UUID customerId, String invoiceNumber) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                subtotal, tax_amount, total, due_date,
                customer_name, org_name, payment_destination, created_by, created_at, updated_at)
            VALUES (:id, :customerId, :invoiceNumber, 'SENT', 'ZAR',
                1000.00, 150.00, 1150.00, '2026-05-30',
                'L70 Client', 'Test Org', 'OPERATING', :memberId, now(), now())
            """)
        .setParameter("id", invoiceId)
        .setParameter("customerId", customerId)
        .setParameter("invoiceNumber", invoiceNumber)
        .setParameter("memberId", memberId)
        .executeUpdate();
  }

  private void insertInvoiceLine(UUID invoiceId, UUID projectId, BigDecimal amount) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                description, quantity, unit_price, amount, sort_order, tax_exempt,
                created_at, updated_at)
            VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                'L70 work', 1, :amount, :amount, 0, false,
                now(), now())
            """)
        .setParameter("invoiceId", invoiceId)
        .setParameter("projectId", projectId)
        .setParameter("amount", amount)
        .executeUpdate();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private void runInTenantWithCapabilities(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runAsApproverWithCapabilities(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, approverId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }
}
