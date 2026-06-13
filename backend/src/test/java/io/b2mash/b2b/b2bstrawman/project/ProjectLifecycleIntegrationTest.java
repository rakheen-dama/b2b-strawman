package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectLifecycleIntegrationTest {
  private static final String ORG_ID = "org_proj_lifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AutomationRuleRepository automationRuleRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository auditEventRepository;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Lifecycle Test Org", null);
    disableSeededRules();

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_plc_owner", "plc_owner@test.com", "PLC Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_plc_admin", "plc_admin@test.com", "PLC Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_plc_member", "plc_member@test.com", "PLC Member", "member");
  }

  private void disableSeededRules() {
    String schemaName =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      automationRuleRepository.findAllByOrderByCreatedAtDesc().stream()
                          .filter(r -> r.getSource() == RuleSource.TEMPLATE && r.isEnabled())
                          .forEach(
                              r -> {
                                r.toggle();
                                automationRuleRepository.save(r);
                              });
                    }));
  }

  @Test
  void shouldCompleteProjectWhenAllTasksDone() throws Exception {
    String projectId = createProject("Complete All Tasks Done");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task to complete");
    claimAndCompleteTask(taskId);

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdOwner));
  }

  @Test
  void shouldRejectCompleteWithOpenTasks() throws Exception {
    String projectId = createProject("Complete Open Tasks");
    addMemberToProject(projectId, memberIdMember);
    createTaskInProject(projectId, "Open task");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCompleteWithUnbilledTimeWithoutAck() throws Exception {
    String projectId = createProject("Unbilled No Ack");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task with time");
    claimAndCompleteTask(taskId);

    // Create a billable time entry
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 60, "billable": true, "description": "Billable work"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldCompleteProjectWithUnbilledTimeWhenAcknowledged() throws Exception {
    String projectId = createProject("Unbilled With Ack");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task with time ack");
    claimAndCompleteTask(taskId);

    // Create a billable time entry
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 60, "billable": true, "description": "Billable work"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acknowledgeUnbilledTime": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void shouldArchiveFromActive() throws Exception {
    String projectId = createProject("Archive From Active");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"))
        .andExpect(jsonPath("$.archivedAt", notNullValue()));
  }

  @Test
  void shouldArchiveFromCompleted() throws Exception {
    String projectId = createProject("Archive From Completed");

    // Complete the project (no tasks, so no open task guardrail)
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void shouldArchiveWithOpenTasksSucceeds() throws Exception {
    String projectId = createProject("Archive With Open Tasks");
    addMemberToProject(projectId, memberIdMember);
    createTaskInProject(projectId, "Open task");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void shouldReopenFromCompleted() throws Exception {
    String projectId = createProject("Reopen From Completed");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/reopen")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.completedAt", nullValue()));
  }

  @Test
  void shouldReopenFromArchived() throws Exception {
    String projectId = createProject("Reopen From Archived");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/reopen")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.archivedAt", nullValue()));
  }

  @Test
  void shouldListProjectsDefaultActiveOnly() throws Exception {
    String activeProjectId = createProject("Active Default Filter");
    String archivedProjectId = createProject("Archived Default Filter");

    mockMvc
        .perform(
            patch("/api/projects/" + archivedProjectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", archivedProjectId).doesNotExist());
  }

  @Test
  void shouldListProjectsWithStatusFilter() throws Exception {
    String activeProjectId = createProject("Active Status Filter");
    String completedProjectId = createProject("Completed Status Filter");

    mockMvc
        .perform(
            patch("/api/projects/" + completedProjectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    // Filter for COMPLETED only
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .param("status", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", completedProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).doesNotExist());

    // Filter for ACTIVE,COMPLETED — should return both
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .param("status", "ACTIVE,COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", completedProjectId).exists());
  }

  @Test
  void shouldListProjectsWithStatusAll() throws Exception {
    String activeProjectId = createProject("Active ALL Filter");
    String archivedProjectId = createProject("Archived ALL Filter");
    String completedProjectId = createProject("Completed ALL Filter");

    mockMvc
        .perform(
            patch("/api/projects/" + archivedProjectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/projects/" + completedProjectId + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    // status=ALL should return projects in all statuses
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .param("status", "ALL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", archivedProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", completedProjectId).exists());
  }

  @Test
  void shouldRejectInvalidStatusParameter() throws Exception {
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .param("status", "BOGUS"))
        .andExpect(status().isBadRequest());
  }

  /**
   * Characterization test for the {@code dueBefore} list filter (TD-009 thin-controller refactor).
   * The filter retains only projects whose dueDate is strictly before the supplied date; projects
   * with a later dueDate or no dueDate at all are excluded. Captures the pre-refactor behavior so
   * moving the filter logic into {@link ProjectService} stays behavior-preserving.
   */
  @Test
  void shouldFilterProjectsByDueBefore() throws Exception {
    String earlyDueId =
        TestEntityHelper.createProjectWithDueDate(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"),
            "Due Early Filter",
            "2026-01-10");
    String lateDueId =
        TestEntityHelper.createProjectWithDueDate(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"),
            "Due Late Filter",
            "2026-12-31");
    String noDueId = createProject("No Due Date Filter");

    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .param("dueBefore", "2026-06-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", earlyDueId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", lateDueId).doesNotExist())
        .andExpect(jsonPath("$[?(@.id == '%s')]", noDueId).doesNotExist());
  }

  @Test
  void shouldRejectCreateTaskOnArchivedProject() throws Exception {
    String projectId = createProject("Archived No Task");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Should fail"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCreateTimeEntryOnArchivedProject() throws Exception {
    String projectId = createProject("Archived No Time Entry");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task before archive");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 30, "billable": false, "description": "Should fail"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectMemberFromCompletingProject() throws Exception {
    String projectId = createProject("Member Forbidden Complete");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectReopenOnActiveProject() throws Exception {
    String projectId = createProject("Reopen Active Fails");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/reopen")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isBadRequest());
  }

  /**
   * OBS-8801 — the full {@code project.*} lifecycle family must carry {@code details.project_id} so
   * the matter Activity feed surfaces them. The feed query ({@code
   * AuditEventRepository.findByProjectId}, exposed via {@code GET /api/projects/{id}/activity})
   * scopes strictly on {@code details->>'project_id'}; any emit site that omits the id silently
   * drops out of the feed.
   *
   * <p>This drives one project through created → updated → completed → reopened → archived →
   * reopened and a second project through created → deleted, then asserts every emitted {@code
   * project.*} event is returned by the feed. Covers 6 of the 7 emit sites; {@code
   * project.created_from_template} is covered by {@code InstantiateTemplateIntegrationTest} (it
   * lives in the template package and needs a persisted template).
   */
  @Test
  void projectLifecycleEventsAreSurfacedInMatterActivityFeed() throws Exception {
    // --- Project A: created -> updated -> completed -> reopened -> archived -> reopened ---
    String projectA = createProject("OBS-8801 Lifecycle Feed A");

    // updated
    mockMvc
        .perform(
            put("/api/projects/" + projectA)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"OBS-8801 Lifecycle Feed A (renamed)\"}"))
        .andExpect(status().isOk());

    // completed (no tasks -> no open-task guardrail)
    mockMvc
        .perform(
            patch("/api/projects/" + projectA + "/complete")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    // reopened (from completed)
    mockMvc
        .perform(
            patch("/api/projects/" + projectA + "/reopen")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    // archived
    mockMvc
        .perform(
            patch("/api/projects/" + projectA + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    // reopened (from archived — second project.reopened row)
    mockMvc
        .perform(
            patch("/api/projects/" + projectA + "/reopen")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isOk());

    var feedA =
        mockMvc
            .perform(
                get("/api/projects/" + projectA + "/activity")
                    .param("size", "50")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Every lifecycle event emitted for project A must appear in its matter Activity feed.
    for (String eventType :
        new String[] {
          "project.created",
          "project.updated",
          "project.completed",
          "project.archived",
          "project.reopened"
        }) {
      List<Object> matches =
          JsonPath.read(feedA, "$.content[?(@.eventType == '%s')]".formatted(eventType));
      assertThat(matches)
          .as(
              "%s missing from matter Activity feed — emit site dropped details.project_id (OBS-8801)",
              eventType)
          .isNotEmpty();
    }

    // --- Project B: created -> deleted (delete requires ACTIVE + owner) ---
    String projectB = createProject("OBS-8801 Lifecycle Feed B");
    mockMvc
        .perform(
            delete("/api/projects/" + projectB)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner")))
        .andExpect(status().isNoContent());

    // The HTTP feed endpoint guards on requireViewAccess, which 404s once the project is gone, so
    // project.deleted is asserted directly against the feed query
    // (AuditEventRepository.findByProjectId
    // — the same query the endpoint runs, scoping on details->>'project_id'). findByProjectId does
    // NOT
    // join the projects table, so the deletion row stays queryable by the deleted project's id.
    String schemaName =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var deletedFeed =
                  auditEventRepository.findByProjectId(
                      projectB, null, null, org.springframework.data.domain.PageRequest.of(0, 50));
              assertThat(deletedFeed.getContent())
                  .as(
                      "project.deleted missing from matter Activity feed (findByProjectId) — emit site"
                          + " dropped details.project_id (OBS-8801)")
                  .extracting(io.b2mash.b2b.b2bstrawman.audit.AuditEvent::getEventType)
                  .contains("project.deleted");
            });
  }

  // --- Helper Methods ---

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Lifecycle test project"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private String createTaskInProject(String projectId, String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addMemberToProject(String projectId, String memberId) throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_plc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private void claimAndCompleteTask(String taskId) throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/complete")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_plc_member")))
        .andExpect(status().isOk());
  }
}
