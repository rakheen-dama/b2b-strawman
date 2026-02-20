package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskCreateWithAssigneeIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_task_assignee_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Assignee Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdAdmin =
        syncMember(ORG_ID, "user_assignee_admin", "admin@assignee-test.com", "Admin User", "admin");
    memberIdMember =
        syncMember(
            ORG_ID, "user_assignee_member", "member@assignee-test.com", "Regular Member", "member");

    // Create project (admin is project lead by default)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Assignee Test Project", "description": "For assignee tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add regular member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
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
                .with(adminJwt())
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
                .with(memberJwt())
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
        .andExpect(jsonPath("$.assigneeId").isEmpty())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
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

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_assignee_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_assignee_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
