package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.ExternalPaymentEvent;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntryRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncService;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncDirection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncEntityType;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncTrigger;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.integration.payment.NoOpPaymentGateway;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentGateway;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentStatus;
import io.b2mash.b2b.b2bstrawman.integration.payment.WebhookResult;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentReconciliationService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Payment-cancellation tests (Phase 83, 589B.2). A PROPOSED collection activity with a PENDING
 * {@code SEND_COLLECTION_REMINDER} gate is cancelled when its invoice is settled on ANY of the
 * three routes — manual {@code InvoiceService.recordPayment}, PSP webhook {@code
 * PaymentReconciliationService.processWebhookResult}, and Xero pull {@code
 * AccountingSyncService.pollPaymentsForConnection} — and when the invoice is voided. Each: gate →
 * EXPIRED, activity → CANCELLED_PAYMENT, {@code collections.reminder.cancelled} audit. Approving
 * the gate after payment is refused (gate not PENDING).
 *
 * <p>AFTER_COMMIT listener effects are asserted after calling the transactional service directly
 * (no wrapping test transaction), so the service's commit fires the listener before the call
 * returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsPaymentCancellationTest {

  private static final String ORG_ID = "org_collections_cancel_test";

  @MockitoBean private IntegrationRegistry integrationRegistry;

  @MockitoBean(name = "noOpAccountingProvider")
  private AccountingProvider accountingProvider;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceService invoiceService;
  @Autowired private PaymentReconciliationService reconciliationService;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private AccountingSyncService syncService;
  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionGateService gateService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private NoOpPaymentGateway noOpPaymentGateway;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID connectionId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Cancel Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_cancel_owner", "cancel_owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(this::createConnectedXeroConnection);
  }

  @BeforeEach
  void stubPaymentGateway() {
    // Manual + webhook routes resolve a PAYMENT gateway; return the real NoOp (records success).
    when(integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class))
        .thenReturn(noOpPaymentGateway);
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
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

  private AiExecution createExecution() {
    var execution =
        new AiExecution(
            "collection-reminder",
            "collection_activity",
            UUID.randomUUID(),
            ownerMemberId,
            "claude-sonnet-4-6",
            1);
    execution.markCompleted(
        new AiCompletionResponse(
            "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
        4250L);
    return executionRepository.save(execution);
  }

  /**
   * Seeds a PROPOSED STAGE_1 activity for the invoice with a PENDING SEND_COLLECTION_REMINDER gate.
   */
  private UUID seedProposedActivityWithGate(UUID invoiceId, UUID customerId) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          var execution = createExecution();
          var gate =
              gateRepository.save(
                  new AiExecutionGate(
                      execution,
                      CollectionsPaymentListener.GATE_TYPE_SEND_COLLECTION_REMINDER,
                      Map.of("invoice_id", invoiceId.toString()),
                      "draft reasoning",
                      Instant.now().plus(Duration.ofHours(72))));
          var activity =
              new CollectionActivity(
                  invoiceId,
                  customerId,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.PROPOSED,
                  10,
                  null);
          activity.markProposed(gate.getId(), 10);
          holder[0] = activityRepository.saveAndFlush(activity).getId();
        });
    return holder[0];
  }

  /** Entity-driven SENT invoice; returns {invoiceId, customerId}. */
  private UUID[] seedSentInvoice(String name) {
    UUID[] holder = new UUID[2];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", ownerMemberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "ZAR",
                  name,
                  name + "@test.com",
                  null,
                  "Test Org",
                  ownerMemberId);
          invoice.updateDraft(LocalDate.now().minusDays(10), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), ownerMemberId);
          invoice.markSent();
          var saved = invoiceRepository.save(invoice);
          holder[0] = saved.getId();
          holder[1] = savedCustomer.getId();
        });
    return holder;
  }

  private void assertCancelled(UUID activityId, UUID invoiceId, String reason) {
    runInTenant(
        () -> {
          var activity = activityRepository.findOneById(activityId).orElseThrow();
          assertThat(activity.getStatus()).isEqualTo(CollectionActivityStatus.CANCELLED_PAYMENT);
          assertThat(activity.getReason()).isEqualTo(reason);

          var gate = gateRepository.findById(activity.getGateId()).orElseThrow();
          assertThat(gate.getStatus()).isEqualTo("EXPIRED");

          var audits =
              auditEventRepository
                  .findByFilter(
                      "collection_activity",
                      activityId,
                      null,
                      "collections.reminder.cancelled",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(audits).isNotEmpty();
          assertThat(audits.get(0).getDetails())
              .containsEntry("invoice_id", invoiceId.toString())
              .containsEntry("reason", reason);
        });
  }

  @Test
  void route_c_manualPayment_cancelsPendingReminder() {
    var ids = seedSentInvoice("Manual Pay Co");
    var activityId = seedProposedActivityWithGate(ids[0], ids[1]);

    runInTenant(() -> invoiceService.recordPayment(ids[0], "MANUAL-REF"));

    assertCancelled(activityId, ids[0], "invoice_paid");
  }

  @Test
  void route_a_webhookPayment_cancelsPendingReminder() {
    var ids = seedSentInvoice("Webhook Pay Co");
    var activityId = seedProposedActivityWithGate(ids[0], ids[1]);

    runInTenant(
        () -> {
          var created =
              new PaymentEvent(
                  ids[0],
                  "test-provider",
                  "sess_cancel",
                  PaymentEventStatus.CREATED,
                  BigDecimal.valueOf(1500),
                  "ZAR",
                  "OPERATING");
          paymentEventRepository.save(created);
          var result =
              new WebhookResult(
                  true,
                  "checkout.completed",
                  "sess_cancel",
                  "pay_ref_cancel",
                  PaymentStatus.COMPLETED,
                  Map.of("invoiceId", ids[0].toString()));
          reconciliationService.processWebhookResult(result, "test-provider");
        });

    assertCancelled(activityId, ids[0], "invoice_paid");
  }

  @Test
  void route_b_xeroPullPayment_cancelsPendingReminder() {
    String extRef = "KAZI-INV-CANCEL-" + UUID.randomUUID().toString().substring(0, 6);
    BigDecimal amount = new BigDecimal("500.00");
    UUID[] holder = new UUID[2];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(
                  "Xero Pay Co", "xero@test.com", ownerMemberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "USD",
                  "Xero Pay Co",
                  "xero@test.com",
                  null,
                  "Test Org",
                  ownerMemberId);
          invoice.recalculateTotals(amount, false, BigDecimal.ZERO, false);
          var savedInvoice = invoiceRepository.save(invoice);
          var line =
              new InvoiceLine(
                  savedInvoice.getId(), null, null, "Test service", BigDecimal.ONE, amount, 0);
          invoiceLineRepository.save(line);
          savedInvoice.approve(
              "INV-XPULL-" + savedInvoice.getId().toString().substring(0, 6), ownerMemberId);
          savedInvoice.markSent();
          savedInvoice = invoiceRepository.save(savedInvoice);
          var pushEntry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  savedInvoice.getId(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  extRef);
          pushEntry.markInFlight();
          pushEntry.markCompleted("XERO-INV-" + savedInvoice.getId().toString().substring(0, 8));
          syncEntryRepository.save(pushEntry);
          holder[0] = savedInvoice.getId();
          holder[1] = savedCustomer.getId();
        });

    var activityId = seedProposedActivityWithGate(holder[0], holder[1]);

    runInTenant(
        () -> {
          when(integrationRegistry.resolve(
                  IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
              .thenReturn(
                  new TestPaymentSource(
                      List.of(
                          new ExternalPaymentEvent(
                              extRef, "XERO-PAY-1", amount, "USD", Instant.now(), "PAID"))));
          syncService.pollPaymentsForConnection(connectionId);
        });

    assertCancelled(activityId, holder[0], "invoice_paid");
  }

  @Test
  void voidInvoice_cancelsPendingReminder() {
    var ids = seedSentInvoice("Void Co");
    var activityId = seedProposedActivityWithGate(ids[0], ids[1]);

    runInTenant(() -> invoiceService.voidInvoice(ids[0]));

    assertCancelled(activityId, ids[0], "invoice_voided");
  }

  @Test
  void approveAfterPayment_isRefused() {
    var ids = seedSentInvoice("Approve After Pay Co");
    var activityId = seedProposedActivityWithGate(ids[0], ids[1]);
    UUID[] gateId = new UUID[1];
    runInTenant(
        () -> gateId[0] = activityRepository.findOneById(activityId).orElseThrow().getGateId());

    runInTenant(() -> invoiceService.recordPayment(ids[0], "MANUAL-REF-2"));

    // Gate is now EXPIRED — a late approval is refused.
    assertThatThrownBy(
            () -> runInTenant(() -> gateService.approve(gateId[0], ownerMemberId, "late")))
        .isInstanceOf(InvalidStateException.class);
  }

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
