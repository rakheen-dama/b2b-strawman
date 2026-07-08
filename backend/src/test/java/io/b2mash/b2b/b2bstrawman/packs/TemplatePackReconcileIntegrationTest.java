package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplatePackReconcileIntegrationTest {

  private static final String ORG_ID = "org_tmpl_pack_reconcile_test";
  private static final String PACK_ID = "legal-za";
  private static final String NEW_TEMPLATE_KEY = "fee-note-za";

  private static final String COMMON_PACK_ID = "common";
  private static final String COVER_LETTER_KEY = "invoice-cover-letter";
  private static final String EDITED_TEMPLATE_KEY = "engagement-letter";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TemplatePackInstaller templatePackInstaller;
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

  /**
   * Regression test for LZKC-010's existing-tenant delivery: a content fix to a pack template (e.g.
   * the {@code invoice.number} → {@code invoice.invoiceNumber} placeholder typo in the common
   * pack's invoice cover letter) must reach tenants that already have the pack — but only for
   * templates the tenant has NOT modified. Tenant-edited templates (stored content hash no longer
   * matches the current content) are preserved untouched.
   */
  @Test
  void reconcileRefreshesUnmodifiedTemplateContentButPreservesTenantEdits() {
    Map<String, Object> staleCoverLetterContent =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of("type", "variable", "attrs", Map.of("key", "invoice.number"))))));

    Map<String, Object> tenantEditedContent =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.of("type", "text", "text", "Tenant customized engagement")))));

    // --- Phase 1: simulate a tenant seeded from an older pack version ---
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Cover letter: stale pack content whose stored hash MATCHES its content —
                  // i.e. the tenant never touched it; it is simply from an older pack version.
                  var coverLetter =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(COMMON_PACK_ID, COVER_LETTER_KEY)
                          .orElseThrow();
                  coverLetter.updateContent(
                      coverLetter.getName(),
                      coverLetter.getDescription(),
                      staleCoverLetterContent,
                      coverLetter.getCss());
                  coverLetter.setContentHash(
                      templatePackInstaller.computeContentHash(
                          staleCoverLetterContent, coverLetter.getCss()));
                  documentTemplateRepository.save(coverLetter);

                  // Engagement letter: tenant-edited content — stored hash does NOT match.
                  var engagement =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(COMMON_PACK_ID, EDITED_TEMPLATE_KEY)
                          .orElseThrow();
                  engagement.updateContent(
                      engagement.getName(),
                      engagement.getDescription(),
                      tenantEditedContent,
                      engagement.getCss());
                  // contentHash deliberately left as the original install-time hash -> mismatch
                  documentTemplateRepository.save(engagement);

                  // Rewind the recorded common-pack version to simulate pre-v2 install.
                  int updated =
                      packInstallRepository.advancePackVersion(COMMON_PACK_ID, "2", "1", 3);
                  assertThat(updated).isEqualTo(1);
                }));

    // --- Phase 2: startup reconciliation ---
    packInstallService.internalInstall(COMMON_PACK_ID, tenantSchema);

    // --- Phase 3: unmodified template refreshed, tenant-edited template preserved ---
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(COMMON_PACK_ID).orElseThrow();
                  assertThat(install.getPackVersion()).isEqualTo("2");

                  var coverLetter =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(COMMON_PACK_ID, COVER_LETTER_KEY)
                          .orElseThrow();
                  String coverLetterJson = coverLetter.getContent().toString();
                  assertThat(coverLetterJson).contains("invoice.invoiceNumber");
                  assertThat(coverLetterJson).doesNotContain("key=invoice.number");
                  // Hash re-pinned to the refreshed content so the template still reads as
                  // pristine (uninstall gate + future reconciles).
                  assertThat(coverLetter.getContentHash())
                      .isEqualTo(
                          templatePackInstaller.computeContentHash(
                              coverLetter.getContent(), coverLetter.getCss()));

                  var engagement =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(COMMON_PACK_ID, EDITED_TEMPLATE_KEY)
                          .orElseThrow();
                  assertThat(engagement.getContent().toString())
                      .contains("Tenant customized engagement");
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
