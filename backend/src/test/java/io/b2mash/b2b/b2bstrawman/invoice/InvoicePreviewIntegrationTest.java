package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class InvoicePreviewIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_preview_test";
  private static final String ORG_ID_B = "org_inv_preview_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID invoiceId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Preview Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_inv_preview_owner",
                "inv_preview_owner@test.com",
                "Preview Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Customer
                      var customer =
                          createActiveCustomer("Preview Corp", "preview@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project
                      var project =
                          new Project("Website Redesign", "Redesign the website", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      // Link project to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Task
                      var task =
                          new Task(
                              projectId,
                              "Backend API dev",
                              "Build REST API endpoints",
                              "HIGH",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);

                      // Billable time entry
                      var te =
                          new TimeEntry(
                              task.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 1, 20),
                              150,
                              true,
                              null,
                              "API development");
                      te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te = timeEntryRepository.save(te);

                      // Create invoice with time entry via API is complex -- create directly
                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Preview Corp",
                              "preview@test.com",
                              "123 Test Street, Cape Town",
                              "Preview Test Org",
                              memberIdOwner);
                      invoice.updateDraft(
                          LocalDate.of(2025, 2, 28),
                          "January 2025 services",
                          "Net 30",
                          new BigDecimal("675.00"));
                      invoice = invoiceRepository.save(invoice);
                      invoiceId = invoice.getId();

                      // Line item linked to project
                      var line1 =
                          new InvoiceLine(
                              invoice.getId(),
                              projectId,
                              te.getId(),
                              "Backend API dev -- 2025-01-20 -- Preview Owner",
                              new BigDecimal("2.5000"),
                              new BigDecimal("1800.00"),
                              0);
                      invoiceLineRepository.save(line1);

                      // Manual line item without project (Other Items)
                      var line2 =
                          new InvoiceLine(
                              invoice.getId(),
                              null,
                              null,
                              "Project setup fee",
                              new BigDecimal("1.0000"),
                              new BigDecimal("5000.00"),
                              1);
                      invoiceLineRepository.save(line2);

                      // Recalculate totals
                      BigDecimal subtotal = line1.getAmount().add(line2.getAmount());
                      invoice.recalculateTotals(subtotal);
                      invoiceRepository.save(invoice);
                    }));

    // --- Tenant B (for cross-tenant isolation test) ---
    provisioningService.provisionTenant(ORG_ID_B, "Preview Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    syncMember(
        ORG_ID_B,
        "user_inv_preview_owner_b",
        "inv_preview_owner_b@test.com",
        "Preview Owner B",
        "owner");
  }

  @Test
  void shouldReturnHtmlWithCorrectContentType() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void shouldIncludeInvoiceNumberAndCustomerName() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Preview Corp")))
        .andExpect(content().string(containsString("Preview Test Org")))
        .andExpect(content().string(containsString("preview@test.com")))
        .andExpect(content().string(containsString("DRAFT")));
  }

  @Test
  void shouldIncludeGroupedLineItems() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Website Redesign")))
        .andExpect(content().string(containsString("Backend API dev")))
        .andExpect(content().string(containsString("Other Items")))
        .andExpect(content().string(containsString("Project setup fee")));
  }

  @Test
  void shouldIncludeTotals() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Subtotal")))
        .andExpect(content().string(containsString("Tax")))
        .andExpect(content().string(containsString("Total (ZAR)")))
        .andExpect(content().string(containsString("Net 30")))
        .andExpect(content().string(containsString("January 2025 services")));
  }

  @Test
  void shouldReturn403ForMemberRole() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturn404ForNonExistentInvoice() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + UUID.randomUUID() + "/preview").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenAccessingPreviewFromDifferentTenant() throws Exception {
    // Invoice was created in tenant A — accessing from tenant B's admin should return 404
    mockMvc
        .perform(get("/api/invoices/" + invoiceId + "/preview").with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_preview_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_preview_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_preview_owner_b")
                    .claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Helpers ---

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
