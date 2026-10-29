package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePartiallyPaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentPartiallyReversedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentReversedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceTransitionService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentPosting;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * trust reversal succeeds, the invoice either flips PAID -> SENT (single-payment) or, with one
 * payment_event removed (multi-payment partial reversal), recomputes amount_paid from the remaining
 * COMPLETED events — PARTIALLY_PAID when they no longer cover the total (Phase 88 U15, ADR-347).
 * Both writes share the same @Transactional boundary. Also pins the approval side (622C, N-5): a
 * fee transfer posts its real amount through postPayment (TRUST_TRANSFER) — PARTIALLY_PAID when it
 * leaves a balance, capped at PAID + {@code invoice.payment_excess} when it exceeds it.
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
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
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
                  assertThat(paymentEvents)
                      .as("postPayment writes the one trust event — no double count")
                      .hasSize(1);
                  assertThat(paymentEvents.get(0).getPaymentReference()).isEqualTo(reference);
                  assertThat(paymentEvents.get(0).getPaymentMethod())
                      .isEqualTo(PaymentMethod.TRUST_TRANSFER);
                  assertThat(paymentEvents.get(0).getProviderSlug())
                      .isEqualTo(PaymentPosting.TRUST_PROVIDER);
                  assertThat(paymentEvents.get(0).getPaymentDestination()).isEqualTo("TRUST");
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1150.00");
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
  void multiPaymentReversal_leavesInvoicePartiallyPaidAndDeletesOnlyOnePaymentEvent() {
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

                  // ADR-347 A4: the 2 000.00 transfer exceeds the 1 150.00 balance — the fee note
                  // is capped at PAID and the 850.00 excess is audited.
                  var invoice = invoiceRepository.findById(invoiceIdHolder[0]).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1150.00");
                  assertThat(auditDetails(invoiceIdHolder[0], "invoice.payment_excess"))
                      .singleElement()
                      .satisfies(
                          d ->
                              assertThat(d)
                                  .containsEntry("excess", "850.00")
                                  .containsEntry("amount", "2000.00")
                                  .containsEntry("payment_method", "TRUST_TRANSFER"));
                }));

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  applicationEvents.clear();

                  transactionService.reverseTransaction(
                      feeTransferTxnIdHolder[0], "Trust transfer was wrong amount");

                  // ADR-347 (Phase 88 U15): amount_paid is recomputed from the remaining COMPLETED
                  // events — the prior 500.00 no longer covers the total, so the fee note is
                  // PARTIALLY_PAID (pre-U15 it stayed PAID regardless of the remaining sum).
                  var invoice = invoiceRepository.findById(invoiceIdHolder[0]).orElseThrow();
                  assertThat(invoice.getStatus())
                      .as("invoice PARTIALLY_PAID — remaining payments are below the total")
                      .isEqualTo(InvoiceStatus.PARTIALLY_PAID);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("500.00");
                  assertThat(invoice.getPaidAt()).isNull();
                  assertThat(invoice.getPaymentReference())
                      .as("reference follows the surviving payment, not the reversed transfer")
                      .isEqualTo("PRIOR-CARD-PAYMENT");

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

  /**
   * Phase 88 622C (ADR-347 A4, OQ-1 A): a fee transfer recorded for the full total while the fee
   * note is SENT, approved after a manual part payment landed. The transfer posts its real amount
   * through postPayment — the fee note is capped at PAID and the 500.00 excess is audited as {@code
   * invoice.payment_excess}; trust is debited the transfer amount. (622B's interim D7 refusal was a
   * stopgap for the deleted legacy full-settle path.)
   */
  @Test
  void feeTransferAboveBalanceDueAtApproval_isCappedAtPaidWithExcessAudit() {
    var note =
        seedFundedFeeNote(
            "D7",
            new BigDecimal("1000.00"),
            new BigDecimal("150.00"),
            new BigDecimal("1150.00"),
            new BigDecimal("5000.00"));
    UUID txnId = recordTransfer(note, new BigDecimal("1150.00"), "FT-D7-PARTIAL");

    // A manual part payment lands before the transfer is approved → PARTIALLY_PAID, 500 paid
    runInTenantWithCapabilities(
        () ->
            invoiceTransitionService.postPayment(
                note.invoiceId(),
                PaymentPosting.manual(new BigDecimal("500.00"), PaymentMethod.EFT, "EFT-D7")));

    approve(txnId);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = invoiceRepository.findById(note.invoiceId()).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1150.00");
                  assertThat(invoice.getBalanceDue()).isEqualByComparingTo("0.00");
                  assertThat(invoice.getPaidAt()).isNotNull();

                  var completed =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          note.invoiceId(), PaymentEventStatus.COMPLETED);
                  assertThat(completed)
                      .extracting(PaymentEvent::getPaymentReference)
                      .containsExactlyInAnyOrder("EFT-D7", "FT-D7-PARTIAL");
                  var trustEvent =
                      completed.stream()
                          .filter(pe -> "FT-D7-PARTIAL".equals(pe.getPaymentReference()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(trustEvent.getAmount())
                      .as("the event records the real transfer amount")
                      .isEqualByComparingTo("1150.00");
                  assertThat(trustEvent.getPaymentMethod()).isEqualTo(PaymentMethod.TRUST_TRANSFER);

                  assertThat(auditDetails(note.invoiceId(), "invoice.payment_excess"))
                      .singleElement()
                      .satisfies(
                          d ->
                              assertThat(d)
                                  .containsEntry("excess", "500.00")
                                  .containsEntry("amount", "1150.00")
                                  .containsEntry("payment_method", "TRUST_TRANSFER"));

                  assertThat(ledgerBalance(note.customerId()))
                      .as("trust debited the transfer amount")
                      .isEqualByComparingTo("3850.00");
                  assertThat(transactionRepository.findById(txnId).orElseThrow().getStatus())
                      .isEqualTo("APPROVED");
                }));
  }

  /**
   * N-5 (Phase 88 U15): a R 12 000 fee transfer against a R 42 182 fee note posts R 12 000 — the
   * fee note is PARTIALLY_PAID, never PAID.
   */
  @Test
  void partialFeeTransfer_leavesFeeNotePartiallyPaidWithRealAmount() {
    var note = seedN5FeeNote("N5");
    UUID txnId = recordTransfer(note, new BigDecimal("12000.00"), "FT-N5");

    applicationEvents.clear();
    approve(txnId);

    var partiallyPaidEvents =
        applicationEvents.stream(InvoicePartiallyPaidEvent.class)
            .filter(e -> note.invoiceId().equals(e.entityId()))
            .toList();
    assertThat(partiallyPaidEvents).hasSize(1);
    assertThat(
            applicationEvents.stream(InvoicePaidEvent.class)
                .filter(e -> note.invoiceId().equals(e.entityId())))
        .isEmpty();
    var syncEvents =
        applicationEvents.stream(InvoiceSyncEvent.class)
            .filter(e -> note.invoiceId().equals(e.getInvoiceId()))
            .toList();
    assertThat(syncEvents).hasSize(1);
    assertThat(syncEvents.get(0).getStatus()).isEqualTo("PARTIALLY_PAID");
    assertThat(syncEvents.get(0).getAmountPaid()).isEqualByComparingTo("12000.00");

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = invoiceRepository.findById(note.invoiceId()).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("12000.00");
                  assertThat(invoice.getBalanceDue()).isEqualByComparingTo("30182.00");
                  assertThat(invoice.getPaidAt()).isNull();

                  var completed =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          note.invoiceId(), PaymentEventStatus.COMPLETED);
                  assertThat(completed).hasSize(1);
                  var event = completed.get(0);
                  assertThat(event.getAmount()).isEqualByComparingTo("12000.00");
                  assertThat(event.getPaymentMethod()).isEqualTo(PaymentMethod.TRUST_TRANSFER);
                  assertThat(event.getProviderSlug()).isEqualTo("trust_fee_transfer");
                  assertThat(event.getPaymentDestination()).isEqualTo("TRUST");
                  assertThat(event.getPaymentReference()).isEqualTo("FT-N5");

                  assertThat(ledgerBalance(note.customerId())).isEqualByComparingTo("38000.00");

                  assertThat(auditDetails(note.invoiceId(), "invoice.partially_paid"))
                      .singleElement()
                      .satisfies(
                          d ->
                              assertThat(d)
                                  .containsEntry("amount", "12000.00")
                                  .containsEntry("payment_method", "TRUST_TRANSFER")
                                  .containsEntry("balance_due", "30182.00"));
                  assertThat(auditDetails(note.invoiceId(), "invoice.payment_excess")).isEmpty();
                }));
  }

  /**
   * 622B D7 lifted + OQ-2: a remainder transfer can be recorded and approved on a PARTIALLY_PAID
   * fee note; it clears the balance exactly (no excess).
   */
  @Test
  void feeTransferOnPartiallyPaidFeeNote_isApproved() {
    var note =
        seedFundedFeeNote(
            "PP",
            new BigDecimal("1000.00"),
            new BigDecimal("150.00"),
            new BigDecimal("1150.00"),
            new BigDecimal("5000.00"));

    runInTenantWithCapabilities(
        () ->
            invoiceTransitionService.postPayment(
                note.invoiceId(),
                PaymentPosting.manual(new BigDecimal("500.00"), PaymentMethod.EFT, "EFT-PP")));

    // Recorded while the fee note is PARTIALLY_PAID (OQ-2)
    UUID txnId = recordTransfer(note, new BigDecimal("650.00"), "FT-PP-REMAINDER");
    approve(txnId);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = invoiceRepository.findById(note.invoiceId()).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1150.00");
                  assertThat(invoice.getPaidAt()).isNotNull();
                  assertThat(invoice.getPaymentReference()).isEqualTo("FT-PP-REMAINDER");

                  var completed =
                      paymentEventRepository.findByInvoiceIdAndStatus(
                          note.invoiceId(), PaymentEventStatus.COMPLETED);
                  assertThat(completed)
                      .extracting(PaymentEvent::getPaymentReference)
                      .containsExactlyInAnyOrder("EFT-PP", "FT-PP-REMAINDER");
                  assertThat(auditDetails(note.invoiceId(), "invoice.payment_excess")).isEmpty();

                  assertThat(ledgerBalance(note.customerId())).isEqualByComparingTo("4350.00");
                }));
  }

  /** Reversing a part fee transfer returns the fee note to SENT and restores the trust ledger. */
  @Test
  void reversalOfPartialFeeTransfer_returnsFeeNoteToSent() {
    var note = seedN5FeeNote("N5-REV");
    UUID txnId = recordTransfer(note, new BigDecimal("12000.00"), "FT-N5-REV");
    approve(txnId);

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  applicationEvents.clear();
                  transactionService.reverseTransaction(txnId, "Wrong matter");

                  var invoice = invoiceRepository.findById(note.invoiceId()).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
                  assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
                  assertThat(invoice.getPaidAt()).isNull();
                  assertThat(
                          paymentEventRepository.findByInvoiceIdAndStatus(
                              note.invoiceId(), PaymentEventStatus.COMPLETED))
                      .isEmpty();
                }));

    assertThat(
            applicationEvents.stream(InvoicePaymentReversedEvent.class)
                .filter(e -> note.invoiceId().equals(e.entityId()))
                .toList())
        .singleElement()
        .satisfies(e -> assertThat(e.amount()).isEqualByComparingTo("12000.00"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  assertThat(auditDetails(note.invoiceId(), "invoice.payment_reversed"))
                      .singleElement()
                      .satisfies(
                          d ->
                              assertThat(d)
                                  .containsEntry("partial", "false")
                                  .containsEntry("amount", "12000.00"));
                  assertThat(ledgerBalance(note.customerId())).isEqualByComparingTo("50000.00");
                }));
  }

  /** A funded client + a SENT fee note with one project-bound line. */
  private record SeededNote(UUID invoiceId, UUID customerId, UUID projectId) {}

  private SeededNote seedN5FeeNote(String label) {
    return seedFundedFeeNote(
        label,
        new BigDecimal("36680.00"),
        new BigDecimal("5502.00"),
        new BigDecimal("42182.00"),
        new BigDecimal("50000.00"));
  }

  private SeededNote seedFundedFeeNote(
      String label, BigDecimal subtotal, BigDecimal tax, BigDecimal total, BigDecimal deposit) {
    SeededNote[] holder = new SeededNote[1];
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  UUID projectId = UUID.randomUUID();
                  insertProject(projectId, label + " Project");
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              label + " Client",
                              label.toLowerCase(Locale.ROOT) + "_ft@test.com",
                              memberId));
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectId,
                          deposit,
                          "DEP-" + label,
                          "Funding",
                          LocalDate.of(2026, 4, 25)));
                  UUID invoiceId = UUID.randomUUID();
                  insertInvoice(invoiceId, customer.getId(), "INV-" + label, subtotal, tax, total);
                  insertInvoiceLine(invoiceId, projectId, subtotal);
                  entityManager.flush();
                  holder[0] = new SeededNote(invoiceId, customer.getId(), projectId);
                }));
    return holder[0];
  }

  private UUID recordTransfer(SeededNote note, BigDecimal amount, String reference) {
    UUID[] holder = new UUID[1];
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    holder[0] =
                        transactionService
                            .recordFeeTransfer(
                                trustAccountId,
                                new RecordFeeTransferRequest(
                                    note.customerId(), note.invoiceId(), amount, reference))
                            .id()));
    return holder[0];
  }

  /** Approves as the second owner (the recorder cannot approve their own transaction). */
  private void approve(UUID txnId) {
    runAsApproverWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> transactionService.approveTransaction(txnId, approverId)));
  }

  private BigDecimal ledgerBalance(UUID customerId) {
    return ledgerCardRepository
        .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
        .orElseThrow()
        .getBalance();
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
                .hasMessageContaining("paid fee notes"));

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
                .hasMessageContaining("paid fee notes"));
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
    insertInvoice(
        invoiceId,
        customerId,
        invoiceNumber,
        new BigDecimal("1000.00"),
        new BigDecimal("150.00"),
        new BigDecimal("1150.00"));
  }

  private void insertInvoice(
      UUID invoiceId,
      UUID customerId,
      String invoiceNumber,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal total) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                subtotal, tax_amount, total, due_date,
                customer_name, org_name, payment_destination, created_by, created_at, updated_at)
            VALUES (:id, :customerId, :invoiceNumber, 'SENT', 'ZAR',
                :subtotal, :tax, :total, '2026-05-30',
                'L70 Client', 'Test Org', 'OPERATING', :memberId, now(), now())
            """)
        .setParameter("id", invoiceId)
        .setParameter("customerId", customerId)
        .setParameter("invoiceNumber", invoiceNumber)
        .setParameter("subtotal", subtotal)
        .setParameter("tax", tax)
        .setParameter("total", total)
        .setParameter("memberId", memberId)
        .executeUpdate();
  }

  /**
   * Details of every audit row of {@code eventType} on this invoice (id-scoped, no count-bleed).
   */
  private List<Map<String, Object>> auditDetails(UUID invoiceId, String eventType) {
    return auditService
        .findEvents(
            new AuditEventFilter("invoice", invoiceId, null, eventType, null, null, null),
            PageRequest.of(0, 10))
        .getContent()
        .stream()
        .map(e -> e.getDetails())
        .toList();
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
