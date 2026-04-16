package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationExecution;
import io.b2mash.b2b.b2bstrawman.automation.AutomationExecutionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.ExecutionStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackUninstallGateTest {

  private static final String ORG_ID = "org_pug_test";
  private static final String TEMPLATE_PACK_ID = "common";
  private static final String AUTOMATION_PACK_ID = "automation-common";

  @Autowired private MockMvc mockMvc;
  @Autowired private TemplatePackInstaller templatePackInstaller;
  @Autowired private AutomationPackInstaller automationPackInstaller;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private String memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pack Uninstall Gate Test Org", null);
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pug_owner", "pug_owner@test.com", "PUG Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void cleanInstallThenUninstallSucceeds() {
    // Install template pack fresh (note: provisioning already seeded via seeder,
    // so we need to work with PackInstaller's install which checks for existing PackInstall)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // The seeder already created templates during provisioning, but without
                  // PackInstall tracking. Install via the installer to get tracked state.
                  templatePackInstaller.install(TEMPLATE_PACK_ID, tenantSchema, memberId);
                }));

    // Verify uninstall check passes
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var check =
                      templatePackInstaller.checkUninstallable(TEMPLATE_PACK_ID, tenantSchema);
                  assertThat(check.canUninstall()).isTrue();
                  assertThat(check.blockingReason()).isNull();
                }));

    // Perform uninstall
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.uninstall(TEMPLATE_PACK_ID, tenantSchema, memberId)));

    // Verify everything is cleaned up
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  assertThat(packInstallRepository.findByPackId(TEMPLATE_PACK_ID)).isEmpty();
                  // Templates that belonged to this install should be gone
                  var remaining =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(dt -> TEMPLATE_PACK_ID.equals(dt.getPackId()))
                          .toList();
                  // Only templates without sourcePackInstallId (legacy) might remain
                  assertThat(remaining)
                      .allSatisfy(dt -> assertThat(dt.getSourcePackInstallId()).isNull());
                }));
  }

  @Test
  void editedTemplateBlocksUninstall() {
    // Re-install after previous test may have uninstalled
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.install(TEMPLATE_PACK_ID, tenantSchema, memberId)));

    // Edit one template's content
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(TEMPLATE_PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();
                  DocumentTemplate first = templates.getFirst();
                  first.updateContent(
                      first.getName(),
                      first.getDescription(),
                      Map.of(
                          "type",
                          "doc",
                          "content",
                          java.util.List.of(
                              Map.of(
                                  "type",
                                  "paragraph",
                                  "content",
                                  java.util.List.of(Map.of("type", "text", "text", "EDITED"))))),
                      first.getCss());
                  documentTemplateRepository.save(first);
                }));

    // Check should block
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var check =
                      templatePackInstaller.checkUninstallable(TEMPLATE_PACK_ID, tenantSchema);
                  assertThat(check.canUninstall()).isFalse();
                  assertThat(check.blockingReason()).contains("templates have been edited");
                }));

    // Clean up: revert the edit and uninstall for test isolation
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(TEMPLATE_PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  // Restore original hash by recomputing from content — but content was changed,
                  // so we just force the hash to match so uninstall works for cleanup
                  for (DocumentTemplate dt : templates) {
                    if (dt.getContent() != null) {
                      var hashInput = new java.util.LinkedHashMap<String, Object>();
                      hashInput.put("content", dt.getContent());
                      if (dt.getCss() != null) {
                        hashInput.put("css", dt.getCss());
                      }
                      var node = objectMapper.valueToTree(hashInput);
                      dt.setContentHash(
                          ContentHashUtil.computeHash(ContentHashUtil.canonicalizeJson(node)));
                      documentTemplateRepository.save(dt);
                    }
                  }
                }));

    // Now uninstall should work
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.uninstall(TEMPLATE_PACK_ID, tenantSchema, memberId)));
  }

  @Test
  void generatedDocumentBlocksUninstall() {
    // Install template pack
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.install(TEMPLATE_PACK_ID, tenantSchema, memberId)));

    // Create a GeneratedDocument referencing a pack template
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(TEMPLATE_PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();
                  var genDoc =
                      new GeneratedDocument(
                          templates.getFirst().getId(),
                          TemplateEntityType.PROJECT,
                          UUID.randomUUID(),
                          "test-doc.pdf",
                          "s3://test/test-doc.pdf",
                          1024L,
                          UUID.fromString(memberId));
                  generatedDocumentRepository.save(genDoc);
                }));

    // Check should block
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var check =
                      templatePackInstaller.checkUninstallable(TEMPLATE_PACK_ID, tenantSchema);
                  assertThat(check.canUninstall()).isFalse();
                  assertThat(check.blockingReason()).contains("used to generate documents");
                }));

    // Clean up: remove generated doc and then uninstall
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  generatedDocumentRepository.deleteAll();
                  templatePackInstaller.uninstall(TEMPLATE_PACK_ID, tenantSchema, memberId);
                }));
  }

  @Test
  void automationExecutionBlocksUninstall() {
    // Install automation pack
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> automationPackInstaller.install(AUTOMATION_PACK_ID, tenantSchema, memberId)));

    // Create an execution referencing a pack rule
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install =
                      packInstallRepository.findByPackId(AUTOMATION_PACK_ID).orElseThrow();
                  var rules = ruleRepository.findBySourcePackInstallId(install.getId());
                  assertThat(rules).isNotEmpty();
                  var execution =
                      new AutomationExecution(
                          rules.getFirst().getId(),
                          "TASK_STATUS_CHANGED",
                          Map.of("taskId", UUID.randomUUID().toString()),
                          true,
                          ExecutionStatus.ACTIONS_COMPLETED);
                  executionRepository.save(execution);
                }));

    // Check should block
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var check =
                      automationPackInstaller.checkUninstallable(AUTOMATION_PACK_ID, tenantSchema);
                  assertThat(check.canUninstall()).isFalse();
                  assertThat(check.blockingReason()).contains("been executed");
                }));

    // Clean up
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  executionRepository.deleteAll();
                  automationPackInstaller.uninstall(AUTOMATION_PACK_ID, tenantSchema, memberId);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
