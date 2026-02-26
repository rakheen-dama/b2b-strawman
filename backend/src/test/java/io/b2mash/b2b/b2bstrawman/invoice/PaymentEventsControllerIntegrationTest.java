package io.b2mash.b2b.b2bstrawman.invoice;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentEventsControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_payment_events_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Payment Events Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                "user_pay_events_owner", "pay_events_owner@test.com", "PayEvents Owner", "owner"));

    // Sync a member user for 403 tests
    syncMember(
        "user_pay_events_member", "pay_events_member@test.com", "PayEvents Member", "member");

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
                          TestCustomerFactory.createActiveCustomer(
                              "PayEvents Test Corp", "payevents@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "PayEvents Test Project",
                              "Project for payment events tests",
                              memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));
                    }));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_pay_events_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_pay_events_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Member sync helper ---

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  // --- Invoice creation helper ---

  private String createDraftInvoice() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Test service",
                      "quantity": 1,
                      "unitPrice": 1000.00,
                      "sortOrder": 0,
                      "projectId": "%s"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
  }

  private void approveInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private void sendInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private void recordPayment(String invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"paymentReference": "PAY-TEST-001"}
                    """))
        .andExpect(status().isOk());
  }

  // --- Tests ---

  @Test
  void getPaymentEvents_returns_empty_for_new_invoice() throws Exception {
    String invoiceId = createDraftInvoice();

    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/payment-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getPaymentEvents_returns_events_after_manual_payment() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);
    recordPayment(invoiceId);

    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/payment-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].providerSlug").value("manual"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].paymentReference").value("PAY-TEST-001"))
        .andExpect(jsonPath("$[0].paymentDestination").value("OPERATING"))
        .andExpect(jsonPath("$[0].currency").value("ZAR"))
        .andExpect(jsonPath("$[0].amount").value(1150.00));
  }

  @Test
  void getPaymentEvents_returns_403_for_member() throws Exception {
    String invoiceId = createDraftInvoice();

    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/payment-events").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getPaymentEvents_returns_404_for_unknown_invoice() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + UUID.randomUUID() + "/payment-events").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void recordPayment_creates_payment_event_with_correct_fields() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);
    recordPayment(invoiceId);

    // Verify via the payment events endpoint
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/payment-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].providerSlug").value("manual"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].amount").value(1150.00))
        .andExpect(jsonPath("$[0].currency").value("ZAR"))
        .andExpect(jsonPath("$[0].paymentDestination").value("OPERATING"))
        .andExpect(jsonPath("$[0].sessionId").isEmpty())
        .andExpect(jsonPath("$[0].id").isNotEmpty())
        .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$[0].updatedAt").isNotEmpty());
  }

  @Test
  void recordPayment_creates_event_with_payment_reference_from_request() throws Exception {
    String invoiceId = createDraftInvoice();
    addLineItem(invoiceId);
    approveInvoice(invoiceId);
    sendInvoice(invoiceId);

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"paymentReference": "REF-CUSTOM-123"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/payment-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].paymentReference").value("REF-CUSTOM-123"));
  }
}
