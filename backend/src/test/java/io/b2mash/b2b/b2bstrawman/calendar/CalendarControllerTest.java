package io.b2mash.b2b.b2bstrawman.calendar;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_calendar_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID adminMemberId;
  private UUID member1Id;
  private UUID member2Id;
  private UUID project1Id;
  private UUID project2Id;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Calendar Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    adminMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cal_admin", "cal_admin@test.com", "Admin User", "admin"));
    member1Id =
        UUID.fromString(
            syncMember(ORG_ID, "user_cal_member1", "cal_m1@test.com", "Member One", "member"));
    member2Id =
        UUID.fromString(
            syncMember(ORG_ID, "user_cal_member2", "cal_m2@test.com", "Member Two", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(this::seedData);
  }

  private void seedData() {
    // Project 1: due in 10 days, member1 is a member
    var project1 =
        projectService.createProject(
            "Project Alpha",
            "Test project 1",
            adminMemberId,
            null,
            null,
            null,
            LocalDate.now().plusDays(10));
    project1Id = project1.getId();
    projectMemberService.addMember(project1Id, member1Id, adminMemberId);

    // Project 2: due in 20 days, member2 is a member (NOT member1)
    var project2 =
        projectService.createProject(
            "Project Beta",
            "Test project 2",
            adminMemberId,
            null,
            null,
            null,
            LocalDate.now().plusDays(20));
    project2Id = project2.getId();
    projectMemberService.addMember(project2Id, member2Id, adminMemberId);

    // Task 1: in project 1, due in 5 days, assigned to member1
    var task1 =
        taskService.createTask(
            project1Id,
            "Task Alpha",
            "desc",
            "HIGH",
            "GENERAL",
            LocalDate.now().plusDays(5),
            adminMemberId,
            "admin");
    taskService.updateTask(
        task1.getId(),
        "Task Alpha",
        "desc",
        "HIGH",
        "OPEN",
        "GENERAL",
        LocalDate.now().plusDays(5),
        member1Id,
        adminMemberId,
        "admin");

    // Task 2: in project 2, due in 15 days, assigned to member2
    var task2 =
        taskService.createTask(
            project2Id,
            "Task Beta",
            "desc",
            "MEDIUM",
            "GENERAL",
            LocalDate.now().plusDays(15),
            adminMemberId,
            "admin");
    taskService.updateTask(
        task2.getId(),
        "Task Beta",
        "desc",
        "MEDIUM",
        "OPEN",
        "GENERAL",
        LocalDate.now().plusDays(15),
        member2Id,
        adminMemberId,
        "admin");

    // Task 3: in project 1, overdue (5 days ago), assigned to member1
    var task3 =
        taskService.createTask(
            project1Id,
            "Overdue Task",
            "desc",
            "URGENT",
            "GENERAL",
            LocalDate.now().minusDays(5),
            adminMemberId,
            "admin");
    taskService.updateTask(
        task3.getId(),
        "Overdue Task",
        "desc",
        "URGENT",
        "OPEN",
        "GENERAL",
        LocalDate.now().minusDays(5),
        member1Id,
        adminMemberId,
        "admin");

    // Task 4: in project 1, DONE (should be excluded)
    var task4 =
        taskService.createTask(
            project1Id,
            "Done Task",
            "desc",
            "LOW",
            "GENERAL",
            LocalDate.now().plusDays(3),
            adminMemberId,
            "admin");
    taskService.updateTask(
        task4.getId(),
        "Done Task",
        "desc",
        "LOW",
        "IN_PROGRESS",
        "GENERAL",
        LocalDate.now().plusDays(3),
        member1Id,
        adminMemberId,
        "admin");
    taskService.updateTask(
        task4.getId(),
        "Done Task",
        "desc",
        "LOW",
        "DONE",
        "GENERAL",
        LocalDate.now().plusDays(3),
        member1Id,
        adminMemberId,
        "admin");
  }

  @Test
  void adminSeesAllProjectsAndTasks() throws Exception {
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(30).toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        // Should see: Task Alpha (5d), Project Alpha (10d), Task Beta (15d), Project Beta (20d)
        // NOT: Overdue Task (before range), Done Task (DONE status)
        .andExpect(jsonPath("$.items", hasSize(4)))
        .andExpect(jsonPath("$.overdueCount").value(0));
  }

  @Test
  void memberSeesOnlyAccessibleProjects() throws Exception {
    // member1 is only on project1, should only see project1's items
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(30).toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        // Should see: Task Alpha (5d), Project Alpha (10d) — NOT project2 items
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].name").value("Task Alpha"))
        .andExpect(jsonPath("$.items[0].itemType").value("TASK"))
        .andExpect(jsonPath("$.items[1].name").value("Project Alpha"))
        .andExpect(jsonPath("$.items[1].itemType").value("PROJECT"));
  }

  @Test
  void overdueIncludesOverdueItemsAndCount() throws Exception {
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().plusDays(30).toString())
                .param("overdue", "true")
                .with(adminJwt()))
        .andExpect(status().isOk())
        // Should include the overdue task in addition to in-range items
        .andExpect(jsonPath("$.overdueCount").value(1))
        .andExpect(jsonPath("$.items[?(@.name == 'Overdue Task')].itemType").value("TASK"));
  }

  @Test
  void filterByTypeReturnsOnlyMatchingItems() throws Exception {
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(30).toString())
                .param("type", "PROJECT")
                .with(adminJwt()))
        .andExpect(status().isOk())
        // Should see only Project Alpha and Project Beta
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].itemType").value("PROJECT"))
        .andExpect(jsonPath("$.items[1].itemType").value("PROJECT"));
  }

  @Test
  void filterByProjectReturnsOnlyMatchingItems() throws Exception {
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(30).toString())
                .param("projectId", project1Id.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        // Should see Task Alpha and Project Alpha only
        .andExpect(jsonPath("$.items", hasSize(2)));
  }

  @Test
  void invalidDateRangeReturnsBadRequest() throws Exception {
    // from > to
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().plusDays(10).toString())
                .param("to", LocalDate.now().toString())
                .with(adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void dateRangeExceeding366DaysReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/calendar")
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().plusDays(400).toString())
                .with(adminJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cal_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor member1Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_cal_member1").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Member Sync Helper ---

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
