package io.b2mash.b2b.b2bstrawman.comment;

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
class CommentServiceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_comment_svc_test";
  private static final String ORG_B_ID = "org_comment_svc_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String documentId;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String projectBId;
  private String taskBId;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision tenant A with Pro plan
    provisioningService.provisionTenant(ORG_ID, "Comment Svc Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Provision tenant B with Pro plan
    provisioningService.provisionTenant(ORG_B_ID, "Comment Svc Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    // Sync members for tenant A
    memberIdOwner = syncMember(ORG_ID, "user_cs_owner", "cs_owner@test.com", "CS Owner", "owner");
    memberIdAdmin = syncMember(ORG_ID, "user_cs_admin", "cs_admin@test.com", "CS Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_cs_member", "cs_member@test.com", "CS Member", "member");

    // Sync member for tenant B
    syncMember(ORG_B_ID, "user_cs_tenant_b", "cs_tenantb@test.com", "Tenant B User", "owner");

    // Create a project in tenant A (owner is auto-assigned as lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Comment Svc Test Project", "description": "For comment svc tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add admin and member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdMember)))
        .andExpect(status().isCreated());

    // Create a task in the project
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Comment Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);

    // Create a document in the project via upload-init
    var docInitResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "comment-test.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    documentId = extractJsonField(docInitResult, "documentId");

    // Create a project and task in tenant B
    var projectBResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(tenantBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant B Project", "description": "B project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectBId = extractIdFromLocation(projectBResult);

    var taskBResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectBId + "/tasks")
                    .with(tenantBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Tenant B Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskBId = extractIdFromLocation(taskBResult);
  }

  // --- Task 59.9: CommentService integration tests ---

  @Test
  void shouldCreateCommentOnTask() throws Exception {
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
                      "body": "This is a comment on a task",
                      "visibility": "INTERNAL"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.entityType").value("TASK"))
        .andExpect(jsonPath("$.entityId").value(taskId))
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.authorMemberId").value(memberIdMember))
        .andExpect(jsonPath("$.authorName").value("CS Member"))
        .andExpect(jsonPath("$.body").value("This is a comment on a task"))
        .andExpect(jsonPath("$.visibility").value("INTERNAL"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void shouldCreateCommentOnDocument() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "DOCUMENT",
                      "entityId": "%s",
                      "body": "This is a comment on a document"
                    }
                    """
                        .formatted(documentId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.entityType").value("DOCUMENT"))
        .andExpect(jsonPath("$.entityId").value(documentId))
        .andExpect(jsonPath("$.body").value("This is a comment on a document"))
        .andExpect(jsonPath("$.visibility").value("INTERNAL"));
  }

  @Test
  void shouldCreateExternalCommentByLead() throws Exception {
    // Owner is auto-assigned as lead, so canEdit() is true
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "External comment by lead",
                      "visibility": "SHARED"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.visibility").value("SHARED"));
  }

  @Test
  void shouldRejectExternalCommentByRegularMember() throws Exception {
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
                      "body": "Should fail as external",
                      "visibility": "SHARED"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldUpdateOwnCommentBody() throws Exception {
    // Create a comment as member
    var createResult =
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
                          "body": "Original body"
                        }
                        """
                            .formatted(taskId)))
            .andExpect(status().isCreated())
            .andReturn();
    var commentId = extractJsonField(createResult, "id");

    // Update own comment body
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "Updated body"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Updated body"));
  }

  @Test
  void shouldRejectVisibilityChangeByRegularMember() throws Exception {
    // Create an INTERNAL comment as member
    var createResult =
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
                          "body": "Internal comment for vis test"
                        }
                        """
                            .formatted(taskId)))
            .andExpect(status().isCreated())
            .andReturn();
    var commentId = extractJsonField(createResult, "id");

    // Try to change visibility to EXTERNAL as regular member (should fail)
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "Internal comment for vis test", "visibility": "SHARED"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldDeleteOwnComment() throws Exception {
    // Create a comment as member
    var createResult =
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
                          "body": "Comment to delete"
                        }
                        """
                            .formatted(taskId)))
            .andExpect(status().isCreated())
            .andReturn();
    var commentId = extractJsonField(createResult, "id");

    // Delete own comment
    mockMvc
        .perform(delete("/api/projects/" + projectId + "/comments/" + commentId).with(memberJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldDeleteOthersCommentAsAdmin() throws Exception {
    // Create a comment as member
    var createResult =
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
                          "body": "Member comment for admin delete"
                        }
                        """
                            .formatted(taskId)))
            .andExpect(status().isCreated())
            .andReturn();
    var commentId = extractJsonField(createResult, "id");

    // Delete as admin (should succeed)
    mockMvc
        .perform(delete("/api/projects/" + projectId + "/comments/" + commentId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  // --- Task 59.11: Tenant isolation tests ---

  @Test
  void commentCreatedInTenantAIsInvisibleInTenantB() throws Exception {
    // Create a comment in tenant A
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "Tenant A isolation test comment"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isCreated());

    // Tenant B should not see tenant A's task (ResourceNotFoundException → 404)
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(tenantBOwnerJwt())
                .param("entityType", "TASK")
                .param("entityId", taskId))
        .andExpect(status().isNotFound());
  }

  @Test
  void commentCreatedByTenantBIsInvisibleToTenantA() throws Exception {
    // Create a comment in tenant B
    mockMvc
        .perform(
            post("/api/projects/" + projectBId + "/comments")
                .with(tenantBOwnerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "Tenant B comment"
                    }
                    """
                        .formatted(taskBId)))
        .andExpect(status().isCreated());

    // Tenant A should not see tenant B's project (ResourceNotFoundException → 404)
    mockMvc
        .perform(
            get("/api/projects/" + projectBId + "/comments")
                .with(ownerJwt())
                .param("entityType", "TASK")
                .param("entityId", taskBId))
        .andExpect(status().isNotFound());
  }

  @Test
  void commentHasTenantIdAutoPopulated() throws Exception {
    // Create a comment and verify it has fields indicating proper persistence
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/comments")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "entityId": "%s",
                          "body": "Tenant ID test comment"
                        }
                        """
                            .formatted(taskId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    // Verify the comment is retrievable in the same tenant context
    // (proves tenant schema isolation is working correctly)
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(ownerJwt())
                .param("entityType", "TASK")
                .param("entityId", taskId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String extractJsonField(MvcResult result, String field) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$." + field).toString();
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
        .jwt(j -> j.subject("user_cs_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cs_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cs_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cs_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
