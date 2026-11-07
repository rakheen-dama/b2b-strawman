package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * G89-F31 + 634C (Batch G): every recipient of a COMMENT_ADDED notification is an existing member
 * who can view the matter — whatever the source (task assignee, document uploader, prior commenter
 * or {@code @mention}).
 *
 * <ul>
 *   <li>A portal-authored comment stores the portal contact id in {@code author_member_id}, so the
 *       prior-commenter query returns that id. {@code notifications.recipient_member_id} has a
 *       foreign key to {@code members}; inserting a notification for the contact id fails the whole
 *       fan-out transaction, so no member — not even a mentioned one — is notified of any later
 *       comment on that matter.
 *   <li>Assignee, uploader and prior-commenter recipients were not filtered by matter access; only
 *       mentions were (C2). A member removed from the matter kept getting notifications whose title
 *       carries the task title or document name.
 * </ul>
 *
 * <p>Comments are posted through the real endpoints ({@code POST /api/matters/{id}/comments} and
 * {@code POST /portal/matters/{id}/comments}). Alice (owner) posts every staff comment; Dave
 * (member) stays on the matters; Mo (member) is added and then removed.
 */
class CommentNotificationRecipientsIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_comment_notif_recipients_test";
  private static final String ALICE = "user_cnr_alice";
  private static final String DAVE = "user_cnr_dave";
  private static final String MO = "user_cnr_mo";

  private static final Map<String, String> ROLES =
      Map.of(ALICE, "owner", DAVE, "member", MO, "member");

  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepository;

  private final AtomicInteger counter = new AtomicInteger();
  private String aliceId;
  private String daveId;
  private String moId;
  private UUID customerId;
  private String portalToken;

  @BeforeAll
  void provisionTenantMembersAndClient() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Notification Recipients Org", null);
    aliceId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, ALICE, "cnr_alice@test.com", "Alice Author", "owner");
    daveId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, DAVE, "cnr_dave@test.com", "Dave Insider", "member");
    moId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, MO, "cnr_mo@test.com", "Mo Departed", "member");

    String tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(aliceId))
        .run(
            () -> {
              customerId =
                  customerService
                      .createCustomer(
                          "Recipients Client",
                          "cnr-client@test.com",
                          null,
                          null,
                          null,
                          UUID.fromString(aliceId))
                      .getId();
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "cnr-contact@test.com",
                  "Thandi Client",
                  PortalContact.ContactRole.PRIMARY);
            });
    portalToken = portalJwtService.issueToken(customerId, ORG_ID);
  }

  /**
   * The G89-F31 reproduction: once the client has commented on the matter, a later staff comment
   * that mentions a member on the matter must still notify that member.
   */
  @Test
  void afterPortalComment_memberComment_stillNotifiesMentionedMember() throws Exception {
    String matterId = newMatterWithDave();
    linkToPortal(matterId);
    postPortalComment(matterId, "Please confirm the transfer date");

    String commentId =
        postComment(
            matterId, "PROJECT", matterId, "@Dave Insider please reply to the client", "SHARED");

    assertThat(isNotifiedOf(DAVE, commentId)).isTrue();
  }

  @Test
  void priorCommenterWithoutMatterAccess_notNotified() throws Exception {
    String matterId = newMatterWithDave();
    addToMatter(matterId, moId);
    String taskId =
        TestEntityHelper.createTask(
            mockMvc, aliceJwt(), matterId, "Recipients task " + counter.incrementAndGet());
    postCommentAs(TestJwtFactory.memberJwt(ORG_ID, MO), matterId, "TASK", taskId, "Mo was here");
    postCommentAs(TestJwtFactory.memberJwt(ORG_ID, DAVE), matterId, "TASK", taskId, "Dave too");
    removeFromMatter(matterId, moId);

    String commentId = postComment(matterId, "TASK", taskId, "Follow-up on the draft", "INTERNAL");

    assertThat(isNotifiedOf(MO, commentId)).isFalse();
    // The filter drops only the member without access; the other prior commenter still hears.
    assertThat(isNotifiedOf(DAVE, commentId)).isTrue();
  }

  @Test
  void assigneeWithoutMatterAccess_notNotified() throws Exception {
    String matterId = newMatterWithDave();
    addToMatter(matterId, moId);
    String taskId = createTaskAssignedTo(matterId, moId);
    removeFromMatter(matterId, moId);

    String commentId =
        postComment(matterId, "TASK", taskId, "Status of the transfer duty receipt?", "INTERNAL");

    assertThat(isNotifiedOf(MO, commentId)).isFalse();
  }

  @Test
  void assigneeOnTheMatter_isNotified() throws Exception {
    String matterId = newMatterWithDave();
    String taskId = createTaskAssignedTo(matterId, daveId);

    String commentId =
        postComment(matterId, "TASK", taskId, "Status of the transfer duty receipt?", "INTERNAL");

    assertThat(isNotifiedOf(DAVE, commentId)).isTrue();
  }

  @Test
  void uploaderWithoutMatterAccess_notNotified() throws Exception {
    String matterId = newMatterWithDave();
    addToMatter(matterId, moId);
    String documentId =
        uploadAndConfirm(
            TestJwtFactory.memberJwt(ORG_ID, MO),
            matterId,
            "recipients-" + counter.incrementAndGet() + ".pdf");
    removeFromMatter(matterId, moId);

    String commentId =
        postComment(matterId, "DOCUMENT", documentId, "Page 3 needs initials", "INTERNAL");

    assertThat(isNotifiedOf(MO, commentId)).isFalse();
  }

  // --- helpers ---

  private JwtRequestPostProcessor aliceJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, ALICE);
  }

  /** A fresh matter (Alice is lead) with Dave on it, so each test's rows are its own. */
  private String newMatterWithDave() throws Exception {
    String matterId =
        TestEntityHelper.createProject(
            mockMvc, aliceJwt(), "Recipients matter " + counter.incrementAndGet(), "G89-F31");
    addToMatter(matterId, daveId);
    return matterId;
  }

  private void addToMatter(String matterId, String memberId) throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + matterId + "/members")
                .with(aliceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private void removeFromMatter(String matterId, String memberId) throws Exception {
    mockMvc
        .perform(delete("/api/matters/" + matterId + "/members/" + memberId).with(aliceJwt()))
        .andExpect(status().isNoContent());
  }

  /** Links the matter to the client in the portal read model so the contact may comment on it. */
  private void linkToPortal(String matterId) {
    readModelRepository.upsertPortalProject(
        UUID.fromString(matterId),
        customerId,
        ORG_ID,
        "Recipients matter",
        "ACTIVE",
        "G89-F31",
        Instant.now());
  }

  private void postPortalComment(String matterId, String content) throws Exception {
    mockMvc
        .perform(
            post("/portal/matters/{matterId}/comments", matterId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"%s\"}".formatted(content)))
        .andExpect(status().isCreated());
  }

  private String createTaskAssignedTo(String matterId, String assigneeId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/tasks")
                    .with(aliceJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Assigned task %d", "priority": "MEDIUM", "assigneeId": "%s"}
                        """
                            .formatted(counter.incrementAndGet(), assigneeId)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  /** Posts a comment as Alice; returns the comment id. */
  private String postComment(
      String matterId, String entityType, String entityId, String body, String visibility)
      throws Exception {
    return postCommentAs(aliceJwt(), matterId, entityType, entityId, body, visibility);
  }

  private String postCommentAs(
      JwtRequestPostProcessor jwt, String matterId, String entityType, String entityId, String body)
      throws Exception {
    return postCommentAs(jwt, matterId, entityType, entityId, body, "INTERNAL");
  }

  private String postCommentAs(
      JwtRequestPostProcessor jwt,
      String matterId,
      String entityType,
      String entityId,
      String body,
      String visibility)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/comments")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityType": "%s", "entityId": "%s", "body": "%s", "visibility": "%s"}
                        """
                            .formatted(entityType, entityId, body, visibility)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  /**
   * True if {@code subject}'s own feed holds a COMMENT_ADDED row about {@code commentId} — scoped
   * to the comment, not the target, so earlier comments on the same task cannot satisfy it.
   */
  private boolean isNotifiedOf(String subject, String commentId) throws Exception {
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
            "$.content[?(@.type == 'COMMENT_ADDED' && @.referenceCommentId == '%s')].id"
                .formatted(commentId));
    return !matching.isEmpty();
  }

  private String uploadAndConfirm(JwtRequestPostProcessor jwt, String matterId, String fileName)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/documents/upload-init")
                    .with(jwt)
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
        .perform(post("/api/documents/" + documentId + "/confirm").with(jwt))
        .andExpect(status().isOk());
    return documentId;
  }
}
