package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskCreateWithAssigneeIntegrationTest {
  private static final String ORG_ID = "org_task_assignee_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Assignee Test Org", null);

    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_assignee_admin",
            "admin@assignee-test.com",
            "Admin User",
            "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_assignee_member",
            "member@assignee-test.com",
            "Regular Member",
            "member");

    // Create project (admin is project lead by default)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_assignee_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Assignee Test Project", "description": "For assignee tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add regular member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_assignee_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldCreateTaskWithAssigneeAsAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_assignee_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Pre-assigned Task",
                      "priority": "HIGH",
                      "assigneeId": "%s"
                    }
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Pre-assigned Task"))
        .andExpect(jsonPath("$.assigneeId").value(memberIdAdmin))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void shouldIgnoreAssigneeIdForRegularMember() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_assignee_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Member Created Task",
                      "assigneeId": "%s"
                    }
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Member Created Task"))
        .andExpect(jsonPath("$.assigneeId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  // --- Helpers ---

}
