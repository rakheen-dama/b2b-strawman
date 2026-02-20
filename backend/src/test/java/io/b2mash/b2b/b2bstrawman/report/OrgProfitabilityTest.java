package io.b2mash.b2b.b2bstrawman.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgProfitabilityTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_orgprofit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerProjectService customerProjectService;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService customerLifecycleService;

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID customerId;
  private UUID projectId1;
  private UUID projectId2;
  private UUID projectId3; // no customer link, no cost rate

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Org Profitability Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_orgprofit_owner",
                "orgprofit_owner@test.com",
                "OrgProfit Owner",
                "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_orgprofit_member",
                "orgprofit_member@test.com",
                "OrgProfit Member",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create 3 projects
              var project1 =
                  projectService.createProject("High Margin Project", "P1", memberIdOwner);
              projectId1 = project1.getId();

              var project2 =
                  projectService.createProject("Low Margin Project", "P2", memberIdOwner);
              projectId2 = project2.getId();

              var project3 = projectService.createProject("No Cost Project", "P3", memberIdOwner);
              projectId3 = project3.getId();

              // Create customer and link to project1 and project2 only
              var customer =
                  customerService.createCustomer(
                      "OrgProfit Customer",
                      "orgprofit_customer@test.com",
                      null,
                      null,
                      null,
                      memberIdOwner);
              customerId = customer.getId();

              // Transition PROSPECT -> ACTIVE so lifecycle guard permits linking
              customerLifecycleService.transition(customerId, "ACTIVE", null, memberIdOwner);

              customerProjectService.linkCustomerToProject(
                  customerId, projectId1, memberIdOwner, memberIdOwner, "owner");
              customerProjectService.linkCustomerToProject(
                  customerId, projectId2, memberIdOwner, memberIdOwner, "owner");

              // Billing rate: $150/hr for owner
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("150.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Cost rate: $60/hr for owner
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("60.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Project 1 tasks and time entries
              var task1 =
                  taskService.createTask(
                      projectId1,
                      "OrgP Task 1",
                      "Task 1",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");

              // 120 min (2 hours), billable => billable value = 2 * 150 = 300
              timeEntryService.createTimeEntry(
                  task1.getId(),
                  LocalDate.of(2025, 1, 15),
                  120,
                  true,
                  null,
                  "Project 1 billable",
                  memberIdOwner,
                  "owner");

              // Project 2 tasks and time entries
              var task2 =
                  taskService.createTask(
                      projectId2,
                      "OrgP Task 2",
                      "Task 2",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");

              // 60 min (1 hour), billable => billable value = 1 * 150 = 150
              timeEntryService.createTimeEntry(
                  task2.getId(),
                  LocalDate.of(2025, 1, 20),
                  60,
                  true,
                  null,
                  "Project 2 billable",
                  memberIdOwner,
                  "owner");

              // Project 3 tasks and time entries (no customer, no cost rate specific override)
              var task3 =
                  taskService.createTask(
                      projectId3,
                      "OrgP Task 3",
                      "Task 3",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");

              // 90 min (1.5 hours), billable => billable value = 1.5 * 150 = 225
              timeEntryService.createTimeEntry(
                  task3.getId(),
                  LocalDate.of(2025, 1, 25),
                  90,
                  true,
                  null,
                  "Project 3 billable",
                  memberIdOwner,
                  "owner");
            });

    // Project 1: revenue=300, cost=2*60=120, margin=180, margin%=60.00%
    // Project 2: revenue=150, cost=1*60=60, margin=90, margin%=60.00%
    // Project 3: revenue=225, cost=1.5*60=90, margin=135, margin%=60.00%
    // Sorted by margin DESC: P1 (180), P3 (135), P2 (90)
  }

  @Test
  @Order(1)
  void showsAllProjectsSortedByMarginDesc() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projects").isArray())
        .andExpect(jsonPath("$.projects.length()").value(3))
        // Sorted by margin DESC: P1 (180) > P3 (135) > P2 (90)
        .andExpect(jsonPath("$.projects[0].projectName").value("High Margin Project"))
        .andExpect(jsonPath("$.projects[0].currency").value("USD"))
        .andExpect(jsonPath("$.projects[0].billableHours").value(2.0))
        .andExpect(jsonPath("$.projects[1].projectName").value("No Cost Project"))
        .andExpect(jsonPath("$.projects[2].projectName").value("Low Margin Project"));
  }

  @Test
  @Order(2)
  void customerIdFilterNarrowsToLinkedProjects() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .param("customerId", customerId.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projects").isArray())
        .andExpect(jsonPath("$.projects.length()").value(2))
        // Only projects linked to the customer (P1, P2)
        .andExpect(jsonPath("$.projects[0].customerName").value("OrgProfit Customer"))
        .andExpect(jsonPath("$.projects[1].customerName").value("OrgProfit Customer"));
  }

  @Test
  @Order(3)
  void projectWithoutCustomerHasNullCustomerName() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        // Project 3 (No Cost Project) has no customer link
        .andExpect(jsonPath("$.projects[1].projectName").value("No Cost Project"))
        .andExpect(jsonPath("$.projects[1].customerName").isEmpty());
  }

  @Test
  @Order(4)
  void nonAdminRejectedWith403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_orgprofit_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_orgprofit_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

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
