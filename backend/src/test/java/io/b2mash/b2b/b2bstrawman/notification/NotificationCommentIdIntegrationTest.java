package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * G88-M6 (Phase 89 634C): a comment notification stores the comment's own id ({@code
 * notifications.reference_comment_id}, tenant V11) and returns it as {@code referenceCommentId} on
 * {@code GET /api/notifications}, so the bell can open the comment itself rather than the matter's
 * task list. Driven through the real comment endpoint ({@code POST /api/matters/{id}/comments} →
 * {@code CommentCreatedEvent} → {@code NotificationService.handleCommentCreated}) for the three
 * comment targets (TASK, DOCUMENT, PROJECT); a non-comment notification carries {@code null}.
 *
 * <p>Rows are found by id-scoped JsonPath filters (never global counts — count-bleed rule).
 */
class NotificationCommentIdIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_notification_comment_id_test";
  private static final String ALICE = "user_ncid_alice";
  private static final String BOB = "user_ncid_bob";

  private final AtomicInteger counter = new AtomicInteger();
  private String projectId;
  private String bobMemberId;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Notification Comment Id Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ALICE, "ncid_alice@test.com", "Alice Author", "owner");
    bobMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, BOB, "ncid_bob@test.com", "Bob Reader", "member");

    projectId =
        TestEntityHelper.createProject(
            mockMvc, aliceJwt(), "Comment Id Test Matter", "G88-M6 comment deep link");
    // Bob is on the matter, so he can be mentioned (C2) and assigned.
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(aliceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(bobMemberId)))
        .andExpect(status().isCreated());
  }

  @Test
  void taskComment_notificationCarriesTheCommentId() throws Exception {
    String taskId =
        TestEntityHelper.createTask(
            mockMvc, aliceJwt(), projectId, "Comment id task " + counter.incrementAndGet());

    String commentId = postComment("TASK", taskId, "@Bob please review the draft", "INTERNAL");

    Map<String, Object> row = bobNotificationFor(commentId);
    assertThat(row.get("type")).isEqualTo("COMMENT_ADDED");
    assertThat(row.get("referenceEntityType")).isEqualTo("TASK");
    assertThat(row.get("referenceEntityId")).isEqualTo(taskId);
    assertThat(row.get("referenceMatterId")).isEqualTo(projectId);
    assertThat(row.get("referenceCommentId")).isEqualTo(commentId);
  }

  @Test
  void documentComment_notificationCarriesTheCommentId() throws Exception {
    String documentId = uploadAndConfirm("comment-id-" + counter.incrementAndGet() + ".pdf");

    String commentId = postComment("DOCUMENT", documentId, "@Bob see page 2", "INTERNAL");

    Map<String, Object> row = bobNotificationFor(commentId);
    assertThat(row.get("type")).isEqualTo("COMMENT_ADDED");
    assertThat(row.get("referenceEntityType")).isEqualTo("DOCUMENT");
    assertThat(row.get("referenceEntityId")).isEqualTo(documentId);
    assertThat(row.get("referenceMatterId")).isEqualTo(projectId);
    assertThat(row.get("referenceCommentId")).isEqualTo(commentId);
  }

  @Test
  void matterComment_notificationCarriesTheCommentId() throws Exception {
    String commentId = postComment("PROJECT", projectId, "@Bob client update is in", "SHARED");

    Map<String, Object> row = bobNotificationFor(commentId);
    assertThat(row.get("type")).isEqualTo("COMMENT_ADDED");
    assertThat(row.get("referenceEntityType")).isEqualTo("PROJECT");
    assertThat(row.get("referenceEntityId")).isEqualTo(projectId);
    assertThat(row.get("referenceMatterId")).isEqualTo(projectId);
    assertThat(row.get("referenceCommentId")).isEqualTo(commentId);
  }

  @Test
  void nonCommentNotification_hasNullCommentId() throws Exception {
    String title = "Assigned task " + counter.incrementAndGet();
    String taskId = TestEntityHelper.createTask(mockMvc, aliceJwt(), projectId, title);
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(aliceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "%s", "priority": "MEDIUM", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(title, bobMemberId)))
        .andExpect(status().isOk());

    String json = bobFeed();
    List<Map<String, Object>> rows =
        JsonPath.read(
            json,
            "$.content[?(@.type == 'TASK_ASSIGNED' && @.referenceEntityId == '%s')]"
                .formatted(taskId));
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst()).containsKey("referenceCommentId");
    assertThat(rows.getFirst().get("referenceCommentId")).isNull();
  }

  // --- helpers ---

  private JwtRequestPostProcessor aliceJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, ALICE);
  }

  /** Posts a comment as Alice through the real endpoint and returns the new comment's id. */
  private String postComment(String entityType, String entityId, String body, String visibility)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + projectId + "/comments")
                    .with(aliceJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityType": "%s", "entityId": "%s", "body": "%s", "visibility": "%s"}
                        """
                            .formatted(entityType, entityId, body, visibility)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String bobFeed() throws Exception {
    return mockMvc
        .perform(
            get("/api/notifications")
                .param("size", "100")
                .with(TestJwtFactory.memberJwt(ORG_ID, BOB)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Bob's single notification row whose {@code referenceCommentId} is {@code commentId}. */
  private Map<String, Object> bobNotificationFor(String commentId) throws Exception {
    List<Map<String, Object>> rows =
        JsonPath.read(bobFeed(), "$.content[?(@.referenceCommentId == '%s')]".formatted(commentId));
    assertThat(rows).as("Bob's notification for comment %s", commentId).hasSize(1);
    return rows.getFirst();
  }

  private String uploadAndConfirm(String fileName) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + projectId + "/documents/upload-init")
                    .with(aliceJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "%s", "contentType": "application/pdf", "size": 1024}
                        """
                            .formatted(fileName)))
            .andExpect(status().isCreated())
            .andReturn();
    String documentId = JsonPath.read(result.getResponse().getContentAsString(), "$.documentId");
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(aliceJwt()))
        .andExpect(status().isOk());
    return documentId;
  }
}
