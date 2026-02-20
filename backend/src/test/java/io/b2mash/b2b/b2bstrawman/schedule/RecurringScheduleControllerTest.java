package io.b2mash.b2b.b2bstrawman.schedule;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecurringScheduleControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_schedule_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;
  private UUID templateId;
  private UUID customerId;
  private String createdScheduleId;
  private UUID activeScheduleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Schedule Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    ownerMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_sched_ctrl_owner", "sched_ctrl_owner@test.com", "Owner", "owner"));
    memberMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_sched_ctrl_member",
                "sched_ctrl_member@test.com",
                "Member",
                "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var template =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Monthly Bookkeeping",
                                  "{customer} - {month} {year}",
                                  "Standard monthly template",
                                  true,
                                  "MANUAL",
                                  null,
                                  ownerMemberId));
                      templateId = template.getId();

                      var customer =
                          customerRepository.saveAndFlush(
                              new Customer(
                                  "Acme Corp", "acme@test.com", null, null, null, ownerMemberId));
                      customerId = customer.getId();

                      var activeSchedule =
                          scheduleRepository.saveAndFlush(
                              new RecurringSchedule(
                                  template.getId(),
                                  customer.getId(),
                                  null,
                                  "QUARTERLY",
                                  LocalDate.now(),
                                  null,
                                  0,
                                  null,
                                  ownerMemberId));
                      activeScheduleId = activeSchedule.getId();
                    }));
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void shouldListSchedules() throws Exception {
    mockMvc
        .perform(get("/api/schedules").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(2)
  void shouldCreateSchedule() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/schedules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "templateId": "%s",
                          "customerId": "%s",
                          "frequency": "MONTHLY",
                          "startDate": "2026-03-01",
                          "leadTimeDays": 5
                        }
                        """
                            .formatted(templateId, customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.templateName").value("Monthly Bookkeeping"))
            .andExpect(jsonPath("$.customerName").value("Acme Corp"))
            .andExpect(jsonPath("$.frequency").value("MONTHLY"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

    createdScheduleId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(3)
  void shouldGetScheduleById() throws Exception {
    mockMvc
        .perform(get("/api/schedules/" + createdScheduleId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdScheduleId))
        .andExpect(jsonPath("$.templateName").value("Monthly Bookkeeping"))
        .andExpect(jsonPath("$.customerName").value("Acme Corp"));
  }

  @Test
  @Order(4)
  void shouldReturn404WhenCreatingWithMissingTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "templateId": "%s",
                      "customerId": "%s",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "leadTimeDays": 0
                    }
                    """
                        .formatted(UUID.randomUUID(), customerId)))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(5)
  void shouldUpdateSchedule() throws Exception {
    mockMvc
        .perform(
            put("/api/schedules/" + createdScheduleId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "leadTimeDays": 7,
                      "nameOverride": "Updated Name"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdScheduleId))
        .andExpect(jsonPath("$.leadTimeDays").value(7))
        .andExpect(jsonPath("$.nameOverride").value("Updated Name"));
  }

  @Test
  @Order(6)
  void shouldPauseSchedule() throws Exception {
    mockMvc
        .perform(post("/api/schedules/" + createdScheduleId + "/pause").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));
  }

  @Test
  @Order(7)
  void shouldDeletePausedSchedule() throws Exception {
    mockMvc
        .perform(delete("/api/schedules/" + createdScheduleId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/schedules/" + createdScheduleId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(8)
  void shouldReturn409WhenDeletingActiveSchedule() throws Exception {
    mockMvc
        .perform(delete("/api/schedules/" + activeScheduleId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(9)
  void shouldGetExecutionsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/schedules/" + activeScheduleId + "/executions").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- Lifecycle + Permission Tests ---

  @Test
  @Order(10)
  void shouldCreateAndResumeSchedule() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/schedules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "templateId": "%s",
                          "customerId": "%s",
                          "frequency": "WEEKLY",
                          "startDate": "2026-04-01",
                          "leadTimeDays": 2
                        }
                        """
                            .formatted(templateId, customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    String newScheduleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Pause it
    mockMvc
        .perform(post("/api/schedules/" + newScheduleId + "/pause").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));

    // Resume it
    mockMvc
        .perform(post("/api/schedules/" + newScheduleId + "/resume").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @Order(11)
  void memberCannotCreateSchedule() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "templateId": "%s",
                      "customerId": "%s",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "leadTimeDays": 0
                    }
                    """
                        .formatted(templateId, customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(12)
  void memberCannotUpdateSchedule() throws Exception {
    mockMvc
        .perform(
            put("/api/schedules/" + activeScheduleId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"leadTimeDays": 3}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(13)
  void memberCannotPauseSchedule() throws Exception {
    mockMvc
        .perform(post("/api/schedules/" + activeScheduleId + "/pause").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(14)
  void memberCanListSchedules() throws Exception {
    mockMvc.perform(get("/api/schedules").with(memberJwt())).andExpect(status().isOk());
  }

  @Test
  @Order(15)
  void shouldListSchedulesFilteredByStatus() throws Exception {
    mockMvc
        .perform(get("/api/schedules").param("status", "ACTIVE").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  // --- Helper methods ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_sched_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_sched_ctrl_member")
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
