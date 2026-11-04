package io.b2mash.b2b.b2bstrawman.comment;

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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * G88-F36: an {@code @Name} mention in a comment body notifies that member, driven through the real
 * comment endpoint ({@code POST /api/matters/{id}/comments} → {@code CommentCreatedEvent} → {@code
 * NotificationService.handleCommentCreated}) and observed on the mentioned member's own {@code GET
 * /api/notifications}.
 *
 * <p>Every test comments on a fresh, un-assigned task that nobody else has commented on, so the
 * mention is the only route by which the member can be notified (task assignee and prior-commenter
 * fan-out would otherwise mask the parser). The comment author is always Alice.
 */
class CommentMentionNotificationIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_comment_mention_test";
  private static final String ALICE = "user_cm_alice";
  private static final String BOB = "user_cm_bob";
  private static final String BOB_SECOND = "user_cm_bob_second";
  private static final String CAROL = "user_cm_carol";
  private static final String ZOE = "user_cm_zoe";

  private final AtomicInteger taskCounter = new AtomicInteger();
  private String projectId;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Mention Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ALICE, "cm_alice@test.com", "Alice Author", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, BOB, "cm_bob@test.com", "Bob Mention", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, BOB_SECOND, "cm_bob_second@test.com", "Bob Second", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, CAROL, "cm_carol@test.com", "Carol Mention", "member");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ZOE, "cm_zoe@test.com", "Zoë Khumalo", "member");

    projectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, ALICE),
            "Mention Test Matter",
            "G88-F36 mention parsing");
  }

  /** The QA reproduction: a first-name mention followed by more words. */
  @Test
  void firstNameMention_followedByWords_notifiesThatMember() throws Exception {
    String taskId = commentOnFreshTask("@Bob please review the draft");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
  }

  @Test
  void fullNameMention_followedByWords_notifiesOnlyThatMember() throws Exception {
    String taskId = commentOnFreshTask("@Bob Mention please review the draft");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
    assertThat(hasCommentNotification(BOB_SECOND, taskId)).isFalse();
  }

  @Test
  void mentionIsCaseInsensitive() throws Exception {
    String taskId = commentOnFreshTask("Over to you, @bOB");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
  }

  @Test
  void multipleMentionsInOneComment_notifyEveryMentionedMember() throws Exception {
    String taskId = commentOnFreshTask("@Bob and @Carol please review");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
    assertThat(hasCommentNotification(CAROL, taskId)).isTrue();
  }

  @Test
  void mentionFollowedByNewline_notifiesThatMember() throws Exception {
    String taskId = commentOnFreshTask("@Bob\nthanks for the draft");

    assertThat(hasCommentNotification(BOB, taskId)).isTrue();
  }

  @Test
  void atSignInsideEmailAddress_isNotAMention() throws Exception {
    String taskId = commentOnFreshTask("Copied info@bob.co.za on the draft");

    assertThat(hasCommentNotification(BOB, taskId)).isFalse();
    assertThat(hasCommentNotification(BOB_SECOND, taskId)).isFalse();
  }

  @Test
  void nonAsciiFirstName_isMatched() throws Exception {
    String taskId = commentOnFreshTask("@Zoë please look at clause 4");

    assertThat(hasCommentNotification(ZOE, taskId)).isTrue();
  }

  @Test
  void authorMentioningThemselves_isNotNotified() throws Exception {
    String taskId = commentOnFreshTask("Note to self @Alice Author");

    assertThat(hasCommentNotification(ALICE, taskId)).isFalse();
  }

  // --- helpers ---

  /** Creates an un-assigned task as Alice and posts {@code body} on it as Alice. */
  private String commentOnFreshTask(String body) throws Exception {
    var aliceJwt = TestJwtFactory.ownerJwt(ORG_ID, ALICE);
    String taskId =
        TestEntityHelper.createTask(
            mockMvc, aliceJwt, projectId, "Mention task " + taskCounter.incrementAndGet());

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/comments")
                .with(aliceJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType": "TASK", "entityId": "%s", "body": "%s", "visibility": "INTERNAL"}
                    """
                        .formatted(taskId, body.replace("\n", "\\n"))))
        .andExpect(status().isCreated());
    return taskId;
  }

  /** True if {@code subject}'s own notification feed holds a COMMENT_ADDED row for the task. */
  private boolean hasCommentNotification(String subject, String taskId) throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/notifications")
                    .param("size", "100")
                    .with(
                        TestJwtFactory.jwtAs(
                            ORG_ID, subject, ALICE.equals(subject) ? "owner" : "member")))
            .andExpect(status().isOk())
            .andReturn();
    List<String> matching =
        JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.content[?(@.type == 'COMMENT_ADDED' && @.referenceEntityId == '%s')].id"
                .formatted(taskId));
    return !matching.isEmpty();
  }
}
