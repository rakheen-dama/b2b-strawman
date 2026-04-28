package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentStatus;
import io.b2mash.b2b.b2bstrawman.integration.payment.WebhookResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentReconciliationServiceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_payment_reconciliation_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private PaymentReconciliationService reconciliationService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID portalContactId;
  private UUID customerWithoutContactId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Payment Reconciliation Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_pay_recon_owner",
                "pay_recon_owner@test.com",
                "Recon Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Recon Test Corp", "recon@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "Recon Test Project",
                              "Project for reconciliation tests",
                              memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Seed a PRIMARY active portal_contact for the main customer so
                      // PaymentReconciliationService can resolve actor_id for portal.invoice.paid
                      // audit rows. Tests below assert the row's actor_id matches this contact.
                      var portalContact =
                          new PortalContact(
                              ORG_ID,
                              customerId,
                              "primary@recon.test",
                              "Primary Recon Contact",
                              PortalContact.ContactRole.PRIMARY);
                      portalContact = portalContactRepository.save(portalContact);
                      portalContactId = portalContact.getId();

                      // Second customer with NO portal_contact rows — exercises the
                      // null-fallback path in handlePaymentCompleted.
                      var orphanCustomer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "No Portal Corp", "noportal@test.com", memberIdOwner);
                      orphanCustomer = customerRepository.save(orphanCustomer);
                      customerWithoutContactId = orphanCustomer.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerWithoutContactId, projectId, memberIdOwner));
                    }));
  }

  @Test
  void processWebhookResult_COMPLETED_marks_invoice_PAID() throws Exception {
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_1");
                      var result =
                          buildWebhookResult(
                              invoiceId, PaymentStatus.COMPLETED, "pay_ref_1", "sess_1");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

                      // GAP-L-75c: portal.invoice.paid PORTAL_CONTACT audit row must land
                      var portalPaidEvents =
                          auditEventRepository
                              .findByFilter(
                                  "invoice",
                                  UUID.fromString(invoiceId),
                                  null,
                                  "portal.invoice.paid",
                                  null,
                                  null,
                                  PageRequest.of(0, 10))
                              .getContent();
                      assertThat(portalPaidEvents).isNotEmpty();
                      var portalPaid = portalPaidEvents.get(0);
                      assertThat(portalPaid.getActorType()).isEqualTo("PORTAL_CONTACT");
                      assertThat(portalPaid.getSource()).isEqualTo("PORTAL");
                      // OBS-PaymentRec: actor_id must resolve to the customer's preferred
                      // active portal_contact (PRIMARY > BILLING > GENERAL). Without this,
                      // actor_type=PORTAL_CONTACT rows have NULL actor_id, breaking
                      // attribution on the matter Activity feed.
                      assertThat(portalPaid.getActorId()).isEqualTo(portalContactId);
                      // project_id is load-bearing for matter Activity feed (findByProjectId)
                      assertThat(portalPaid.getDetails()).containsKey("project_id");
                      assertThat(portalPaid.getDetails().get("provider"))
                          .isEqualTo("test-provider");
                    }));
  }

  @Test
  void processWebhookResult_COMPLETED_when_no_portal_contact_actor_id_is_null() throws Exception {
    String invoiceId = createAndSendInvoiceForCustomer(customerWithoutContactId);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_no_contact");
                      var result =
                          buildWebhookResult(
                              invoiceId,
                              PaymentStatus.COMPLETED,
                              "pay_ref_no_contact",
                              "sess_no_contact");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var portalPaidEvents =
                          auditEventRepository
                              .findByFilter(
                                  "invoice",
                                  UUID.fromString(invoiceId),
                                  null,
                                  "portal.invoice.paid",
                                  null,
                                  null,
                                  PageRequest.of(0, 10))
                              .getContent();
                      assertThat(portalPaidEvents).isNotEmpty();
                      var portalPaid = portalPaidEvents.get(0);
                      assertThat(portalPaid.getActorType()).isEqualTo("PORTAL_CONTACT");
                      // No portal_contact exists for this customer — preserve status-quo
                      // null actor_id rather than fabricating attribution. The row must
                      // still land (paid event is required for downstream reconciliation).
                      assertThat(portalPaid.getActorId()).isNull();
                    }));
  }

  @Test
  void processWebhookResult_COMPLETED_idempotent_when_already_paid() throws Exception {
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_2");
                      // First call — marks as PAID
                      var result =
                          buildWebhookResult(
                              invoiceId, PaymentStatus.COMPLETED, "pay_ref_2", "sess_2");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      // Second call — should be idempotent
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                    }));
  }

  @Test
  void processWebhookResult_FAILED_creates_event() throws Exception {
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_fail");
                      var result =
                          buildWebhookResult(invoiceId, PaymentStatus.FAILED, null, "sess_fail");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var events =
                          paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(
                              UUID.fromString(invoiceId));
                      boolean hasFailedEvent =
                          events.stream().anyMatch(e -> e.getStatus() == PaymentEventStatus.FAILED);
                      assertThat(hasFailedEvent).isTrue();
                    }));
  }

  @Test
  void processWebhookResult_EXPIRED_creates_event() throws Exception {
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_exp");
                      var result =
                          buildWebhookResult(invoiceId, PaymentStatus.EXPIRED, null, "sess_exp");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var events =
                          paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(
                              UUID.fromString(invoiceId));
                      boolean hasExpiredEvent =
                          events.stream()
                              .anyMatch(e -> e.getStatus() == PaymentEventStatus.EXPIRED);
                      assertThat(hasExpiredEvent).isTrue();
                    }));
  }

  @Test
  void invoiceService_send_calls_generatePaymentLink() throws Exception {
    // Verify that sending an invoice works — with NoOp adapter, no payment link is generated
    // but the flow should complete without error
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
                    }));
  }

  @Test
  void invoiceService_recordPayment_cancels_active_session() throws Exception {
    String invoiceId = createAndSendInvoice();

    // Set a payment session ID on the invoice, then record manual payment
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      invoice.setPaymentSessionId("test-session-to-cancel");
                      invoiceRepository.save(invoice);
                    }));

    // Record manual payment via API
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"paymentReference": "manual-ref-cancel"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      // Session should be cleared after manual payment
                      assertThat(invoice.getPaymentSessionId()).isNull();
                      assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
                    }));
  }

  @Test
  void manual_payment_writes_manual_PaymentEvent() throws Exception {
    String invoiceId = createAndSendInvoice();

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"paymentReference": "manual-ref-event"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var events =
                          paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(
                              UUID.fromString(invoiceId));
                      boolean hasManualEvent =
                          events.stream()
                              .anyMatch(
                                  e ->
                                      "manual".equals(e.getProviderSlug())
                                          && e.getStatus() == PaymentEventStatus.COMPLETED);
                      assertThat(hasManualEvent).isTrue();
                    }));
  }

  @Test
  void audit_event_logged_on_payment_completed() throws Exception {
    String invoiceId = createAndSendInvoice();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      seedCreatedPaymentEvent(invoiceId, "sess_audit");
                      var result =
                          buildWebhookResult(
                              invoiceId, PaymentStatus.COMPLETED, "pay_ref_audit", "sess_audit");
                      reconciliationService.processWebhookResult(result, "test-provider");

                      var auditEvents =
                          auditEventRepository.findByFilter(
                              null,
                              UUID.fromString(invoiceId),
                              null,
                              "payment.completed",
                              null,
                              null,
                              PageRequest.of(0, 10));
                      assertThat(auditEvents.getTotalElements()).isGreaterThanOrEqualTo(1);
                    }));
  }

  // --- Helpers ---

  /**
   * Seeds a CREATED PaymentEvent for the given invoice+session so the session-to-invoice
   * verification in processWebhookResult passes. This simulates what PaymentLinkService does when a
   * checkout session is first created.
   */
  private void seedCreatedPaymentEvent(String invoiceId, String sessionId) {
    var event =
        new PaymentEvent(
            UUID.fromString(invoiceId),
            "test-provider",
            sessionId,
            PaymentEventStatus.CREATED,
            java.math.BigDecimal.valueOf(1500),
            "ZAR",
            "OPERATING");
    paymentEventRepository.save(event);
  }

  private WebhookResult buildWebhookResult(
      String invoiceId, PaymentStatus status, String paymentReference, String sessionId) {
    return new WebhookResult(
        true,
        "checkout.completed",
        sessionId,
        paymentReference,
        status,
        Map.of("invoiceId", invoiceId));
  }

  private String createAndSendInvoice() throws Exception {
    return createAndSendInvoiceForCustomer(customerId);
  }

  private String createAndSendInvoiceForCustomer(UUID forCustomerId) throws Exception {
    String invoiceId = createDraftInvoiceForCustomer(forCustomerId);
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);
    return invoiceId;
  }

  private String createDraftInvoice() throws Exception {
    return createDraftInvoiceForCustomer(customerId);
  }

  private String createDraftInvoiceForCustomer(UUID forCustomerId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "currency": "ZAR"
                        }
                        """
                            .formatted(forCustomerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addLineItem(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Reconciliation test service",
                      "quantity": 1,
                      "unitPrice": 1500.00,
                      "sortOrder": 0,
                      "projectId": "%s"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
  }

  private void approveInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner")))
        .andExpect(status().isOk());
  }

  private void sendInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_recon_owner")))
        .andExpect(status().isOk());
  }
}
