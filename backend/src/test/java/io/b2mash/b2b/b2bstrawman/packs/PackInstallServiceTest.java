package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PackInstallServiceTest {

  private static final String ORG_ID = "org_pis_test";
  private static final String PACK_ID = "common";

  @Autowired private MockMvc mockMvc;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackCatalogService packCatalogService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pack Install Service Test Org", null);
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pis_owner", "pis_owner@test.com", "PIS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void installCreatesPackInstallRowAndContentRows() {
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  PackInstall install = packInstallService.install(PACK_ID, memberId);

                  assertThat(install).isNotNull();
                  assertThat(install.getPackId()).isEqualTo(PACK_ID);
                  assertThat(install.getPackType()).isEqualTo(PackType.DOCUMENT_TEMPLATE);
                  assertThat(install.getInstalledAt()).isNotNull();

                  // Verify content rows have source_pack_install_id
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();
                  assertThat(templates)
                      .allSatisfy(
                          dt -> assertThat(dt.getSourcePackInstallId()).isEqualTo(install.getId()));
                }));
  }

  @Test
  @Order(2)
  void doubleInstallReturnsExistingInstall() {
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  PackInstall existing = packInstallService.install(PACK_ID, memberId);

                  assertThat(existing).isNotNull();
                  assertThat(existing.getPackId()).isEqualTo(PACK_ID);

                  // Only one PackInstall row should exist for this packId
                  long count =
                      packInstallRepository.findAll().stream()
                          .filter(pi -> PACK_ID.equals(pi.getPackId()))
                          .count();
                  assertThat(count).isEqualTo(1);
                }));
  }

  @Test
  @Order(3)
  void orgSettingsTemplatePackStatusUpdatedAfterInstall() {
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  var templatePackStatus = settings.get().getTemplatePackStatus();
                  assertThat(templatePackStatus).isNotNull();
                  assertThat(templatePackStatus)
                      .anyMatch(entry -> PACK_ID.equals(entry.get("packId")));
                }));
  }

  @Test
  @Order(4)
  void profileAffinityRejectsCrossProfilePack() {
    // Profile affinity: install() rejects cross-profile packs.
    // Our tenant has no vertical profile, so any pack with a verticalProfile != null should fail.
    // We test this by calling install() outside a TransactionTemplate so the thrown exception
    // doesn't mark an outer transaction as rollback-only.
    runInTenantWithMember(
        () -> {
          var allPacks = packCatalogService.listCatalog(true);
          var profileSpecificPack =
              allPacks.stream().filter(p -> p.verticalProfile() != null).findFirst();

          if (profileSpecificPack.isPresent()) {
            String profilePackId = profileSpecificPack.get().packId();
            String expectedProfile = profileSpecificPack.get().verticalProfile();
            // The install() call is @Transactional itself, so it manages its own transaction.
            // When it throws, its transaction rolls back cleanly.
            assertThatThrownBy(() -> packInstallService.install(profilePackId, memberId))
                .isInstanceOf(InvalidStateException.class)
                .satisfies(
                    thrown -> {
                      var ex = (InvalidStateException) thrown;
                      var problem = ex.getBody();
                      assertThat(problem.getStatus()).isEqualTo(400);
                      assertThat(problem.getTitle()).isEqualTo("Profile mismatch");
                      assertThat(problem.getDetail()).contains(expectedProfile);
                    });
          }
          // If no profile-specific packs exist, universal packs are proven in Order(1)
        });
  }

  @Test
  @Order(5)
  void installAndImmediateUninstallRemovesContentAndPackInstallRow() {
    // Uninstall the pack installed in Order(1)
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  packInstallService.uninstall(PACK_ID, memberId);

                  // PackInstall row should be gone
                  assertThat(packInstallRepository.findByPackId(PACK_ID)).isEmpty();
                }));

    // Verify OrgSettings templatePackStatus no longer contains the pack
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  var templatePackStatus = settings.get().getTemplatePackStatus();
                  if (templatePackStatus != null) {
                    assertThat(templatePackStatus)
                        .noneMatch(entry -> PACK_ID.equals(entry.get("packId")));
                  }
                }));
  }

  @Test
  @Order(6)
  void installEditTemplateUninstallBlockedWith409() {
    // Re-install the common pack
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> packInstallService.install(PACK_ID, memberId)));

    // Edit one of the installed templates to trigger the content hash mismatch gate
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).isNotEmpty();

                  // Modify the first template's CSS to trigger content hash mismatch
                  DocumentTemplate template = templates.getFirst();
                  template.setCss(
                      (template.getCss() != null ? template.getCss() : "") + " /* EDITED */");
                  documentTemplateRepository.save(template);
                }));

    // Now try to uninstall -- should be blocked.
    // Call uninstall() outside TransactionTemplate to avoid rollback-only propagation.
    // The service method is @Transactional and will roll back its own transaction on exception.
    runInTenantWithMember(
        () ->
            assertThatThrownBy(() -> packInstallService.uninstall(PACK_ID, memberId))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(
                    thrown -> {
                      var ex = (ResourceConflictException) thrown;
                      var problem = ex.getBody();
                      assertThat(problem.getStatus()).isEqualTo(409);
                      assertThat(problem.getTitle()).isNotBlank();
                      assertThat(problem.getDetail()).isNotBlank();
                    }));
  }

  @Test
  @Order(7)
  void auditEventsEmittedForInstallAndUninstall() {
    // Verify audit events from previous test steps.
    // Order(1) install + Order(5) uninstall + Order(6) re-install all committed successfully.
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Filter by entity type "pack_install" to scope assertions to our domain
                  // Check for pack.installed events (from Order 1 and Order 6 = exactly 2)
                  var installedEvents =
                      auditService.findEvents(
                          new AuditEventFilter(
                              "pack_install", null, null, "pack.installed", null, null),
                          PageRequest.of(0, 50));
                  assertThat(installedEvents.getTotalElements()).isEqualTo(2);
                  assertThat(installedEvents.getContent())
                      .allSatisfy(
                          event -> assertThat(event.getDetails().get("packId")).isEqualTo(PACK_ID));

                  // Check for pack.uninstalled events (from Order 5 = exactly 1)
                  var uninstalledEvents =
                      auditService.findEvents(
                          new AuditEventFilter(
                              "pack_install", null, null, "pack.uninstalled", null, null),
                          PageRequest.of(0, 50));
                  assertThat(uninstalledEvents.getTotalElements()).isEqualTo(1);
                  assertThat(uninstalledEvents.getContent().getFirst().getDetails().get("packId"))
                      .isEqualTo(PACK_ID);
                }));
  }

  @Test
  @Order(8)
  void listCatalogFiltersAndEnrichesInstallState() {
    // Pack from Order(6) is still installed (uninstall was blocked)
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // listCatalog(false) - profile-filtered (our tenant has no profile, so only
                  // universal)
                  var filtered = packCatalogService.listCatalog(false);
                  assertThat(filtered).isNotEmpty();
                  assertThat(filtered)
                      .allSatisfy(
                          entry ->
                              assertThat(entry.verticalProfile())
                                  .isNull()); // Only universal packs for null-profile tenant

                  // listCatalog(true) - show all
                  var all = packCatalogService.listCatalog(true);
                  assertThat(all.size()).isGreaterThanOrEqualTo(filtered.size());

                  // The common pack should be marked as installed
                  var commonEntry =
                      filtered.stream().filter(e -> PACK_ID.equals(e.packId())).findFirst();
                  assertThat(commonEntry).isPresent();
                  assertThat(commonEntry.get().installed()).isTrue();
                  assertThat(commonEntry.get().installedAt()).isNotNull();
                }));
  }

  private void runInTenantWithMember(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberId))
        .run(action);
  }
}
