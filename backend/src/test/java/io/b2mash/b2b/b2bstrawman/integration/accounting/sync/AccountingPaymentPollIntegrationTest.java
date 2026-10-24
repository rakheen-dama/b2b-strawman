package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.ExternalPaymentEvent;
import io.b2mash.b2b.b2bstrawman.integration.accounting.NoOpAccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPaymentPollIntegrationTest {

  private static final String ORG_ID = "org_poll_integration_test";

  @MockitoBean private IntegrationRegistry integrationRegistry;

  @MockitoBean(name = "noOpAccountingProvider")
  private AccountingProvider accountingProvider;

  @Autowired private AccountingSyncService syncService;
  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private MockMvc mockMvc;

  private String tenantSchema;
  private UUID memberId;
  private UUID connectionId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Poll Integration Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    String memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_poll_integ",
            "poll-integ@test.com",
            "Poll Integ Tester",
            "owner");
    memberId = UUID.fromString(memberIdStr);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(this::createConnectedXeroConnection);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrationRepository.save(orgIntegration);

    var connection =
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email");
    connectionId = xeroConnectionRepository.save(connection).getId();
  }

  /**
   * Creates a SENT invoice with a specific total, plus a completed PUSH sync entry linking the
   * invoice to an external reference. Returns the invoice ID.
   */
  private UUID createSentInvoiceWithPushEntry(
      String customerName, BigDecimal total, String externalRef) {
    var customer =
        TestCustomerFactory.createActiveCustomer(
            customerName, customerName + "@test.com", memberId);
    var savedCustomer = customerRepository.save(customer);

    var invoice =
        new Invoice(savedCustomer.getId(), "USD", customerName, null, null, "Test Org", memberId);
    // Set up a non-zero total
    invoice.recalculateTotals(total, false, BigDecimal.ZERO, false);
    var savedInvoice = invoiceRepository.save(invoice);

    // Add a line item so the invoice is valid for approval
    var line =
        new InvoiceLine(savedInvoice.getId(), null, null, "Test service", BigDecimal.ONE, total, 0);
    invoiceLineRepository.save(line);

    // Transition: DRAFT -> APPROVED -> SENT
    savedInvoice.approve(
        "INV-POLL-" + savedInvoice.getId().toString().substring(0, 6),
        memberId,
        BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);
    savedInvoice.markSent();
    savedInvoice = invoiceRepository.save(savedInvoice);

    // Create a completed PUSH sync entry with the external reference
    var pushEntry =
        new AccountingSyncEntry(
            SyncEntityType.INVOICE,
            savedInvoice.getId(),
            "xero",
            SyncDirection.PUSH,
            SyncTrigger.EVENT,
            externalRef);
    pushEntry.markInFlight();
    pushEntry.markCompleted("XERO-INV-" + savedInvoice.getId().toString().substring(0, 8));
    syncEntryRepository.save(pushEntry);

    return savedInvoice.getId();
  }

  private void stubPaymentSource(AccountingPaymentSource paymentSource) {
    // The service resolves AccountingPaymentSource directly per ADR-279
    when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
        .thenReturn(paymentSource);
  }

  @Test
  void happyPath_matchesPaymentAndTransitionsInvoiceToPaid() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-HAPPY-" + UUID.randomUUID().toString().substring(0, 6);
          BigDecimal amount = new BigDecimal("500.00");
          UUID invoiceId = createSentInvoiceWithPushEntry("Happy Path Customer", amount, extRef);

          String xeroPaymentId = "XERO-PAY-" + UUID.randomUUID().toString().substring(0, 8);
          var paymentEvent =
              new ExternalPaymentEvent(extRef, xeroPaymentId, amount, "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isEqualTo(1);
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isZero();

          // Invoice should now be PAID
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

          // PaymentEvent should exist
          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).hasSize(1);
          assertThat(events.getFirst().getProviderSlug()).isEqualTo("xero");
          assertThat(events.getFirst().getPaymentReference()).isEqualTo(xeroPaymentId);
          assertThat(events.getFirst().getStatus()).isEqualTo(PaymentEventStatus.COMPLETED);
          assertThat(events.getFirst().getPaymentDestination()).isEqualTo("OPERATING");
          assertThat(events.getFirst().getPaymentMethod()).isEqualTo(PaymentMethod.XERO);
          assertThat(events.getFirst().getAmount()).isEqualByComparingTo("500.00");
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo("500.00");

          // PULL sync entry should exist
          var pullEntries =
              syncEntryRepository.findByEntity(SyncEntityType.PAYMENT_PULL, invoiceId);
          assertThat(pullEntries).hasSize(1);
          assertThat(pullEntries.getFirst().getState()).isEqualTo(SyncState.COMPLETED);
          assertThat(pullEntries.getFirst().getDirection()).isEqualTo(SyncDirection.PULL);
          assertThat(pullEntries.getFirst().getExternalId()).isEqualTo(xeroPaymentId);
        });
  }

  /**
   * Phase 88 622C (ADR-347 A3): a Xero payment below the total is a real part payment — posted as
   * XERO, the fee note left PARTIALLY_PAID (pre-622C this was classed as amount drift).
   */
  @Test
  void partialXeroPayment_leavesFeeNotePartiallyPaid() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-PART-" + UUID.randomUUID().toString().substring(0, 6);
          UUID invoiceId =
              createSentInvoiceWithPushEntry(
                  "Partial Xero Customer", new BigDecimal("1000.00"), extRef);

          var paymentEvent =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-PART", new BigDecimal("999.00"), "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isEqualTo(1);
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isZero();

          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo("999.00");
          assertThat(invoice.getBalanceDue()).isEqualByComparingTo("1.00");

          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).hasSize(1);
          assertThat(events.getFirst().getPaymentMethod()).isEqualTo(PaymentMethod.XERO);
          assertThat(events.getFirst().getProviderSlug()).isEqualTo("xero");
          assertThat(events.getFirst().getAmount()).isEqualByComparingTo("999.00");
          assertThat(events.getFirst().getPaymentReference()).isEqualTo("XERO-PAY-PART");

          var pullEntries =
              syncEntryRepository.findByEntity(SyncEntityType.PAYMENT_PULL, invoiceId);
          assertThat(pullEntries).hasSize(1);
          assertThat(pullEntries.getFirst().getState()).isEqualTo(SyncState.COMPLETED);
        });
  }

  /**
   * §13.4.7: the payable gate is SENT | PARTIALLY_PAID — a second Xero payment completes a
   * part-paid fee note (pre-622C it was skipped as "not SENT").
   */
  @Test
  void secondXeroPayment_completesPartiallyPaidFeeNote() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-TWO-" + UUID.randomUUID().toString().substring(0, 6);
          UUID invoiceId =
              createSentInvoiceWithPushEntry(
                  "Two Payments Customer", new BigDecimal("1000.00"), extRef);

          var first =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-TWO-1", new BigDecimal("400.00"), "USD", Instant.now(), "PAID");
          var second =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-TWO-2", new BigDecimal("600.00"), "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(first, second)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isEqualTo(2);
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isZero();

          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1000.00");
          assertThat(invoice.getPaidAt()).isNotNull();

          var events =
              paymentEventRepository.findByInvoiceIdAndStatus(
                  invoiceId, PaymentEventStatus.COMPLETED);
          assertThat(events)
              .extracting(e -> e.getPaymentReference())
              .containsExactlyInAnyOrder("XERO-PAY-TWO-1", "XERO-PAY-TWO-2");
          assertThat(events).allMatch(e -> e.getPaymentMethod() == PaymentMethod.XERO);
        });
  }

  /** ADR-347 A4: a Xero payment above the balance is capped at PAID and the excess audited. */
  @Test
  void xeroPaymentAboveBalance_isCappedWithPaymentExcess() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-OVER-" + UUID.randomUUID().toString().substring(0, 6);
          UUID invoiceId =
              createSentInvoiceWithPushEntry(
                  "Overpaid Xero Customer", new BigDecimal("1000.00"), extRef);

          var paymentEvent =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-OVER", new BigDecimal("1100.00"), "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isEqualTo(1);
          assertThat(summary.drifted()).isZero();

          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1000.00");

          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).hasSize(1);
          assertThat(events.getFirst().getAmount()).isEqualByComparingTo("1100.00");

          var excessAudits =
              auditEventRepository
                  .findByFilter(
                      "invoice",
                      invoiceId,
                      null,
                      "invoice.payment_excess",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(excessAudits).hasSize(1);
          assertThat(excessAudits.getFirst().getDetails())
              .containsEntry("excess", "100.00")
              .containsEntry("payment_method", "XERO");
        });
  }

  /**
   * A zero or negative Xero payment amount (zero rounds-to-zero at scale 2 included) is skipped,
   * not posted: zero would move a SENT note to PARTIALLY_PAID with nothing paid, and a negative one
   * used to throw inside the poll transaction, so {@code recordPoll()} never ran and the same
   * window was re-fetched forever. The poll still completes and posts the valid payment; the
   * reconcile audit carries the amount actually posted (scale 2).
   */
  @Test
  void nonPositiveXeroAmounts_areSkippedAndThePollCompletes() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-NONPOS-" + UUID.randomUUID().toString().substring(0, 6);
          UUID invoiceId =
              createSentInvoiceWithPushEntry(
                  "Non Positive Xero Customer", new BigDecimal("1000.00"), extRef);

          var negative =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-NEG", new BigDecimal("-50.00"), "USD", Instant.now(), "PAID");
          var zero =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-ZERO", BigDecimal.ZERO, "USD", Instant.now(), "PAID");
          var roundsToZero =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-TINY", new BigDecimal("0.004"), "USD", Instant.now(), "PAID");
          var valid =
              new ExternalPaymentEvent(
                  extRef,
                  "XERO-PAY-VALID",
                  new BigDecimal("300.004"),
                  "USD",
                  Instant.now(),
                  "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(negative, zero, roundsToZero, valid)));

          Instant beforePoll = Instant.now();
          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isEqualTo(1);
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isEqualTo(3);

          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo("300.00");

          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events)
              .extracting(e -> e.getPaymentReference())
              .containsExactly("XERO-PAY-VALID");

          var reconciledAudits =
              auditEventRepository
                  .findByFilter(
                      null,
                      invoiceId,
                      null,
                      "integration.xero.payment_reconciled",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(reconciledAudits).hasSize(1);
          assertThat(reconciledAudits.getFirst().getDetails()).containsEntry("amount", "300.00");

          var connection = xeroConnectionRepository.findOneById(connectionId).orElseThrow();
          assertThat(connection.getLastPollAt()).isNotNull().isAfterOrEqualTo(beforePoll);
        });
  }

  @Test
  void currencyMismatch_createsReconcileDriftEntry() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-CURR-" + UUID.randomUUID().toString().substring(0, 6);
          BigDecimal amount = new BigDecimal("750.00");
          UUID invoiceId =
              createSentInvoiceWithPushEntry("Currency Mismatch Customer", amount, extRef);

          // Payment has same amount but different currency (GBP vs USD)
          var paymentEvent =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-CURR-MISMATCH", amount, "GBP", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isZero();
          assertThat(summary.drifted()).isEqualTo(1);
          assertThat(summary.skipped()).isZero();

          // Invoice should still be SENT
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);

          // RECONCILE_DRIFT sync entry should exist with CURRENCY_MISMATCH error code
          var pullEntries =
              syncEntryRepository.findByEntity(SyncEntityType.PAYMENT_PULL, invoiceId);
          assertThat(pullEntries).hasSize(1);
          assertThat(pullEntries.getFirst().getState()).isEqualTo(SyncState.RECONCILE_DRIFT);
          assertThat(pullEntries.getFirst().getLastErrorCode()).isEqualTo("CURRENCY_MISMATCH");
          assertThat(pullEntries.getFirst().getLastErrorDetail()).contains("GBP", "USD");

          // No PaymentEvent should be created
          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).isEmpty();
        });
  }

  @Test
  void xeroNativeInvoice_skippedWhenNoMatchingExternalReference() {
    runInTenant(
        () -> {
          // Payment references an external ref that has no matching PUSH sync entry
          var paymentEvent =
              new ExternalPaymentEvent(
                  "XERO-NATIVE-REF-" + UUID.randomUUID().toString().substring(0, 6),
                  "XERO-PAY-NATIVE",
                  new BigDecimal("200.00"),
                  "USD",
                  Instant.now(),
                  "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isZero();
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isEqualTo(1);
        });
  }

  @Test
  void alreadyPaidInvoice_skippedIdempotently() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-PAID-" + UUID.randomUUID().toString().substring(0, 6);
          BigDecimal amount = new BigDecimal("300.00");
          UUID invoiceId = createSentInvoiceWithPushEntry("Already Paid Customer", amount, extRef);

          // Manually transition to PAID first
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          invoice.recordPayment("MANUAL-REF");
          invoiceRepository.save(invoice);

          String xeroPaymentId = "XERO-PAY-ALREADY";
          var paymentEvent =
              new ExternalPaymentEvent(extRef, xeroPaymentId, amount, "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isZero();
          assertThat(summary.drifted()).isZero();
          assertThat(summary.skipped()).isEqualTo(1);

          // No additional PaymentEvent should be created
          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).isEmpty(); // manual payment doesn't create PaymentEvent via this path
        });
  }

  @Test
  void pollUpdatesConnectionLastPollAt() {
    runInTenant(
        () -> {
          var noopProvider = new NoOpAccountingProvider();
          when(integrationRegistry.resolve(
                  IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
              .thenReturn(noopProvider);

          Instant beforePoll = Instant.now();

          syncService.pollPaymentsForConnection(connectionId);

          var updated = xeroConnectionRepository.findOneById(connectionId).orElseThrow();
          assertThat(updated.getLastPollAt()).isNotNull().isAfterOrEqualTo(beforePoll);
        });
  }

  /**
   * Simple test implementation of AccountingPaymentSource that returns a fixed list of payments.
   */
  private static class TestPaymentSource implements AccountingPaymentSource {

    private final List<ExternalPaymentEvent> payments;

    TestPaymentSource(List<ExternalPaymentEvent> payments) {
      this.payments = payments;
    }

    @Override
    public String providerId() {
      return "test";
    }

    @Override
    public List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since) {
      return payments;
    }
  }
}
