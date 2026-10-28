package io.b2mash.b2b.b2bstrawman.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Comment attachments (Phase 88 U24, A13, closes G-B15): documents uploaded through the presign
 * flow are linked to a comment, inherit its visibility, are stamped {@code source = COMMENT} and
 * stay first-class rows in the matter's Documents list. Errors are 422 "Attachment not allowed".
 */
class CommentAttachmentIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_comment_attach_test";
  private static final String NOT_ALLOWED = "Attachment not allowed";

  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentService documentService;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private String tenantSchema;
  private String matterA;
  private String matterB;
  private String taskA;
  private String portalToken;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Comment Attach Test Org", null);
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ca_owner", "ca_owner@test.com", "CA Owner", "owner");
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ca_member", "ca_member@test.com", "CA Member", "member");
    String member2Id =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ca_member2", "ca_member2@test.com", "CA Member2", "member");

    matterA = TestEntityHelper.createProject(mockMvc, owner(), "Attach Matter A", "matter A");
    matterB = TestEntityHelper.createProject(mockMvc, owner(), "Attach Matter B", "matter B");
    for (String mid : List.of(memberId, member2Id)) {
      mockMvc
          .perform(
              post("/api/matters/" + matterA + "/members")
                  .with(owner())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"memberId\": \"%s\"}".formatted(mid)))
          .andExpect(status().isCreated());
    }
    taskA = TestEntityHelper.createTask(mockMvc, owner(), matterA, "Attach Task A");

    // A client linked to matter A with a portal contact, for the portal list.
    String customerId =
        TestEntityHelper.createCustomer(mockMvc, owner(), "Attach Client", "attach_c@test.com");
    mockMvc
        .perform(
            post("/api/clients/" + customerId + "/transition")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.transitionToActive(mockMvc, customerId, owner());
    mockMvc
        .perform(post("/api/clients/" + customerId + "/matters/" + matterA).with(owner()))
        .andExpect(status().isCreated());
    UUID customerUuid = UUID.fromString(customerId);
    RequestScopes.runForTenant(
        tenantSchema,
        ORG_ID,
        () ->
            portalContactService.createContact(
                ORG_ID,
                customerUuid,
                "attach-portal@test.com",
                "Attach Contact",
                PortalContact.ContactRole.PRIMARY));
    portalToken = portalJwtService.issueToken(customerUuid, ORG_ID);
  }

  // --- link + visibility inheritance ---

  @Test
  void createLinksAttachmentsInRequestOrderAndListsThem() throws Exception {
    String first = uploadAndConfirm(owner(), matterA, "masters-letter.pdf");
    String second = uploadAndConfirm(owner(), matterA, "annexure.pdf");

    String commentId =
        id(
            postComment(owner(), "TASK", taskA, "INTERNAL", second, first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments", hasSize(2)))
                .andExpect(jsonPath("$.attachments[0].documentId").value(second))
                .andExpect(jsonPath("$.attachments[0].fileName").value("annexure.pdf"))
                .andExpect(jsonPath("$.attachments[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.attachments[0].size").value(1024))
                .andExpect(jsonPath("$.attachments[1].documentId").value(first))
                .andExpect(jsonPath("$.attachments[1].fileName").value("masters-letter.pdf")));

    mockMvc
        .perform(
            get("/api/matters/" + matterA + "/comments")
                .with(owner())
                .param("entityType", "TASK")
                .param("entityId", taskA))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '%s')].attachments[0].documentId", commentId).value(second))
        .andExpect(
            jsonPath("$[?(@.id == '%s')].attachments[1].documentId", commentId).value(first));

    // Documents stay first-class rows on the matter, now labelled source COMMENT.
    mockMvc
        .perform(get("/api/matters/" + matterA + "/documents").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')].source", first).value("COMMENT"))
        .andExpect(jsonPath("$[?(@.id == '%s')].visibility", first).value("INTERNAL"))
        .andExpect(jsonPath("$[?(@.id == '%s')].source", second).value("COMMENT"));
  }

  @Test
  void commentWithoutAttachmentsReturnsEmptyArray() throws Exception {
    postComment(owner(), "TASK", taskA, "INTERNAL")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments").isArray())
        .andExpect(jsonPath("$.attachments", hasSize(0)));
  }

  @Test
  void sharedCommentSharesItsAttachmentAndStampsSourceComment() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "shared-letter.pdf");

    postComment(owner(), "TASK", taskA, "SHARED", doc).andExpect(status().isCreated());

    assertDocument(doc, Document.Visibility.SHARED, Document.Source.COMMENT);
  }

  @Test
  void internalCommentKeepsItsAttachmentInternal() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "internal-note.pdf");

    postComment(owner(), "TASK", taskA, "INTERNAL", doc).andExpect(status().isCreated());

    assertDocument(doc, Document.Visibility.INTERNAL, Document.Source.COMMENT);
  }

  @Test
  void nonManualSourceKeepsItsProvenance() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "generated.pdf");
    RequestScopes.runForTenant(
        tenantSchema,
        ORG_ID,
        () -> {
          var d = documentRepository.findById(UUID.fromString(doc)).orElseThrow();
          d.setSource(Document.Source.TEMPLATE_GENERATED);
          documentRepository.save(d);
        });

    postComment(owner(), "TASK", taskA, "SHARED", doc).andExpect(status().isCreated());

    assertDocument(doc, Document.Visibility.SHARED, Document.Source.TEMPLATE_GENERATED);
  }

  @Test
  void commentVisibilityChangeReappliesToAttachmentsButNeverTouchesPortalDocuments()
      throws Exception {
    String fresh = uploadAndConfirm(owner(), matterA, "fresh.pdf");
    String portal = uploadAndConfirm(owner(), matterA, "closure-letter.pdf");
    RequestScopes.runForTenant(
        tenantSchema, ORG_ID, () -> documentService.markSystemAutoShared(UUID.fromString(portal)));

    String commentId =
        id(
            postComment(owner(), "TASK", taskA, "SHARED", fresh, portal)
                .andExpect(status().isCreated()));
    assertDocument(fresh, Document.Visibility.SHARED, Document.Source.COMMENT);
    assertDocument(portal, Document.Visibility.PORTAL, Document.Source.COMMENT);

    putComment(owner(), commentId, "now internal", "INTERNAL", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments", hasSize(2)));
    assertDocument(fresh, Document.Visibility.INTERNAL, Document.Source.COMMENT);
    assertDocument(portal, Document.Visibility.PORTAL, Document.Source.COMMENT);

    putComment(owner(), commentId, "shared again", "SHARED", null).andExpect(status().isOk());
    assertDocument(fresh, Document.Visibility.SHARED, Document.Source.COMMENT);
    assertDocument(portal, Document.Visibility.PORTAL, Document.Source.COMMENT);
  }

  // --- validation (422 "Attachment not allowed") ---

  @Test
  void documentOnAnotherMatterIsRejected() throws Exception {
    String other = uploadAndConfirm(owner(), matterB, "other-matter.pdf");

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", other),
        "Document " + other + " is not a document on this matter",
        other);
  }

  @Test
  void nonexistentDocumentGetsTheSameDetailAsCrossMatter() throws Exception {
    String missing = UUID.randomUUID().toString();

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", missing),
        "Document " + missing + " is not a document on this matter",
        missing);
  }

  @Test
  void sixthDistinctAttachmentIsRejected() throws Exception {
    List<String> docs = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      docs.add(uploadAndConfirm(owner(), matterA, "bulk-" + i + ".pdf"));
    }

    String commentId =
        id(
            postComment(
                    owner(), "TASK", taskA, "INTERNAL", docs.subList(0, 5).toArray(String[]::new))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments", hasSize(5))));

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", docs.toArray(String[]::new)),
        "A comment can have at most 5 attachments (got 6)",
        null);
    expectNotAllowed(
        putComment(owner(), commentId, "six", null, docs),
        "A comment can have at most 5 attachments (got 6)",
        null);
  }

  @Test
  void duplicateIdsAreDeDuplicatedKeepingFirstPosition() throws Exception {
    String a = uploadAndConfirm(owner(), matterA, "dup-a.pdf");
    String b = uploadAndConfirm(owner(), matterA, "dup-b.pdf");

    postComment(owner(), "TASK", taskA, "INTERNAL", a, b, a)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments", hasSize(2)))
        .andExpect(jsonPath("$.attachments[0].documentId").value(a))
        .andExpect(jsonPath("$.attachments[1].documentId").value(b));
  }

  @Test
  void documentThatHasNotFinishedUploadingIsRejected() throws Exception {
    String pending = uploadInit(owner(), matterA, "pending.pdf");

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", pending),
        "Document " + pending + " has not finished uploading",
        pending);
  }

  @Test
  void documentAlreadyAttachedToAnotherCommentIsRejected() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "once-only.pdf");
    postComment(owner(), "TASK", taskA, "INTERNAL", doc).andExpect(status().isCreated());
    String second = id(postComment(owner(), "TASK", taskA, "INTERNAL"));

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", doc),
        "Document " + doc + " is already attached to another comment",
        doc);
    expectNotAllowed(
        putComment(owner(), second, "steal", null, List.of(doc)),
        "Document " + doc + " is already attached to another comment",
        doc);
  }

  @Test
  void clientVisibleDocumentCannotBeAttachedToAnInternalComment() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "already-shared.pdf");
    mockMvc
        .perform(
            patch("/api/documents/" + doc + "/visibility")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"SHARED\"}"))
        .andExpect(status().isOk());

    expectNotAllowed(
        postComment(owner(), "TASK", taskA, "INTERNAL", doc),
        "Document "
            + doc
            + " is shared with the client; attach it to a shared comment or unshare it first",
        doc);
    assertDocument(doc, Document.Visibility.SHARED, Document.Source.MANUAL);
  }

  @Test
  void uploaderMayAttachOwnUploadButNotSomeoneElses() throws Exception {
    String own = uploadAndConfirm(member2(), matterA, "member2-own.pdf");
    String ownersDoc = uploadAndConfirm(owner(), matterA, "owners.pdf");

    String commentId =
        id(
            postComment(member2(), "TASK", taskA, "INTERNAL", own)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].documentId").value(own)));

    expectNotAllowed(
        postComment(member2(), "TASK", taskA, "INTERNAL", ownersDoc),
        "Only the uploader or a matter lead can attach document " + ownersDoc,
        ownersDoc);
    expectNotAllowed(
        putComment(member2(), commentId, "add owner's", null, List.of(own, ownersDoc)),
        "Only the uploader or a matter lead can attach document " + ownersDoc,
        ownersDoc);
    assertDocument(ownersDoc, Document.Visibility.INTERNAL, Document.Source.MANUAL);
  }

  @Test
  void rejectedAttachmentDoesNotPersistTheComment() throws Exception {
    String body = "should not persist " + UUID.randomUUID();
    String missing = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/matters/" + matterA + "/comments")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType": "TASK", "entityId": "%s", "body": "%s",
                     "visibility": "INTERNAL", "attachmentDocumentIds": ["%s"]}
                    """
                        .formatted(taskA, body, missing)))
        .andExpect(status().isUnprocessableContent());

    mockMvc
        .perform(
            get("/api/matters/" + matterA + "/comments")
                .with(owner())
                .param("entityType", "TASK")
                .param("entityId", taskA)
                .param("size", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.body == '%s')]", body).isEmpty());
  }

  // --- update semantics ---

  @Test
  void updateNullKeepsTheSetListReplacesAndEmptyClears() throws Exception {
    String a = uploadAndConfirm(owner(), matterA, "replace-a.pdf");
    String b = uploadAndConfirm(owner(), matterA, "replace-b.pdf");
    String commentId =
        id(postComment(owner(), "TASK", taskA, "SHARED", a).andExpect(status().isCreated()));

    putComment(owner(), commentId, "body only", null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments", hasSize(1)))
        .andExpect(jsonPath("$.attachments[0].documentId").value(a));

    putComment(owner(), commentId, "replace", null, List.of(b))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments", hasSize(1)))
        .andExpect(jsonPath("$.attachments[0].documentId").value(b));
    // The removed document is left as it was.
    assertDocument(a, Document.Visibility.SHARED, Document.Source.COMMENT);
    assertDocument(b, Document.Visibility.SHARED, Document.Source.COMMENT);
    assertThat(linkCount(commentId)).isEqualTo(1);

    putComment(owner(), commentId, "clear", null, List.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments", hasSize(0)));
    assertThat(linkCount(commentId)).isZero();
    assertDocument(b, Document.Visibility.SHARED, Document.Source.COMMENT);
  }

  @Test
  void updateReordersKeptAttachments() throws Exception {
    String a = uploadAndConfirm(owner(), matterA, "order-a.pdf");
    String b = uploadAndConfirm(owner(), matterA, "order-b.pdf");
    String commentId =
        id(postComment(owner(), "TASK", taskA, "INTERNAL", a, b).andExpect(status().isCreated()));

    putComment(owner(), commentId, "reorder", null, List.of(b, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments[0].documentId").value(b))
        .andExpect(jsonPath("$.attachments[1].documentId").value(a));
  }

  // --- delete ---

  @Test
  void deletingTheCommentRemovesLinksAndKeepsTheDocument() throws Exception {
    String doc = uploadAndConfirm(owner(), matterA, "survivor.pdf");
    String commentId =
        id(postComment(owner(), "TASK", taskA, "INTERNAL", doc).andExpect(status().isCreated()));
    assertThat(linkCount(commentId)).isEqualTo(1);

    mockMvc
        .perform(delete("/api/matters/" + matterA + "/comments/" + commentId).with(owner()))
        .andExpect(status().isNoContent());

    assertThat(linkCount(commentId)).isZero();
    mockMvc
        .perform(get("/api/matters/" + matterA + "/documents").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')].source", doc).value("COMMENT"));
    mockMvc
        .perform(get("/api/documents/" + doc + "/presign-download").with(owner()))
        .andExpect(status().isOk());
    // Freed document can be attached again.
    postComment(owner(), "TASK", taskA, "INTERNAL", doc).andExpect(status().isCreated());
  }

  // --- portal ---

  @Test
  void portalShowsChipsOnlyOnSharedCommentsAndOnlyForClientVisibleDocuments() throws Exception {
    String shown = uploadAndConfirm(owner(), matterA, "portal-shown.pdf");
    String unshared = uploadAndConfirm(owner(), matterA, "portal-unshared.pdf");
    String hidden = uploadAndConfirm(owner(), matterA, "portal-hidden.pdf");

    String sharedComment =
        id(
            postComment(owner(), "PROJECT", matterA, "SHARED", shown, unshared)
                .andExpect(status().isCreated()));
    String internalComment =
        id(postComment(owner(), "TASK", taskA, "INTERNAL", hidden).andExpect(status().isCreated()));
    // Unsharing one attachment directly hides its chip on the portal.
    mockMvc
        .perform(
            patch("/api/documents/" + unshared + "/visibility")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"INTERNAL\"}"))
        .andExpect(status().isOk());

    String json =
        mockMvc
            .perform(
                get("/portal/matters/" + matterA + "/comments")
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]", sharedComment).isNotEmpty())
            .andExpect(
                jsonPath("$[?(@.id == '%s')].attachments[0].documentId", sharedComment)
                    .value(shown))
            .andExpect(
                jsonPath("$[?(@.id == '%s')].attachments[0].fileName", sharedComment)
                    .value("portal-shown.pdf"))
            .andExpect(jsonPath("$[?(@.id == '%s')]", internalComment).isEmpty())
            .andExpect(jsonPath("$[*].attachments").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> allChipIds = JsonPath.read(json, "$[*].attachments[*].documentId");
    assertThat(allChipIds).contains(shown).doesNotContain(unshared, hidden);

    mockMvc
        .perform(
            get("/portal/documents/" + shown + "/presign-download")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk());
  }

  // --- helpers ---

  private static JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner");
  }

  private static JwtRequestPostProcessor member2() {
    return TestJwtFactory.memberJwt(ORG_ID, "user_ca_member2");
  }

  private String uploadInit(JwtRequestPostProcessor jwt, String matterId, String fileName)
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.documentId");
  }

  private String uploadAndConfirm(JwtRequestPostProcessor jwt, String matterId, String fileName)
      throws Exception {
    String documentId = uploadInit(jwt, matterId, fileName);
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(jwt))
        .andExpect(status().isOk());
    return documentId;
  }

  private ResultActions postComment(
      JwtRequestPostProcessor jwt,
      String entityType,
      String entityId,
      String visibility,
      String... attachmentIds)
      throws Exception {
    return mockMvc.perform(
        post("/api/matters/" + matterA + "/comments")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"entityType": "%s", "entityId": "%s", "body": "attach test",
                 "visibility": "%s", "attachmentDocumentIds": %s}
                """
                    .formatted(entityType, entityId, visibility, jsonIds(List.of(attachmentIds)))));
  }

  private ResultActions putComment(
      JwtRequestPostProcessor jwt,
      String commentId,
      String body,
      String visibility,
      List<String> attachmentIds)
      throws Exception {
    String visibilityJson = visibility == null ? "null" : "\"" + visibility + "\"";
    String idsJson = attachmentIds == null ? "null" : jsonIds(attachmentIds);
    return mockMvc.perform(
        put("/api/matters/" + matterA + "/comments/" + commentId)
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"body": "%s", "visibility": %s, "attachmentDocumentIds": %s}
                """
                    .formatted(body, visibilityJson, idsJson)));
  }

  private static String jsonIds(List<String> ids) {
    return ids.stream().map(i -> "\"" + i + "\"").collect(Collectors.joining(",", "[", "]"));
  }

  private static String id(ResultActions actions) throws Exception {
    return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private void expectNotAllowed(ResultActions actions, String detail, String documentId)
      throws Exception {
    actions
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.title").value(NOT_ALLOWED))
        .andExpect(jsonPath("$.detail").value(detail));
    if (documentId != null) {
      actions.andExpect(jsonPath("$.documentId").value(documentId));
    } else {
      actions.andExpect(jsonPath("$.documentId").doesNotExist());
    }
  }

  private void assertDocument(String documentId, String visibility, String source)
      throws Exception {
    mockMvc
        .perform(get("/api/matters/" + matterA + "/documents").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')].visibility", documentId).value(visibility))
        .andExpect(jsonPath("$[?(@.id == '%s')].source", documentId).value(source));
  }

  private int linkCount(String commentId) {
    return new JdbcTemplate(migrationDataSource)
        .queryForObject(
            "SELECT count(*) FROM \"%s\".comment_attachments WHERE comment_id = ?::uuid"
                .formatted(tenantSchema),
            Integer.class,
            commentId);
  }
}
