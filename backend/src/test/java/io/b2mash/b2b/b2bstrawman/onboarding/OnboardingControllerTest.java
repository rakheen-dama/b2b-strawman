package io.b2mash.b2b.b2bstrawman.onboarding;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String EMPTY_ORG_ID = "org_onboarding_empty";
  private static final String POPULATED_ORG_ID = "org_onboarding_populated";
  private static final String ADMIN_ORG_ID = "org_onboarding_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private InvoiceRepository invoiceRepository;

  private String emptyTenantSchema;
  private String populatedTenantSchema;
  private String emptyOwnerMemberId;
  private String populatedOwnerMemberId;
  private String adminMemberId;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Empty tenant — only owner member (to test zero-entity state)
    emptyTenantSchema =
        provisioningService.provisionTenant(EMPTY_ORG_ID, "Empty Onboarding Org").schemaName();
    planSyncService.syncPlan(EMPTY_ORG_ID, "pro-plan");
    emptyOwnerMemberId =
        syncMember(
            EMPTY_ORG_ID, "user_onb_empty_owner", "onb_empty@test.com", "Empty Owner", "owner");

    // Admin test tenant — owner + admin to test admin dismiss capability
    provisioningService.provisionTenant(ADMIN_ORG_ID, "Admin Test Org");
    planSyncService.syncPlan(ADMIN_ORG_ID, "pro-plan");
    syncMember(ADMIN_ORG_ID, "user_onb_adm_owner", "onb_adm_owner@test.com", "Adm Owner", "owner");
    adminMemberId =
        syncMember(ADMIN_ORG_ID, "user_onb_admin", "onb_admin@test.com", "Admin User", "admin");

    // Populated tenant — has all entity types
    populatedTenantSchema =
        provisioningService
            .provisionTenant(POPULATED_ORG_ID, "Populated Onboarding Org")
            .schemaName();
    planSyncService.syncPlan(POPULATED_ORG_ID, "pro-plan");
    populatedOwnerMemberId =
        syncMember(
            POPULATED_ORG_ID, "user_onb_pop_owner", "onb_pop@test.com", "Pop Owner", "owner");
    // Second member for INVITE_MEMBER step
    syncMember(
        POPULATED_ORG_ID, "user_onb_pop_member", "onb_pop_member@test.com", "Pop Member", "member");

    // Seed entities in populated tenant
    UUID memberId = UUID.fromString(populatedOwnerMemberId);
    ScopedValue.where(RequestScopes.TENANT_ID, populatedTenantSchema)
        .run(
            () -> {
              // Project
              var project =
                  projectRepository.save(
                      new Project("Onboarding Project", "Test project", memberId));

              // Customer
              var customer =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Onboarding Customer", "onb_cust@test.com", memberId));

              // Task + TimeEntry
              var task =
                  taskRepository.save(
                      new Task(
                          project.getId(), "Test Task", "desc", "MEDIUM", "TASK", null, memberId));
              timeEntryRepository.save(
                  new TimeEntry(
                      task.getId(), memberId, LocalDate.now(), 60, true, null, "Test time"));

              // BillingRate
              billingRateRepository.save(
                  new BillingRate(
                      memberId,
                      null,
                      null,
                      "USD",
                      new BigDecimal("100.00"),
                      LocalDate.now(),
                      null));

              // Invoice
              invoiceRepository.save(
                  new Invoice(
                      customer.getId(),
                      "USD",
                      "Onboarding Customer",
                      "onb_cust@test.com",
                      null,
                      "Populated Onboarding Org",
                      memberId));
            });
  }

  @Test
  @Order(1)
  void getProgress_noEntities_allStepsIncomplete() throws Exception {
    mockMvc
        .perform(get("/api/onboarding/progress").with(emptyOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps", hasSize(6)))
        .andExpect(jsonPath("$.completedCount").value(0))
        .andExpect(jsonPath("$.totalCount").value(6))
        .andExpect(jsonPath("$.dismissed").value(false))
        .andExpect(jsonPath("$.steps[0].code").value("CREATE_PROJECT"))
        .andExpect(jsonPath("$.steps[0].completed").value(false))
        .andExpect(jsonPath("$.steps[1].code").value("ADD_CUSTOMER"))
        .andExpect(jsonPath("$.steps[1].completed").value(false))
        .andExpect(jsonPath("$.steps[2].code").value("INVITE_MEMBER"))
        .andExpect(jsonPath("$.steps[2].completed").value(false))
        .andExpect(jsonPath("$.steps[3].code").value("LOG_TIME"))
        .andExpect(jsonPath("$.steps[3].completed").value(false))
        .andExpect(jsonPath("$.steps[4].code").value("SETUP_RATES"))
        .andExpect(jsonPath("$.steps[4].completed").value(false))
        .andExpect(jsonPath("$.steps[5].code").value("CREATE_INVOICE"))
        .andExpect(jsonPath("$.steps[5].completed").value(false));
  }

  @Test
  @Order(2)
  void getProgress_withAllEntities_allStepsComplete() throws Exception {
    mockMvc
        .perform(get("/api/onboarding/progress").with(populatedOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps", hasSize(6)))
        .andExpect(jsonPath("$.completedCount").value(6))
        .andExpect(jsonPath("$.totalCount").value(6))
        .andExpect(jsonPath("$.dismissed").value(false))
        .andExpect(jsonPath("$.steps[0].code").value("CREATE_PROJECT"))
        .andExpect(jsonPath("$.steps[0].completed").value(true))
        .andExpect(jsonPath("$.steps[1].code").value("ADD_CUSTOMER"))
        .andExpect(jsonPath("$.steps[1].completed").value(true))
        .andExpect(jsonPath("$.steps[2].code").value("INVITE_MEMBER"))
        .andExpect(jsonPath("$.steps[2].completed").value(true))
        .andExpect(jsonPath("$.steps[3].code").value("LOG_TIME"))
        .andExpect(jsonPath("$.steps[3].completed").value(true))
        .andExpect(jsonPath("$.steps[4].code").value("SETUP_RATES"))
        .andExpect(jsonPath("$.steps[4].completed").value(true))
        .andExpect(jsonPath("$.steps[5].code").value("CREATE_INVOICE"))
        .andExpect(jsonPath("$.steps[5].completed").value(true));
  }

  @Test
  @Order(3)
  void dismiss_ownerRole_returns204() throws Exception {
    mockMvc
        .perform(post("/api/onboarding/dismiss").with(populatedOwnerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(4)
  void dismiss_idempotent_returns204() throws Exception {
    mockMvc
        .perform(post("/api/onboarding/dismiss").with(populatedOwnerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(5)
  void getProgress_dismissed_returnsDismissedTrue() throws Exception {
    mockMvc
        .perform(get("/api/onboarding/progress").with(populatedOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dismissed").value(true))
        .andExpect(jsonPath("$.completedCount").value(6));
  }

  @Test
  @Order(6)
  void dismiss_adminRole_returns204() throws Exception {
    // Admin in a properly synced org has TEAM_OVERSIGHT capability and can dismiss
    mockMvc
        .perform(post("/api/onboarding/dismiss").with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(7)
  void getProgress_afterDismiss_emptyTenant_returnsDismissedTrue() throws Exception {
    // Dismiss empty tenant via owner, then verify dismissed state is reflected
    mockMvc
        .perform(post("/api/onboarding/dismiss").with(emptyOwnerJwt()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/onboarding/progress").with(emptyOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dismissed").value(true));
  }

  @Test
  @Order(8)
  void dismiss_memberRole_returns403() throws Exception {
    mockMvc
        .perform(post("/api/onboarding/dismiss").with(emptyMemberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void getProgress_memberRole_allowed() throws Exception {
    mockMvc
        .perform(get("/api/onboarding/progress").with(emptyMemberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps", hasSize(6)));
  }

  @Test
  @Order(10)
  void getProgress_withProject_createProjectComplete() throws Exception {
    // This uses the populated tenant which already has a project
    mockMvc
        .perform(get("/api/onboarding/progress").with(populatedOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps[0].code").value("CREATE_PROJECT"))
        .andExpect(jsonPath("$.steps[0].completed").value(true));
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

  private JwtRequestPostProcessor emptyOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_onb_empty_owner")
                    .claim("o", Map.of("id", EMPTY_ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor populatedOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_onb_pop_owner")
                    .claim("o", Map.of("id", POPULATED_ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor emptyMemberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_onb_empty_member")
                    .claim("o", Map.of("id", EMPTY_ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_onb_admin").claim("o", Map.of("id", ADMIN_ORG_ID, "rol", "admin")));
  }
}
