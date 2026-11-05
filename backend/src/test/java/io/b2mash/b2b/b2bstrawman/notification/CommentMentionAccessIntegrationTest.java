package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * C2 (Phase 89 634C, ADR-346, architecture §14.3.22 / §14.6.3): an {@code @Name} mention only
 * notifies members who can view the matter. The notification title carries the task title or
 * document file name, so a member who is not on the matter must never receive it, even when named;
 * owners and admins can view every matter and are always mentionable. Sits beside #1720's {@code
 * comment/CommentMentionNotificationIntegrationTest} (which covers the parser itself).
 *
 * <p>Every comment is posted by Alice (owner) through the real endpoint ({@code POST
 * /api/matters/{id}/comments}) on a fresh, un-assigned target nobody else has commented on, so the
 * mention is the only route by which a member could be notified. Carol (member) and Bob (admin) are
 * NOT on the matter; Dave (member) and Carol Second (member) are.
 */
class CommentMentionAccessIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_comment_mention_access_test";
  private static final String ALICE = "user_cma_alice";
  private static final String BOB = "user_cma_bob";
  private static final String CAROL = "user_cma_carol";
  private static final String CAROL_SECOND = "user_cma_carol_second";
  private static final String DAVE = "user_cma_dave";
  private static final String OLIVE = "user_cma_olive";

  private static final Map<String, String> ROLES =
      Map.of(
          ALICE, "owner",
          BOB, "admin",
          CAROL, "member",
          CAROL_SECOND, "member",
          DAVE, "member",
          OLIVE, "owner");

  private final AtomicInteger counter = new AtomicInteger();
  private String projectId;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Mention Access Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ALICE, "cma_alice@test.com", "Alice Author", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, BOB, "cma_bob@test.com", "Bob Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, CAROL, "cma_carol@test.com", "Carol Outsider", "member");
    String carolSecondId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, CAROL_SECOND, "cma_carol2@test.com", "Carol Insider", "member");
    String daveId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, DAVE, "cma_dave@test.com", "Dave Insider", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OLIVE, "cma_olive@test.com", "Olive Partner", "owner");

    projectId =
        TestEntityHelper.createProject(
            mockMvc, aliceJwt(), "Restricted Mention Matter", "C2 mention access filter");
    addToMatter(daveId);
    addToMatter(carolSecondId);
  }

  /** The C2 reproduction: a member who is not on the matter is named and must not be notified. */
  @Test
  void memberNotOnTheMatter_isNotNotified() throws Exception {
    String taskId = commentOnFreshTask("@Carol Outsider please review the draft");

    assertThat(hasCommentNotification(CAROL, taskId)).isFalse();
  }

  @Test
  void adminNotOnTheMatter_isNotified() throws Exception {
    String taskId = commentOnFreshTask("@Bob please review the draft");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
  }

  @Test
  void ownerNotOnTheMatter_isNotified() throws Exception {
    String taskId = commentOnFreshTask("@Olive please sign off");

    assertThat(hasCommentNotification(OLIVE, taskId)).isTrue();
  }

  @Test
  void memberOnTheMatter_isNotified() throws Exception {
    String taskId = commentOnFreshTask("@Dave please review the draft");

    assertThat(hasCommentNotification(DAVE, taskId)).isTrue();
  }

  @Test
  void mixedMentions_notifyOnlyTheMembersWhoCanViewTheMatter() throws Exception {
    String taskId = commentOnFreshTask("@Carol Outsider and @Dave please review");

    assertThat(hasCommentNotification(DAVE, taskId)).isTrue();
    assertThat(hasCommentNotification(CAROL, taskId)).isFalse();
  }

  /**
   * Two members share the first name Carol; only the one on the matter is a candidate, so the
   * first-name fallback resolves to her alone.
   */
  @Test
  void sharedFirstName_onlyTheMemberOnTheMatterIsNotified() throws Exception {
    String taskId = commentOnFreshTask("@Carol please review the draft");

    assertThat(hasCommentNotification(CAROL_SECOND, taskId)).isTrue();
    assertThat(hasCommentNotification(CAROL, taskId)).isFalse();
  }

  @Test
  void documentComment_memberNotOnTheMatter_isNotNotified() throws Exception {
    String documentId = uploadAndConfirm("mention-access-" + counter.incrementAndGet() + ".pdf");
    postComment("DOCUMENT", documentId, "@Carol Outsider and @Dave see page 2", "INTERNAL");

    assertThat(hasCommentNotification(CAROL, documentId)).isFalse();
    assertThat(hasCommentNotification(DAVE, documentId)).isTrue();
  }

  @Test
  void matterComment_memberNotOnTheMatter_isNotNotified() throws Exception {
    // A dedicated matter, so the PROJECT-target rows (referenceEntityId = matter id) are this
    // test's alone.
    String matterId =
        TestEntityHelper.createProject(
            mockMvc, aliceJwt(), "Matter comment mention " + counter.incrementAndGet(), "C2");
    postCommentOn(matterId, "PROJECT", matterId, "@Carol Outsider and @Bob update", "SHARED");

    assertThat(hasCommentNotification(CAROL, matterId)).isFalse();
    assertThat(hasCommentNotification(BOB, matterId)).isTrue();
  }

  // --- helpers ---

  private JwtRequestPostProcessor aliceJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, ALICE);
  }

  private void addToMatter(String memberId) throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(aliceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberId)))
        .andExpect(status().isCreated());
  }

  /** Creates an un-assigned task as Alice, posts {@code body} on it as Alice; returns the task. */
  private String commentOnFreshTask(String body) throws Exception {
    String taskId =
        TestEntityHelper.createTask(
            mockMvc, aliceJwt(), projectId, "Mention access task " + counter.incrementAndGet());
    postComment("TASK", taskId, body, "INTERNAL");
    return taskId;
  }

  private void postComment(String entityType, String entityId, String body, String visibility)
      throws Exception {
    postCommentOn(projectId, entityType, entityId, body, visibility);
  }

  private void postCommentOn(
      String matterId, String entityType, String entityId, String body, String visibility)
      throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + matterId + "/comments")
                .with(aliceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType": "%s", "entityId": "%s", "body": "%s", "visibility": "%s"}
                    """
                        .formatted(entityType, entityId, body, visibility)))
        .andExpect(status().isCreated());
  }

  /** True if {@code subject}'s own feed holds a COMMENT_ADDED row for {@code entityId}. */
  private boolean hasCommentNotification(String subject, String entityId) throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/notifications")
                    .param("size", "100")
                    .with(TestJwtFactory.jwtAs(ORG_ID, subject, ROLES.get(subject))))
            .andExpect(status().isOk())
            .andReturn();
    List<String> matching =
        JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.content[?(@.type == 'COMMENT_ADDED' && @.referenceEntityId == '%s')].id"
                .formatted(entityId));
    return !matching.isEmpty();
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
