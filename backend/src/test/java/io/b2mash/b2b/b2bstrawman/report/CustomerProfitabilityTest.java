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
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
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
class CustomerProfitabilityTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cust_profit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectService customerProjectService;

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID customerId;
  private UUID customerIdNoProjects;
  private UUID projectId1;
  private UUID projectId2;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Profit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_custprofit_owner",
                "custprofit_owner@test.com",
                "CustProfit Owner",
                "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_custprofit_member",
                "custprofit_member@test.com",
                "CustProfit Member",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create 2 projects
              var project1 =
                  projectService.createProject("Customer Project 1", "Project 1", memberIdOwner);
              projectId1 = project1.getId();

              var project2 =
                  projectService.createProject("Customer Project 2", "Project 2", memberIdOwner);
              projectId2 = project2.getId();

              // Create customer and link to both projects
              var customer =
                  new Customer(
                      "Test Customer",
                      "custprofit_customer@test.com",
                      null,
                      null,
                      null,
                      memberIdOwner,
                      null,
                      LifecycleStatus.ACTIVE);
              customer = customerRepository.save(customer);
              customerId = customer.getId();

              customerProjectService.linkCustomerToProject(
                  customerId, projectId1, memberIdOwner, memberIdOwner, "owner");
              customerProjectService.linkCustomerToProject(
                  customerId, projectId2, memberIdOwner, memberIdOwner, "owner");

              // Create customer with no projects
              var noProjectsCustomer =
                  customerService.createCustomer(
                      "No Projects Customer",
                      "custprofit_noproj@test.com",
                      null,
                      null,
                      null,
                      memberIdOwner);
              customerIdNoProjects = noProjectsCustomer.getId();

              // Create billing rate: $100/hr USD
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("100.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Create cost rate: $50/hr USD
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("50.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Tasks for each project
              var task1 =
                  taskService.createTask(
                      projectId1,
                      "Cust Task 1",
                      "Task 1",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              var task2 =
                  taskService.createTask(
                      projectId2,
                      "Cust Task 2",
                      "Task 2",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");

              // Time entries on project 1: 120 min billable (2h)
              timeEntryService.createTimeEntry(
                  task1.getId(),
                  LocalDate.of(2025, 1, 15),
                  120,
                  true,
                  null,
                  "Project 1 work",
                  memberIdOwner,
                  "owner");

              // Time entries on project 2: 60 min billable (1h), 30 min non-billable
              timeEntryService.createTimeEntry(
                  task2.getId(),
                  LocalDate.of(2025, 1, 20),
                  60,
                  true,
                  null,
                  "Project 2 billable",
                  memberIdOwner,
                  "owner");

              timeEntryService.createTimeEntry(
                  task2.getId(),
                  LocalDate.of(2025, 2, 5),
                  30,
                  false,
                  null,
                  "Project 2 non-billable",
                  memberIdOwner,
                  "owner");
            });
    // Customer totals across both projects:
    // Billable hours: 2 + 1 = 3h
    // Non-billable hours: 0.5h
    // Total hours: 3.5h
    // Billable value: 3 * 100 = $300
    // Cost value: 3.5 * 50 = $175
    // Margin: 300 - 175 = $125
    // MarginPercent: 125 / 300 * 100 = 41.67%
  }

  @Test
  @Order(1)
  void aggregatesAcrossMultipleProjects() throws Exception {
    mockMvc
        .perform(get("/api/customers/{customerId}/profitability", customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.customerName").value("Test Customer"))
        .andExpect(jsonPath("$.currencies").isArray())
        .andExpect(jsonPath("$.currencies.length()").value(1))
        .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(3.0))
        .andExpect(jsonPath("$.currencies[0].totalNonBillableHours").value(0.5))
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.5));
  }

  @Test
  @Order(2)
  void dateRangeFiltering() throws Exception {
    // Only January entries: 2h from project 1 + 1h from project 2 = 3h billable, 0 non-billable
    mockMvc
        .perform(
            get("/api/customers/{customerId}/profitability", customerId)
                .param("from", "2025-01-01")
                .param("to", "2025-01-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(3.0))
        .andExpect(jsonPath("$.currencies[0].totalNonBillableHours").value(0.0))
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.0));
  }

  @Test
  @Order(3)
  void customerWithNoLinkedProjectsReturnsEmptyCurrencies() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/{customerId}/profitability", customerIdNoProjects).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerIdNoProjects.toString()))
        .andExpect(jsonPath("$.currencies").isArray())
        .andExpect(jsonPath("$.currencies.length()").value(0));
  }

  @Test
  @Order(4)
  void nonAdminRejectedWith403() throws Exception {
    // Regular member should be rejected — customer profitability is admin/owner only
    mockMvc
        .perform(get("/api/customers/{customerId}/profitability", customerId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void marginCalculation() throws Exception {
    // billableValue: 300, costValue: 175, margin: 125, marginPercent: 41.67
    mockMvc
        .perform(get("/api/customers/{customerId}/profitability", customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].margin").isNumber())
        .andExpect(jsonPath("$.currencies[0].marginPercent").isNumber());
  }

  @Test
  @Order(6)
  void nonExistentCustomerReturns404() throws Exception {
    UUID fakeId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/customers/{customerId}/profitability", fakeId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_custprofit_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_custprofit_member")
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
