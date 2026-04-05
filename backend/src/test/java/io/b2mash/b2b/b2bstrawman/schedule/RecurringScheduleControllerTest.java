package io.b2mash.b2b.b2bstrawman.schedule;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.util.Set;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecurringScheduleControllerTest {
  private static final String ORG_ID = "org_schedule_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;
  private UUID templateId;
  private UUID customerId;
  private String createdScheduleId;
  private UUID activeScheduleId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;
  private String scheduleWithActionsId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Schedule Controller Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sched_ctrl_owner",
                "sched_ctrl_owner@test.com",
                "Owner",
                "owner"));
    memberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
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
                              createActiveCustomer("Acme Corp", "acme@test.com", ownerMemberId));
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

    // Assign system owner role to owner member for capability-based auth
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sched_314b_custom",
                "sched_custom@test.com",
                "Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sched_314b_nocap",
                "sched_nocap@test.com",
                "NoCap User",
                "member"));

    // Assign OrgRoles within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Project Manager", "Has project cap", Set.of("PROJECT_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead", "No project cap", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void shouldListSchedules() throws Exception {
    mockMvc
        .perform(
            get("/api/schedules").with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
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
        .perform(
            get("/api/schedules/" + createdScheduleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
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
        .perform(
            post("/api/schedules/" + createdScheduleId + "/pause")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));
  }

  @Test
  @Order(7)
  void shouldDeletePausedSchedule() throws Exception {
    mockMvc
        .perform(
            delete("/api/schedules/" + createdScheduleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(
            get("/api/schedules/" + createdScheduleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(8)
  void shouldReturn409WhenDeletingActiveSchedule() throws Exception {
    mockMvc
        .perform(
            delete("/api/schedules/" + activeScheduleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(9)
  void shouldGetExecutionsEmpty() throws Exception {
    mockMvc
        .perform(
            get("/api/schedules/" + activeScheduleId + "/executions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
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
        .perform(
            post("/api/schedules/" + newScheduleId + "/pause")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));

    // Resume it
    mockMvc
        .perform(
            post("/api/schedules/" + newScheduleId + "/resume")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @Order(11)
  void memberCannotCreateSchedule() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_ctrl_member"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_ctrl_member"))
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
        .perform(
            post("/api/schedules/" + activeScheduleId + "/pause")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_ctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(14)
  void memberCanListSchedules() throws Exception {
    mockMvc
        .perform(
            get("/api/schedules").with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_ctrl_member")))
        .andExpect(status().isOk());
  }

  @Test
  @Order(15)
  void shouldListSchedulesFilteredByStatus() throws Exception {
    mockMvc
        .perform(
            get("/api/schedules")
                .param("status", "ACTIVE")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  // --- Capability Tests ---

  @Test
  @Order(20)
  void customRoleWithCapability_accessesScheduleEndpoint_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_314b_custom"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "templateId": "%s",
                      "customerId": "%s",
                      "frequency": "MONTHLY",
                      "startDate": "2026-06-01",
                      "leadTimeDays": 3
                    }
                    """
                        .formatted(templateId, customerId)))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(21)
  void customRoleWithoutCapability_accessesScheduleEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_sched_314b_nocap"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "templateId": "%s",
                      "customerId": "%s",
                      "frequency": "MONTHLY",
                      "startDate": "2026-06-01",
                      "leadTimeDays": 3
                    }
                    """
                        .formatted(templateId, customerId)))
        .andExpect(status().isForbidden());
  }

  // --- postCreateActions Tests ---

  @Test
  @Order(30)
  void shouldCreateScheduleWithPostCreateActions() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/schedules")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "templateId": "%s",
                          "customerId": "%s",
                          "frequency": "SEMI_ANNUALLY",
                          "startDate": "2026-07-01",
                          "leadTimeDays": 3,
                          "postCreateActions": {
                            "generateDocument": {
                              "templateSlug": "engagement-letter-tax-return",
                              "autoSend": false
                            },
                            "sendInfoRequest": {
                              "requestTemplateSlug": "year-end-info-request-za",
                              "dueDays": 14
                            }
                          }
                        }
                        """
                            .formatted(templateId, customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(
                jsonPath("$.postCreateActions.generateDocument.templateSlug")
                    .value("engagement-letter-tax-return"))
            .andExpect(jsonPath("$.postCreateActions.generateDocument.autoSend").value(false))
            .andExpect(
                jsonPath("$.postCreateActions.sendInfoRequest.requestTemplateSlug")
                    .value("year-end-info-request-za"))
            .andExpect(jsonPath("$.postCreateActions.sendInfoRequest.dueDays").value(14))
            .andReturn();

    scheduleWithActionsId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(31)
  void shouldGetScheduleWithPostCreateActions() throws Exception {
    mockMvc
        .perform(
            get("/api/schedules/" + scheduleWithActionsId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(scheduleWithActionsId))
        .andExpect(
            jsonPath("$.postCreateActions.generateDocument.templateSlug")
                .value("engagement-letter-tax-return"))
        .andExpect(jsonPath("$.postCreateActions.generateDocument.autoSend").value(false))
        .andExpect(
            jsonPath("$.postCreateActions.sendInfoRequest.requestTemplateSlug")
                .value("year-end-info-request-za"))
        .andExpect(jsonPath("$.postCreateActions.sendInfoRequest.dueDays").value(14));
  }

  @Test
  @Order(32)
  void shouldUpdatePostCreateActions() throws Exception {
    mockMvc
        .perform(
            put("/api/schedules/" + scheduleWithActionsId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "leadTimeDays": 5,
                      "postCreateActions": {
                        "generateDocument": {
                          "templateSlug": "updated-template",
                          "autoSend": true
                        }
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(scheduleWithActionsId))
        .andExpect(
            jsonPath("$.postCreateActions.generateDocument.templateSlug").value("updated-template"))
        .andExpect(jsonPath("$.postCreateActions.generateDocument.autoSend").value(true))
        .andExpect(jsonPath("$.postCreateActions.sendInfoRequest").doesNotExist());
  }

  @Test
  @Order(33)
  void shouldCreateScheduleWithoutPostCreateActions() throws Exception {
    mockMvc
        .perform(
            post("/api/schedules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sched_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "templateId": "%s",
                      "customerId": "%s",
                      "frequency": "ANNUALLY",
                      "startDate": "2026-08-01",
                      "leadTimeDays": 0
                    }
                    """
                        .formatted(templateId, customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.postCreateActions").doesNotExist());
  }
}
