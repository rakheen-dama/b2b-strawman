package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression test for LZKC-012's existing-tenant delivery mechanism.
 *
 * <p>Template-pack install is idempotent-by-presence (a {@code PackInstall} row short-circuits
 * re-install), so a template added to a pack JSON in a newer version historically never reached
 * tenants that already had the pack. {@code TemplatePackInstaller#reconcile} — invoked by {@code
 * PackInstallService#internalInstall} on the startup reconciliation path — closes that gap: when
 * the classpath pack version is newer than the installed version, missing templates are created and
 * tagged against the existing install.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplatePackReconcileIntegrationTest {

  private static final String ORG_ID = "org_tmpl_pack_reconcile_test";
  private static final String PACK_ID = "legal-za";
  private static final String NEW_TEMPLATE_KEY = "fee-note-za";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Template Pack Reconcile Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void reconcileDeliversNewPackTemplateToExistingTenantOnlyOnVersionBump() {
    // --- Phase 1: simulate a tenant that has the pack but is missing the new template ---
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var feeNote =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, NEW_TEMPLATE_KEY)
                          .orElseThrow();
                  documentTemplateRepository.delete(feeNote);
                }));

    // --- Phase 2: same recorded version -> reconcile is a no-op (no resurrection per boot) ---
    packInstallService.internalInstall(PACK_ID, tenantSchema);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(
                            documentTemplateRepository.findByPackIdAndPackTemplateKey(
                                PACK_ID, NEW_TEMPLATE_KEY))
                        .isEmpty()));

    // --- Phase 3: recorded version older than classpath -> reconcile creates the template ---
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Rewind the recorded version to simulate a tenant installed before v6. Also
                  // exercises the CAS guard: the expected-version match must succeed exactly once.
                  int updated = packInstallRepository.advancePackVersion(PACK_ID, "6", "5", 16);
                  assertThat(updated).isEqualTo(1);
                }));

    packInstallService.internalInstall(PACK_ID, tenantSchema);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var feeNote =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, NEW_TEMPLATE_KEY)
                          .orElseThrow();

                  // Created template is tagged against the existing install with a content hash
                  assertThat(feeNote.getSourcePackInstallId()).isEqualTo(install.getId());
                  assertThat(feeNote.getContentHash()).isNotBlank();

                  // Install row advanced to the classpath pack version
                  assertThat(install.getPackVersion()).isEqualTo("6");
                  assertThat(install.getItemCount()).isEqualTo(17);
                }));

    // --- Phase 4: reconcile again -> idempotent, no duplicate templates ---
    packInstallService.internalInstall(PACK_ID, tenantSchema);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var packTemplates =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .toList();
                  assertThat(packTemplates).hasSize(17);
                  assertThat(
                          packTemplates.stream()
                              .filter(t -> NEW_TEMPLATE_KEY.equals(t.getPackTemplateKey())))
                      .hasSize(1);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
