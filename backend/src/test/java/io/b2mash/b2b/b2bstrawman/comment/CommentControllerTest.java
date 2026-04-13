package io.b2mash.b2b.b2bstrawman.comment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

class CommentControllerTest extends AbstractIntegrationTest {
  private static final String ORG_ID = "org_comment_ctrl_test";

  private String projectId;
  private String taskId;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String memberIdMember2;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Ctrl Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_cc_owner", "cc_owner@test.com", "CC Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_cc_admin", "cc_admin@test.com", "CC Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_cc_member", "cc_member@test.com", "CC Member", "member");
    memberIdMember2 =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_cc_member2", "cc_member2@test.com", "CC Member2", "member");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Comment Ctrl Test Project", "description": "For comment ctrl tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add all members to the project
    for (String mid : List.of(memberIdAdmin, memberIdMember, memberIdMember2)) {
      mockMvc
          .perform(
              post("/api/projects/" + projectId + "/members")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"memberId\": \"%s\"}".formatted(mid)))
          .andExpect(status().isCreated());
    }

    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Comment Ctrl Test Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = TestEntityHelper.extractIdFromLocation(taskResult);
  }

  @Test
  void postCreatesCommentAndReturns201WithCorrectShape() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"))
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
    createComment(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"), "First comment");
    createComment(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"), "Second comment");
    createComment(TestJwtFactory.adminJwt(ORG_ID, "user_cc_admin"), "Third comment");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
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
      createComment(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"), "Pagination comment " + i);
    }

    // Request page 0 with size 2
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
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
    var commentId =
        createComment(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"), "Body to update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"))
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
    var commentId =
        createComment(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"), "Protected comment");

    // Try to update as member2 (not author, not admin/owner)
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/comments/" + commentId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member2"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "Attempted edit"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteByAuthorReturns204() throws Exception {
    var commentId =
        createComment(
            TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"), "Comment to delete by author");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/comments/" + commentId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteByNonAuthorNonAdminReturns403() throws Exception {
    var commentId =
        createComment(
            TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"),
            "Comment non-author tries to delete");

    // member2 is not the author and not admin/owner
    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/comments/" + commentId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member2")))
        .andExpect(status().isForbidden());
  }

  @Test
  void postCommentOnArchivedProjectReturns400() throws Exception {
    // Create a separate project for archiving (to avoid affecting other tests)
    var archiveProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Archive Comment Test Project", "description": "For archive guard test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var archiveProjectId = TestEntityHelper.extractIdFromLocation(archiveProjectResult);

    // Create a task on the project before archiving
    var archiveTaskResult =
        mockMvc
            .perform(
                post("/api/projects/" + archiveProjectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Archive Test Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var archiveTaskId = TestEntityHelper.extractIdFromLocation(archiveTaskResult);

    // Archive the project
    mockMvc
        .perform(
            patch("/api/projects/" + archiveProjectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner")))
        .andExpect(status().isOk());

    // Attempt to create a comment on the archived project — should be rejected
    mockMvc
        .perform(
            post("/api/projects/" + archiveProjectId + "/comments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "This should be rejected"
                    }
                    """
                        .formatted(archiveTaskId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putCommentOnArchivedProjectReturns400() throws Exception {
    // Create a comment on a non-archived project first
    var commentId =
        createComment(
            TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"), "Comment to test archive update");

    // Create a separate project, add a comment, then archive it
    var archiveProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Archive Update Test Project", "description": "For archive update guard test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var archiveProjectId = TestEntityHelper.extractIdFromLocation(archiveProjectResult);

    // Create a task on the project
    var archiveTaskResult =
        mockMvc
            .perform(
                post("/api/projects/" + archiveProjectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Archive Update Test Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var archiveTaskId = TestEntityHelper.extractIdFromLocation(archiveTaskResult);

    // Create a comment before archiving
    var archiveCommentResult =
        mockMvc
            .perform(
                post("/api/projects/" + archiveProjectId + "/comments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "entityId": "%s",
                          "body": "Pre-archive comment"
                        }
                        """
                            .formatted(archiveTaskId)))
            .andExpect(status().isCreated())
            .andReturn();
    var archiveCommentId =
        JsonPath.read(archiveCommentResult.getResponse().getContentAsString(), "$.id").toString();

    // Archive the project
    mockMvc
        .perform(
            patch("/api/projects/" + archiveProjectId + "/archive")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner")))
        .andExpect(status().isOk());

    // Attempt to update the comment on the archived project — should be rejected
    mockMvc
        .perform(
            put("/api/projects/" + archiveProjectId + "/comments/" + archiveCommentId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"body": "This update should be rejected"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void memberCanPostProjectLevelSharedComment() throws Exception {
    // Member-role users must be able to post PROJECT-level (Client Comments) which are SHARED
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "entityId": "%s",
                      "body": "Member reply to client thread",
                      "visibility": "SHARED"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorMemberId").value(memberIdMember))
        .andExpect(jsonPath("$.visibility").value("SHARED"))
        .andExpect(jsonPath("$.entityType").value("PROJECT"));
  }

  @Test
  void memberCannotPostSharedCommentOnTask() throws Exception {
    // SHARED visibility on TASK comments still requires canEdit
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "TASK",
                      "entityId": "%s",
                      "body": "Member tries shared on task",
                      "visibility": "SHARED"
                    }
                    """
                        .formatted(taskId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").isNotEmpty());
  }

  @Test
  void memberProjectCommentVisibleAfterReload() throws Exception {
    // Post as member
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "entityId": "%s",
                      "body": "Member comment persists",
                      "visibility": "SHARED"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());

    // List as different user — member's comment must be present
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/comments")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cc_admin"))
                .param("entityType", "PROJECT"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.body == 'Member comment persists')].authorMemberId")
                .value(org.hamcrest.Matchers.hasItem(memberIdMember)));
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
}
