package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class PaymentLinkServiceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_payment_link_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PaymentLinkService paymentLinkService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Payment Link Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_pay_link_owner",
                "pay_link_owner@test.com",
                "PayLink Owner",
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
                              "PayLink Test Corp", "paylink@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "PayLink Test Project",
                              "Project for payment link tests",
                              memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));
                    }));
  }

  @Test
  void psp_return_urls_are_portal_deep_links_carrying_orgId() {
    // Phase 88 U11 (§13.6.6): the PSP success/cancel return URLs land on authenticated portal
    // routes, so they carry orgId (the org's external id) like every other portal deep link.
    UUID invoiceId = UUID.randomUUID();
    String[] urls = new String[2];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              urls[0] = paymentLinkService.buildSuccessUrl(invoiceId);
              urls[1] = paymentLinkService.buildCancelUrl(invoiceId);
            });
    assertThat(urls[0])
        .isEqualTo(
            "http://localhost:3002/invoices/" + invoiceId + "/payment-success?orgId=" + ORG_ID);
    assertThat(urls[1])
        .isEqualTo(
            "http://localhost:3002/invoices/" + invoiceId + "/payment-cancelled?orgId=" + ORG_ID);
  }

  @Test
  void send_invoice_generates_no_payment_link_with_noop_adapter() throws Exception {
    // NoOp adapter returns notSupported, so payment fields should remain null
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      assertThat(invoice.getPaymentSessionId()).isNull();
                      assertThat(invoice.getPaymentUrl()).isNull();
                    }));
  }

  @Test
  void send_invoice_creates_no_payment_event_with_noop_adapter() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var events =
                          paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(
                              UUID.fromString(invoiceId));
                      // With NoOp adapter, no CREATED payment event should exist
                      boolean hasCreatedEvent =
                          events.stream()
                              .anyMatch(e -> e.getStatus() == PaymentEventStatus.CREATED);
                      assertThat(hasCreatedEvent).isFalse();
                    }));
  }

  @Test
  void refresh_payment_link_returns_ok_for_sent_invoice() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/refresh-payment-link")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner")))
        .andExpect(status().isOk());
  }

  @Test
  void refresh_payment_link_rejects_draft_invoice() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/refresh-payment-link")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Invalid status"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Only sent or partially paid fee notes can have their payment link refreshed"));
  }

  /** Phase 88 622C (ADR-347): a part-paid fee note can take a pay-now link for its balance due. */
  @Test
  void refresh_payment_link_returns_ok_for_partially_paid_invoice() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 100.00, "paymentMethod": "EFT", "paymentReference": "EFT-PART"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/refresh-payment-link")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
  }

  private String createDraftInvoice() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "currency": "ZAR"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addLineItem(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Test service",
                      "quantity": 1,
                      "unitPrice": 1000.00,
                      "sortOrder": 0,
                      "matterId": "%s"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
  }

  private void approveInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner")))
        .andExpect(status().isOk());
  }

  private void sendInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pay_link_owner")))
        .andExpect(status().isOk());
  }
}
