package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.ExternalPaymentEvent;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
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
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
    savedInvoice.approve("INV-POLL-" + savedInvoice.getId().toString().substring(0, 6), memberId);
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
    // The service resolves AccountingProvider then checks instanceof AccountingPaymentSource
    when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
        .thenReturn((AccountingProvider) paymentSource);
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

          // PULL sync entry should exist
          var pullEntries =
              syncEntryRepository.findByEntity(SyncEntityType.PAYMENT_PULL, invoiceId);
          assertThat(pullEntries).hasSize(1);
          assertThat(pullEntries.getFirst().getState()).isEqualTo(SyncState.COMPLETED);
          assertThat(pullEntries.getFirst().getDirection()).isEqualTo(SyncDirection.PULL);
          assertThat(pullEntries.getFirst().getExternalId()).isEqualTo(xeroPaymentId);
        });
  }

  @Test
  void amountDrift_createsReconcileDriftEntry() {
    runInTenant(
        () -> {
          String extRef = "KAZI-INV-DRIFT-" + UUID.randomUUID().toString().substring(0, 6);
          BigDecimal kaziTotal = new BigDecimal("1000.00");
          BigDecimal xeroAmount = new BigDecimal("999.00"); // drift of 1.00 > 0.01
          UUID invoiceId = createSentInvoiceWithPushEntry("Drift Customer", kaziTotal, extRef);

          var paymentEvent =
              new ExternalPaymentEvent(
                  extRef, "XERO-PAY-DRIFT", xeroAmount, "USD", Instant.now(), "PAID");

          stubPaymentSource(new TestPaymentSource(List.of(paymentEvent)));

          var summary = syncService.pollPaymentsForConnection(connectionId);

          assertThat(summary.matched()).isZero();
          assertThat(summary.drifted()).isEqualTo(1);
          assertThat(summary.skipped()).isZero();

          // Invoice should still be SENT
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);

          // RECONCILE_DRIFT sync entry should exist
          var pullEntries =
              syncEntryRepository.findByEntity(SyncEntityType.PAYMENT_PULL, invoiceId);
          assertThat(pullEntries).hasSize(1);
          assertThat(pullEntries.getFirst().getState()).isEqualTo(SyncState.RECONCILE_DRIFT);
          assertThat(pullEntries.getFirst().getLastErrorCode()).isEqualTo("DRIFT_DETECTED");
          assertThat(pullEntries.getFirst().getLastErrorDetail()).contains("999.00", "1000.00");
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
          when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
              .thenReturn(noopProvider);

          Instant beforePoll = Instant.now();

          syncService.pollPaymentsForConnection(connectionId);

          var updated = xeroConnectionRepository.findOneById(connectionId).orElseThrow();
          assertThat(updated.getLastPollAt()).isNotNull().isAfterOrEqualTo(beforePoll);
        });
  }

  /**
   * Simple test implementation of AccountingPaymentSource that returns a fixed list of payments.
   * Also implements AccountingProvider so it passes the instanceof check in the service.
   */
  private static class TestPaymentSource implements AccountingProvider, AccountingPaymentSource {

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

    @Override
    public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
      throw new UnsupportedOperationException("Not needed for payment pull tests");
    }

    @Override
    public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
      throw new UnsupportedOperationException("Not needed for payment pull tests");
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(true, "test", null);
    }
  }
}
