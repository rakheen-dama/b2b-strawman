package io.b2mash.b2b.b2bstrawman.activity;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests for {@link ActivityController}. Tests the activity feed endpoint with MockMvc
 * against a real database via Testcontainers. Seeds audit events with project_id in details JSONB
 * to verify the findByProjectId native query.
 */
class ActivityControllerTest extends AbstractIntegrationTest {
  private static final String ORG_ID = "org_activity_ctrl_test";

  @Autowired private AuditService auditService;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;
  private UUID ownerUuid;
  private UUID memberUuid;
  private String projectId;
  private UUID projectUuid;
  private Instant seedTime;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Activity Ctrl Test Org", null).schemaName();

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ac_owner", "ac_owner@test.com", "AC Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ac_member", "ac_member@test.com", "AC Member", "member");

    ownerUuid = UUID.fromString(memberIdOwner);
    memberUuid = UUID.fromString(memberIdMember);

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Activity Ctrl Test Project", "description": "For activity ctrl tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);
    projectUuid = UUID.fromString(projectId);

    // Add member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdMember)))
        .andExpect(status().isCreated());

    // Record the seed time before creating audit events
    seedTime = Instant.now();

    // Seed audit events with project_id in details
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerUuid)
        .run(
            () -> {
              UUID taskId = UUID.randomUUID();

              // Task created event
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      taskId,
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Test Task", "project_id", projectUuid.toString())));

              sleep(50);

              // Task claimed event
              auditService.log(
                  new AuditEventRecord(
                      "task.claimed",
                      "task",
                      taskId,
                      memberUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Test Task", "project_id", projectUuid.toString())));

              sleep(50);

              // Document uploaded event
              auditService.log(
                  new AuditEventRecord(
                      "document.uploaded",
                      "document",
                      UUID.randomUUID(),
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of(
                          "file_name", "requirements.pdf", "project_id", projectUuid.toString())));

              sleep(50);

              // Comment created event
              auditService.log(
                  new AuditEventRecord(
                      "comment.created",
                      "comment",
                      UUID.randomUUID(),
                      memberUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("entity_type", "task", "project_id", projectUuid.toString())));
            });
  }

  @Test
  void getActivityReturnsPaginatedItemsWithCorrectShape() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.page.totalElements").value(5))
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].message").exists())
        .andExpect(jsonPath("$.content[0].actorName").exists())
        .andExpect(jsonPath("$.content[0].entityType").exists())
        .andExpect(jsonPath("$.content[0].entityId").exists())
        .andExpect(jsonPath("$.content[0].eventType").exists())
        .andExpect(jsonPath("$.content[0].occurredAt").exists());
  }

  @Test
  void filterByEntityTypeReturnsOnlyMatchingEvents() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("entityType", "TASK")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].entityType").value("task"))
        .andExpect(jsonPath("$.content[1].entityType").value("task"));
  }

  @Test
  void filterBySinceReturnsOnlyEventsAfterTimestamp() throws Exception {
    // Use a future timestamp to ensure no events match
    String futureTimestamp = Instant.now().plus(1, ChronoUnit.HOURS).toString();

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("since", futureTimestamp)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void emptyProjectReturnsEmptyList() throws Exception {
    // Create a second project with no audit events
    var emptyProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Empty Activity Project", "description": "No audit events"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String emptyProjectId = TestEntityHelper.extractIdFromLocation(emptyProjectResult);

    mockMvc
        .perform(
            get("/api/projects/" + emptyProjectId + "/activity")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void nonMemberReturns404() throws Exception {
    // Create a different org with a different member who is NOT on this project
    String otherOrgId = "org_activity_other";
    provisioningService.provisionTenant(otherOrgId, "Other Org", null);

    String otherMemberId =
        TestMemberHelper.syncMember(
            mockMvc, otherOrgId, "user_ac_other", "ac_other@test.com", "AC Other", "member");

    // This member is in a different org, so they should get a 404
    var otherJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_ac_other")
                        .claim("o", Map.of("id", otherOrgId, "rol", "member")));

    mockMvc
        .perform(get("/api/projects/" + projectId + "/activity").with(otherJwt))
        .andExpect(status().isNotFound());
  }

  @Test
  void responseFormatContainsActorNameAndMessage() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("entityType", "DOCUMENT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].actorName").value("AC Owner"))
        .andExpect(
            jsonPath("$.content[0].message")
                .value("AC Owner uploaded document \"requirements.pdf\""))
        .andExpect(jsonPath("$.content[0].entityType").value("document"))
        .andExpect(jsonPath("$.content[0].eventType").value("document.uploaded"));
  }

  // --- Helpers ---

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
