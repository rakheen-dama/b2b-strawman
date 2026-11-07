package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TestDocumentBuilder;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F23 -- the portal Firm-actions trail must not announce a generated document (or Statement of
 * Account) the firm kept as an internal draft. {@code document.generated}, {@code
 * docx_document.generated} and {@code statement.generated} audit rows carry the {@code
 * generated_documents.id} as {@code entity_id}; they surface only when the generated document's
 * linked {@code documents} row is portal-visible (SHARED / PORTAL), checked at read time.
 *
 * <p>Every case drives the real firm endpoints (generate, share, statement) and reads the real
 * {@code GET /portal/activity} endpoint, on both the FIRM tab ({@code findActivityFirmForCustomer})
 * and the ALL tab ({@code findActivityForPortalContact}, the contact is registered).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalActivityInternalDraftIntegrationTest {

  private static final String ORG_ID = "org_pa_internal_draft";
  private static final String OWNER = "user_pa_draft_owner";
  private static final List<String> TABS = List.of("FIRM", "ALL");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID templateId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Internal Draft Firm", "legal-za").schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "pa_draft_owner@test.com", "Draft Owner", "owner"));

    inTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  customerId =
                      customerRepository
                          .saveAndFlush(
                              TestCustomerFactory.createActiveCustomer(
                                  "Draft Client", "pa-draft-client@test.com", memberId))
                          .getId();
                  portalContactService.createContact(
                      ORG_ID,
                      customerId,
                      "pa-draft-client@test.com",
                      "Draft Client",
                      PortalContact.ContactRole.PRIMARY);

                  var project = new Project("Draft Matter", "Closure documents", memberId);
                  project.setCustomerId(customerId);
                  projectId = projectRepository.saveAndFlush(project).getId();
                  customerProjectService.linkCustomerToProject(
                      customerId, projectId, memberId, new ActorContext(memberId, "owner"));

                  templateId =
                      documentTemplateRepository
                          .save(
                              new DocumentTemplate(
                                  TemplateEntityType.PROJECT,
                                  "Closure Letter",
                                  "pa-draft-closure-letter",
                                  TemplateCategory.ENGAGEMENT_LETTER,
                                  TestDocumentBuilder.doc()
                                      .heading(1, "Closure letter")
                                      .paragraph("Your matter is closed.")
                                      .build()))
                          .getId();
                }));
  }

  @Test
  void internalDraftGeneratedDocument_notInPortalActivity() throws Exception {
    UUID generatedDocId = generateSaved();
    assertAudited(generatedDocId, "document.generated");

    assertHiddenOnPortal(generatedDocId);
  }

  @Test
  void sharedGeneratedDocument_inPortalActivity() throws Exception {
    String body = generateSavedBody();
    UUID generatedDocId = UUID.fromString(JsonPath.read(body, "$.id"));
    String documentId = JsonPath.read(body, "$.documentId");

    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"SHARED\"}"))
        .andExpect(status().isOk());

    assertVisibleOnPortal(generatedDocId);
  }

  @Test
  void visibilityChangedToPortal_eventAppears() throws Exception {
    UUID generatedDocId = generateSaved();
    assertHiddenOnPortal(generatedDocId);

    // "Share with client" (ADR-350) moves the linked document to PORTAL; no re-projection, the
    // next read sees it.
    mockMvc
        .perform(
            post("/api/generated-documents/" + generatedDocId + "/share")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    assertVisibleOnPortal(generatedDocId);
  }

  @Test
  void statementGenerated_internal_hidden() throws Exception {
    UUID generatedDocId = generateStatement(false);
    assertAudited(generatedDocId, "statement.generated");

    assertHiddenOnPortal(generatedDocId);
  }

  @Test
  void statementGenerated_sharedWithClient_visible() throws Exception {
    UUID generatedDocId = generateStatement(true);

    assertVisibleOnPortal(generatedDocId);
  }

  @Test
  void unsavedGeneratedDocument_hidden() throws Exception {
    // saveToDocuments=false: no documents row, generated_documents.document_id stays null.
    UUID generatedDocId = generateUnsaved();
    assertAudited(generatedDocId, "document.generated");

    assertHiddenOnPortal(generatedDocId);
  }

  // --- helpers ---

  /** Saved to the matter's documents (INTERNAL by default): 201 with the generated-doc id. */
  private UUID generateSaved() throws Exception {
    return UUID.fromString(JsonPath.read(generateSavedBody(), "$.id"));
  }

  private String generateSavedBody() throws Exception {
    return postGenerate(true)
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * Not saved to documents: the endpoint streams the PDF (200), so the new generated-doc id is the
   * one this call added to the template's history.
   */
  private UUID generateUnsaved() throws Exception {
    List<UUID> before = generatedDocIdsForTemplate();
    postGenerate(false).andExpect(status().isOk());
    List<UUID> added = new ArrayList<>(generatedDocIdsForTemplate());
    added.removeAll(before);
    assertThat(added).hasSize(1);
    return added.getFirst();
  }

  private List<UUID> generatedDocIdsForTemplate() {
    return inTenantReturning(
        () ->
            generatedDocumentRepository.findByTemplateIdOrderByGeneratedAtDesc(templateId).stream()
                .map(GeneratedDocument::getId)
                .toList());
  }

  private ResultActions postGenerate(boolean saveToDocuments) throws Exception {
    return mockMvc.perform(
        post("/api/templates/" + templateId + "/generate")
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"entityId": "%s", "saveToDocuments": %s, "acknowledgeWarnings": true}
                """
                    .formatted(projectId, saveToDocuments)));
  }

  private UUID generateStatement(boolean shareWithClient) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/matters/" + projectId + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart": "2026-04-01", "periodEnd": "2026-04-30",
                         "templateId": null, "shareWithClient": %s}
                        """
                            .formatted(shareWithClient)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(body, "$.id"));
  }

  /** Guards against a vacuous pass: the audit row the portal must hide really exists. */
  private void assertAudited(UUID generatedDocId, String eventType) {
    var rows =
        inTenantReturning(
            () ->
                auditEventRepository
                    .findByFilter(
                        "generated_document",
                        generatedDocId,
                        null,
                        eventType,
                        null,
                        null,
                        PageRequest.of(0, 10))
                    .getContent());
    assertThat(rows).as("%s audit row for %s", eventType, generatedDocId).isNotEmpty();
    assertThat(rows.getFirst().getDetails()).containsEntry("project_id", projectId.toString());
  }

  private void assertHiddenOnPortal(UUID generatedDocId) throws Exception {
    for (String tab : TABS) {
      assertThat(portalEntityIds(tab))
          .as("portal %s tab must not show internal/unsaved generated document", tab)
          .doesNotContain(generatedDocId.toString());
    }
  }

  private void assertVisibleOnPortal(UUID generatedDocId) throws Exception {
    for (String tab : TABS) {
      assertThat(portalEntityIds(tab))
          .as("portal %s tab must show the shared generated document", tab)
          .contains(generatedDocId.toString());
    }
  }

  private List<String> portalEntityIds(String tab) throws Exception {
    String token = portalJwtService.issueToken(customerId, ORG_ID);
    String body =
        mockMvc
            .perform(
                get("/portal/activity")
                    .param("tab", tab)
                    .param("size", "200")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.content[*].entityId");
  }

  private void inTenant(Runnable runnable) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(runnable);
  }

  private <T> T inTenantReturning(ScopedValue.CallableOp<T, RuntimeException> op) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(op);
  }
}
