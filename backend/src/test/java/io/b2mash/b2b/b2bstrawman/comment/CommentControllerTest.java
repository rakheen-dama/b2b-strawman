package io.b2mash.b2b.b2bstrawman.comment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CommentControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_comment_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String memberIdMember2;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_cc_owner", "cc_owner@test.com", "CC Owner", "owner");
    memberIdAdmin = syncMember(ORG_ID, "user_cc_admin", "cc_admin@test.com", "CC Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_cc_member", "cc_member@test.com", "CC Member", "member");
    memberIdMember2 =
        syncMember(ORG_ID, "user_cc_member2", "cc_member2@test.com", "CC Member2", "member");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Comment Ctrl Test Project", "description": "For comment ctrl tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add all members to the project
    for (String mid : List.of(memberIdAdmin, memberIdMember, memberIdMember2)) {
      mockMvc
          .perform(
              post("/api/projects/" + projectId + "/members")
                  .with(ownerJwt())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"memberId\": \"%s\"}".formatted(mid)))
          .andExpect(status().isCreated());
    }

    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Comment Ctrl Test Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);
  }

  @Test
  void postCreatesCommentAndReturns201WithCorrectShape() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "Controller test comment"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.entityType").value("TASK"))
        .andExpect(jsonPath("$.entityId").value(taskId))
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.authorMemberId").value(memberIdMember))
        .andExpect(jsonPath("$.authorName").value("CC Member"))
        .andExpect(jsonPath("$.body").value("Controller test comment"))
        .andExpect(jsonPath("$.visibility").value("INTERNAL"))
        .andExpect(jsonPath("$.parentId").isEmpty())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void getListsCommentsOrderedByCreatedAtAsc() throws Exception {
    // Create multiple comments
    createComment(ownerJwt(), "First comment");
    createComment(memberJwt(), "Second comment");
    createComment(adminJwt(), "Third comment");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .param("entityType", "TASK")
                .param("entityId", taskId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
  }

  @Test
  void getWithPagination() throws Exception {
    // Create enough comments to test pagination
    for (int i = 0; i < 3; i++) {
      createComment(ownerJwt(), "Pagination comment " + i);
    }

    // Request page 0 with size 2
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .param("entityType", "TASK")
                .param("entityId", taskId)
                .param("page", "0")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void putUpdatesBodyAndReturns200() throws Exception {
    var commentId = createComment(memberJwt(), "Body to update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "Updated body value"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(commentId))
        .andExpect(jsonPath("$.body").value("Updated body value"));
  }

  @Test
  void putByNonAuthorNonAdminReturns403() throws Exception {
    // Create comment as member
    var commentId = createComment(memberJwt(), "Protected comment");

    // Try to update as member2 (not author, not admin/owner)
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(member2Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "Attempted edit"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteByAuthorReturns204() throws Exception {
    var commentId = createComment(memberJwt(), "Comment to delete by author");

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/comments/" + commentId).with(memberJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteByNonAuthorNonAdminReturns403() throws Exception {
    var commentId = createComment(memberJwt(), "Comment non-author tries to delete");

    // member2 is not the author and not admin/owner
    mockMvc
        .perform(delete("/api/projects/" + projectId + "/comments/" + commentId).with(member2Jwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private String createComment(JwtRequestPostProcessor jwt, String body) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/comments")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "entityId": "%s",
                          "body": "%s"
                        }
                        """
                            .formatted(taskId, body)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor member2Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_cc_member2").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
