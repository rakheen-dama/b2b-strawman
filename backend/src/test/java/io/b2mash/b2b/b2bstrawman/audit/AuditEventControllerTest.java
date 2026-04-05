package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuditEventControllerTest extends AbstractIntegrationTest {
  private static final String ORG_ID = "org_audit_ctrl_test";

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String projectId;
  private String taskId;
  private String project2Id;
  private String project3Id;
  private Instant beforeCreation;
  private Instant afterCreation;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Audit Controller Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ac_owner", "ac_owner@test.com", "AC Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ac_admin", "ac_admin@test.com", "AC Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ac_member", "ac_member@test.com", "AC Member", "member");

    beforeCreation = Instant.now();

    // Create a project (produces project.created audit event)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project", "description": "For audit controller tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add member to project so we can create tasks
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    // Create a task (produces task.created audit event)
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Audit Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    // Create additional projects for pagination tests
    var project2Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project 2", "description": "Second project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    project2Id = TestEntityHelper.extractIdFromLocation(project2Result);

    var project3Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project 3", "description": "Third project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    project3Id = TestEntityHelper.extractIdFromLocation(project3Result);

    afterCreation = Instant.now();
  }

  @Test
  void ownerCanListAuditEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(1)));
  }

  @Test
  void adminCanListAuditEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(TestJwtFactory.adminJwt(ORG_ID, "user_ac_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(1)));
  }

  @Test
  void regularMemberDenied() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(TestJwtFactory.memberJwt(ORG_ID, "user_ac_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void filterByEntityTypeReturnsOnlyMatchingEvents() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("entityType", "project"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(
            jsonPath("$.content[*].entityType", everyItem(org.hamcrest.Matchers.is("project"))));
  }

  @Test
  void filterByEntityIdReturnsEventsForSpecificEntity() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("entityType", "task")
                .param("entityId", taskId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[0].entityId").value(taskId));
  }

  @Test
  void filterByEventTypePrefixMatchesMultipleTypes() throws Exception {
    // "project." should match project.created (and any other project.* events)
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("eventType", "project."))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[*].eventType", everyItem(startsWith("project."))));
  }

  @Test
  void filterByTimeRange() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("from", beforeCreation.toString())
                .param("to", afterCreation.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

    // Events before our test setup should return empty (use a time far in the past)
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("from", "2020-01-01T00:00:00Z")
                .param("to", "2020-01-02T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void paginationWorks() throws Exception {
    // We created at least 3 projects + 1 task = 4+ audit events
    // Request page 0 with size 2
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("size", "2")
                .param("page", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.totalPages", greaterThanOrEqualTo(2)));

    // Request page 1
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner"))
                .param("size", "2")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.page.number").value(1));
  }

  @Test
  void entitySpecificEndpointReturnsCorrectEvents() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/task/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[0].entityType").value("task"))
        .andExpect(jsonPath("$.content[0].entityId").value(taskId));
  }

  @Test
  void responseIncludesSecurityFieldsButExcludesTenantId() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/audit-events").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ac_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0]").exists())
            .andExpect(jsonPath("$.content[0].ipAddress", is("127.0.0.1")))
            .andExpect(jsonPath("$.content[0].tenantId").doesNotExist())
            // Verify expected fields ARE present
            .andExpect(jsonPath("$.content[0].id").exists())
            .andExpect(jsonPath("$.content[0].eventType").exists())
            .andExpect(jsonPath("$.content[0].entityType").exists())
            .andExpect(jsonPath("$.content[0].entityId").exists())
            .andExpect(jsonPath("$.content[0].occurredAt").exists())
            .andReturn();

    // Verify userAgent key is present in JSON (even if null — MockMvc doesn't set User-Agent)
    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("\"userAgent\""), "Response should include userAgent field");
  }
}
