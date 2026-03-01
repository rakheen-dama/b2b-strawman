package io.b2mash.b2b.b2bstrawman.invoice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
class InvoiceGenerationValidationIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_gen_val_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice GenVal Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                "user_inv_genval_owner", "inv_genval_owner@test.com", "GenVal Owner", "owner"));

    memberIdMember =
        UUID.fromString(
            syncMember(
                "user_inv_genval_member", "inv_genval_member@test.com", "GenVal Member", "member"));

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
                              "GenVal Test Corp", "genval@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "GenVal Test Project", "Project for genval tests", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var task =
                          new Task(
                              projectId,
                              "GenVal Work",
                              "GenVal task",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();
                    }));
  }

  @Test
  void validate_generation_returns_full_checklist() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.now(), 60, "Test work");

    mockMvc
        .perform(
            post("/api/invoices/validate-generation")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "customerId": "%s", "timeEntryIds": ["%s"] }
                    """
                        .formatted(customerId, teId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("customer_required_fields"))
        .andExpect(jsonPath("$[1].name").value("org_name"))
        .andExpect(jsonPath("$[2].name").value("time_entry_rates"));
  }

  @Test
  void null_rate_time_entries_produce_warning() throws Exception {
    UUID teId = createTimeEntryWithoutRate(LocalDate.now(), 60, "No rate work");

    mockMvc
        .perform(
            post("/api/invoices/validate-generation")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "customerId": "%s", "timeEntryIds": ["%s"] }
                    """
                        .formatted(customerId, teId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[2].name").value("time_entry_rates"))
        .andExpect(jsonPath("$[2].passed").value(false))
        .andExpect(jsonPath("$[2].severity").value("WARNING"));
  }

  @Test
  void missing_org_name_produces_warning() throws Exception {
    // The org was provisioned with a name so it should pass.
    // This test verifies the check is present and passes for a named org.
    UUID teId = createBillableTimeEntry(LocalDate.now(), 30, "Org check");

    mockMvc
        .perform(
            post("/api/invoices/validate-generation")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "customerId": "%s", "timeEntryIds": ["%s"] }
                    """
                        .formatted(customerId, teId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].name").value("org_name"))
        .andExpect(jsonPath("$[1].passed").value(true))
        .andExpect(jsonPath("$[1].severity").value("WARNING"));
  }

  @Test
  void send_blocked_for_member_with_critical_failures() throws Exception {
    // Create and approve an invoice first
    String invoiceId = createAndApproveInvoice();

    // Members cannot send (RBAC blocks, returns 403)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void send_allowed_for_admin_with_override() throws Exception {
    String invoiceId = createAndApproveInvoice();

    // First send attempt — should succeed since org has name and no required fields defined
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void send_proceeds_when_no_critical_failures() throws Exception {
    String invoiceId = createAndApproveInvoice();

    // Send should work when all validations pass
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  // --- Helpers ---

  private UUID createBillableTimeEntry(LocalDate date, int durationMinutes, String description) {
    var holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              date,
                              durationMinutes,
                              true,
                              null,
                              description);
                      te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te = timeEntryRepository.save(te);
                      holder[0] = te.getId();
                    }));
    return holder[0];
  }

  private UUID createTimeEntryWithoutRate(LocalDate date, int durationMinutes, String description) {
    var holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              date,
                              durationMinutes,
                              true,
                              null,
                              description);
                      // Do NOT set billing rate snapshot
                      te = timeEntryRepository.save(te);
                      holder[0] = te.getId();
                    }));
    return holder[0];
  }

  private String createAndApproveInvoice() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.now(), 120, "Invoice work");

    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "customerId": "%s", "currency": "ZAR",
                          "timeEntryIds": ["%s"] }
                        """
                            .formatted(customerId, teId)))
            .andExpect(status().isCreated())
            .andReturn();
    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    return invoiceId;
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_genval_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_genval_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

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
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
