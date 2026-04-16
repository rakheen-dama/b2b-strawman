package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplatePackInstallerTest {

  private static final String ORG_ID = "org_tpi_test";
  private static final String PACK_ID = "common";

  @Autowired private MockMvc mockMvc;
  @Autowired private TemplatePackInstaller templatePackInstaller;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Pack Installer Test Org", null);
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_tpi_owner", "tpi_owner@test.com", "TPI Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Install the pack
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.install(PACK_ID, tenantSchema, memberId)));
  }

  @Test
  void installCreatesPackInstallRow() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID);
                  assertThat(install).isPresent();
                  assertThat(install.get().getPackType()).isEqualTo(PackType.DOCUMENT_TEMPLATE);
                  assertThat(install.get().getPackName()).isEqualTo("Common Templates");
                  assertThat(install.get().getItemCount()).isEqualTo(3);
                }));
  }

  @Test
  void installedTemplatesHaveSourcePackInstallId() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();
                  assertThat(templates)
                      .allSatisfy(
                          dt -> assertThat(dt.getSourcePackInstallId()).isEqualTo(install.getId()));
                }));
  }

  @Test
  void installedTemplatesHaveContentHash() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();
                  assertThat(templates)
                      .allSatisfy(
                          dt -> {
                            assertThat(dt.getContentHash()).isNotNull();
                            assertThat(dt.getContentHash()).hasSize(64); // SHA-256 hex
                          });
                }));
  }

  @Test
  void idempotencySecondInstallIsNoOp() {
    // Count templates before second install
    int[] beforeCount = new int[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  beforeCount[0] = templates.size();
                }));

    // Install again
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> templatePackInstaller.install(PACK_ID, tenantSchema, memberId)));

    // Count should be unchanged
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  assertThat(templates).hasSize(beforeCount[0]);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
