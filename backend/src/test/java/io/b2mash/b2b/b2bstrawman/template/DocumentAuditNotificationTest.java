package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentAuditNotificationTest {

  private static final Map<String, Object> CONTENT =
      TestDocumentBuilder.doc()
          .heading(1, "Audit Notification Test")
          .paragraph("Template content for audit and notification tests.")
          .build();
  private static final String ORG_ID = "org_audit_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private UUID testProjectId;
  private UUID testTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Audit Notif Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_audit_owner", "audit_owner@test.com", "Audit Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Audit Test Project", "For audit tests", UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Audit Template",
                          "audit-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          CONTENT);
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();
                }));
  }

  @Test
  void shouldCreateAuditEventOnTemplateCreation() throws Exception {
    mockMvc
        .perform(
            post("/api/templates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_audit_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Audit Created Template",
                      "category": "REPORT",
                      "primaryEntityType": "PROJECT",
                      "content": {"type": "doc", "content": []}
                    }
                    """))
        .andExpect(status().isCreated());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "document_template",
                          null,
                          null,
                          "template.created",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEntityType()).isEqualTo("document_template");
                  assertThat(latestEvent.getEventType()).isEqualTo("template.created");
                }));
  }

  @Test
  void shouldCreateAuditEventOnTemplateClone() throws Exception {
    final UUID[] platformTemplateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Clone Source Template",
                          "clone-source-audit",
                          TemplateCategory.PROPOSAL,
                          CONTENT);
                  template.setSource(TemplateSource.PLATFORM);
                  template = documentTemplateRepository.save(template);
                  platformTemplateId[0] = template.getId();
                }));

    mockMvc
        .perform(
            post("/api/templates/" + platformTemplateId[0] + "/clone")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_audit_owner")))
        .andExpect(status().isCreated());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "document_template",
                          null,
                          null,
                          "template.cloned",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEventType()).isEqualTo("template.cloned");
                }));
  }

  @Test
  void shouldCreateAuditEventOnDocumentGeneration() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_audit_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "generated_document",
                          null,
                          null,
                          "document.generated",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEventType()).isEqualTo("document.generated");
                  assertThat(latestEvent.getEntityType()).isEqualTo("generated_document");
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
