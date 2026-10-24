package io.b2mash.b2b.b2bstrawman.template;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class GeneratedDocumentControllerTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();
  private static final String CONTACT_EMAIL = "gendocctrl-portal-contact@test.com";

  private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of());
  private static final String ORG_ID = "org_gen_doc_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;
  private UUID testProjectId;
  private UUID testTemplateId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "GenDoc Controller Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_gendocctrl_owner",
            "gendocctrl_owner@test.com",
            "GenDocCtrl Owner",
            "owner");

    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_gendocctrl_member",
            "gendocctrl_member@test.com",
            "GenDocCtrl Member",
            "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "GenDocCtrl Test Project",
                          "For controller tests",
                          UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "GenDocCtrl Template",
                          "gendocctrl-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          CONTENT);
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();

                  // ADR-350 share tests: an ACTIVE client with a portal contact so the
                  // document-ready email has a recipient.
                  var customer =
                      createActiveCustomer(
                          "GenDocCtrl Client", CONTACT_EMAIL, UUID.fromString(memberIdOwner));
                  customerId = customerRepository.saveAndFlush(customer).getId();
                  portalContactRepository.saveAndFlush(
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          CONTACT_EMAIL,
                          "GenDocCtrl Contact",
                          PortalContact.ContactRole.PRIMARY));
                }));

    // Generate a document so we have data for list/download/delete tests
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": true, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldListGeneratedDocumentsByEntity() throws Exception {
    mockMvc
        .perform(
            get("/api/generated-documents")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .param("entityType", "PROJECT")
                .param("entityId", testProjectId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].templateName").value("GenDocCtrl Template"))
        .andExpect(jsonPath("$[0].fileName").exists())
        .andExpect(jsonPath("$[0].generatedByName").value("GenDocCtrl Owner"));
  }

  @Test
  void shouldDownloadGeneratedDocument() throws Exception {
    // First get the generated doc ID from list
    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String generatedDocId = JsonPath.read(listResult.getResponse().getContentAsString(), "$[0].id");

    // Download returns 302 redirect to presigned URL
    mockMvc
        .perform(
            get("/api/generated-documents/" + generatedDocId + "/download")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner")))
        .andExpect(status().is(302));
  }

  @Test
  void adminCanDeleteGeneratedDocument() throws Exception {
    // Generate a new document to delete
    var genResult =
        mockMvc
            .perform(
                post("/api/templates/" + testTemplateId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                        """
                            .formatted(testProjectId)))
            .andExpect(status().isOk())
            .andReturn();

    // Get the generated doc from the list (last one created)
    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String content = listResult.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(content, "$[*].id");
    String latestDocId = ids.getFirst();

    mockMvc
        .perform(
            delete("/api/generated-documents/" + latestDocId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner")))
        .andExpect(status().isNoContent());
  }

  @Test
  void memberCannotDeleteGeneratedDocument() throws Exception {
    // Generate a new document
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String content = listResult.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(content, "$[*].id");
    String latestDocId = ids.getFirst();

    mockMvc
        .perform(
            delete("/api/generated-documents/" + latestDocId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_gendocctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldIncludeTemplateNameAndGeneratedByInList() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].templateName").isNotEmpty())
            .andExpect(jsonPath("$[0].generatedByName").isNotEmpty())
            .andExpect(jsonPath("$[0].fileSize").isNumber())
            .andExpect(jsonPath("$[0].primaryEntityType").value("PROJECT"))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String templateName = JsonPath.read(responseBody, "$[0].templateName");
    assertThat(templateName).isEqualTo("GenDocCtrl Template");
  }

  @Test
  void downloadDocxReturns404WhenNoDocxAvailable() throws Exception {
    // The document generated in setup is PDF-only (no docxS3Key), so download-docx should 404
    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String generatedDocId = JsonPath.read(listResult.getResponse().getContentAsString(), "$[0].id");

    mockMvc
        .perform(
            get("/api/generated-documents/" + generatedDocId + "/download-docx")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void listResponseIncludesHasDocxDownloadField() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hasDocxDownload").value(false))
            .andExpect(jsonPath("$[0].outputFormat").value("PDF"))
            .andReturn();

    // Verify that docxS3Key is NOT exposed in the response
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).doesNotContain("docxS3Key");
  }

  @BeforeEach
  void purgeMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  // ==========================================================================
  // Phase 88 U13 / ADR-350 — POST /api/generated-documents/{id}/share
  // ==========================================================================

  @Test
  void share_internalDocument_flipsToPortal_noEmailByDefault() throws Exception {
    UUID projectId = createClientProject("Share Default Matter");
    var seeded = seedGeneratedDocument(projectId, true);

    mockMvc
        .perform(
            post("/api/generated-documents/" + seeded.generatedDocumentId() + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notifyClient": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(seeded.generatedDocumentId().toString()))
        .andExpect(jsonPath("$.documentId").value(seeded.documentId().toString()))
        .andExpect(jsonPath("$.visibility").value("PORTAL"));

    assertThat(documentVisibility(seeded.documentId())).isEqualTo(Document.Visibility.PORTAL);
    greenMail.waitForIncomingEmail(1_000L, 1);
    assertThat(mailsToContact()).as("share without notifyClient must not email").isEmpty();
  }

  @Test
  void share_withNotifyClient_sendsOneDocumentReadyEmail() throws Exception {
    UUID projectId = createClientProject("Share Notify Matter");
    var seeded = seedGeneratedDocument(projectId, true);

    mockMvc
        .perform(
            post("/api/generated-documents/" + seeded.generatedDocumentId() + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notifyClient": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("PORTAL"));

    assertThat(greenMail.waitForIncomingEmail(5_000L, 1)).isTrue();
    var mails = mailsToContact();
    assertThat(mails).hasSize(1);
    var body = new StringBuilder();
    collectTextParts(mails.get(0).getContent(), body);
    assertThat(body.toString())
        .contains("http://localhost:3002/matters/" + projectId + "?orgId=" + ORG_ID);
  }

  @Test
  void share_alreadyPortal_isIdempotent() throws Exception {
    UUID projectId = createClientProject("Share Idempotent Matter");
    var seeded = seedGeneratedDocument(projectId, true);

    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/api/generated-documents/" + seeded.generatedDocumentId() + "/share")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.visibility").value("PORTAL"));
    }

    assertThat(
            events.stream(DocumentVisibilityChangedEvent.class)
                .filter(e -> seeded.documentId().equals(e.getDocumentId())))
        .as("D-5: a second share must not re-flip (the portal read model would double-count)")
        .hasSize(1);
  }

  @Test
  void share_member_returns403() throws Exception {
    UUID projectId = createClientProject("Share Member Matter");
    var seeded = seedGeneratedDocument(projectId, true);

    mockMvc
        .perform(
            post("/api/generated-documents/" + seeded.generatedDocumentId() + "/share")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_gendocctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(documentVisibility(seeded.documentId())).isEqualTo(Document.Visibility.INTERNAL);
  }

  @Test
  void share_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/generated-documents/" + UUID.randomUUID() + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("GeneratedDocument not found"));
  }

  @Test
  void share_notSavedToDocuments_returns400() throws Exception {
    UUID projectId = createClientProject("Share Unsaved Matter");
    var seeded = seedGeneratedDocument(projectId, false);

    mockMvc
        .perform(
            post("/api/generated-documents/" + seeded.generatedDocumentId() + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Cannot share document"))
        .andExpect(
            jsonPath("$.detail").value("The document was not saved to the matter's documents."));
  }

  @Test
  void share_nonMatterDocument_returns400() throws Exception {
    var generatedId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    generatedId[0] =
                        generatedDocumentRepository
                            .save(
                                new GeneratedDocument(
                                    testTemplateId,
                                    TemplateEntityType.CUSTOMER,
                                    customerId,
                                    "client-doc.pdf",
                                    "org/test/generated/client-doc.pdf",
                                    10,
                                    UUID.fromString(memberIdOwner)))
                            .getId()));

    mockMvc
        .perform(
            post("/api/generated-documents/" + generatedId[0] + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot share document"))
        .andExpect(
            jsonPath("$.detail").value("Only matter documents can be shared with the client."));
  }

  /** D-9: the list carries the linked Document's visibility (null when not saved). */
  @Test
  void list_includesLinkedDocumentVisibility() throws Exception {
    UUID projectId = createClientProject("List Visibility Matter");
    var saved = seedGeneratedDocument(projectId, true);
    var unsaved = seedGeneratedDocument(projectId, false);

    var result =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gendocctrl_owner"))
                    .param("entityType", "PROJECT")
                    .param("entityId", projectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    List<String> savedVisibility =
        JsonPath.read(body, "$[?(@.id=='" + saved.generatedDocumentId() + "')].visibility");
    assertThat(savedVisibility).containsExactly("INTERNAL");
    List<Object> unsavedVisibility =
        JsonPath.read(body, "$[?(@.id=='" + unsaved.generatedDocumentId() + "')].visibility");
    assertThat(unsavedVisibility).hasSize(1);
    assertThat(unsavedVisibility.get(0)).isNull();
  }

  private record Seeded(UUID generatedDocumentId, UUID documentId) {}

  private UUID createClientProject(String name) {
    var id = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(name, "ADR-350 share test", UUID.fromString(memberIdOwner));
                  project.setCustomerId(customerId);
                  id[0] = projectRepository.saveAndFlush(project).getId();
                }));
    return id[0];
  }

  /** Seeds a generated matter document, optionally linked to an INTERNAL Document row. */
  private Seeded seedGeneratedDocument(UUID projectId, boolean saveToDocuments) {
    var holder = new Seeded[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  UUID owner = UUID.fromString(memberIdOwner);
                  String fileName = "share-" + UUID.randomUUID() + ".pdf";
                  String s3Key = "org/test/generated/" + fileName;
                  var generated =
                      new GeneratedDocument(
                          testTemplateId,
                          TemplateEntityType.PROJECT,
                          projectId,
                          fileName,
                          s3Key,
                          10,
                          owner);
                  UUID documentId = null;
                  if (saveToDocuments) {
                    var doc = new Document(projectId, fileName, "application/pdf", 10, owner);
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    documentId = documentRepository.save(doc).getId();
                    generated.linkToDocument(documentId);
                  }
                  generated = generatedDocumentRepository.save(generated);
                  holder[0] = new Seeded(generated.getId(), documentId);
                }));
    return holder[0];
  }

  private String documentVisibility(UUID documentId) {
    var holder = new String[1];
    runInTenant(
        () ->
            holder[0] =
                transactionTemplate.execute(
                    tx -> documentRepository.findById(documentId).orElseThrow().getVisibility()));
    return holder[0];
  }

  private static void collectTextParts(Object content, StringBuilder out) throws Exception {
    if (content instanceof jakarta.mail.Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        collectTextParts(multipart.getBodyPart(i).getContent(), out);
      }
    } else if (content != null) {
      out.append(content);
    }
  }

  private List<MimeMessage> mailsToContact() {
    return Arrays.stream(greenMail.getReceivedMessages())
        .filter(
            m -> {
              try {
                return CONTACT_EMAIL.equals(m.getAllRecipients()[0].toString());
              } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
