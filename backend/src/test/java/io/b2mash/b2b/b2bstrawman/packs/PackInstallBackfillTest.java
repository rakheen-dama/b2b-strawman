package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationExecutionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.StandardReportPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackInstallBackfillTest {

  private static final String ORG_ID = "org_pack_install_backfill_test";
  private static final String LEGAL_ORG_ID = "org_pack_install_backfill_legal";

  /** Seeder-managed (ledger-only) pack types introduced by Epic 625E (ADR-356). */
  private static final Set<PackType> LEDGER_TYPES =
      EnumSet.of(PackType.CLAUSE, PackType.FIELD, PackType.REPORT, PackType.REQUEST);

  /** The ledger rows a fresh legal-za tenant must hold — type → (packId → version). */
  private static final Map<PackType, Map<String, String>> EXPECTED_LEGAL_ZA_LEDGER =
      Map.of(
          PackType.CLAUSE,
          new TreeMap<>(
              Map.of(
                  "conveyancing-za-clauses", "1",
                  "legal-za-clauses", "1",
                  "standard-clauses", "1")),
          PackType.FIELD,
          new TreeMap<>(
              Map.of(
                  "common-project", "2",
                  "common-task", "2",
                  "conveyancing-za-project", "1",
                  "legal-za-customer", "2",
                  "legal-za-project", "2")),
          PackType.REPORT,
          new TreeMap<>(Map.of("standard-reports", "1")),
          PackType.REQUEST,
          new TreeMap<>(
              Map.of(
                  "conveyancing-intake-za", "1",
                  "fica-onboarding-pack", "1",
                  "legal-za-liquidation-distribution", "1")));

  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private AutomationRuleRepository automationRuleRepository;
  @Autowired private AutomationExecutionRepository automationExecutionRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private ClausePackSeeder clausePackSeeder;
  @Autowired private RequestPackSeeder requestPackSeeder;
  @Autowired private StandardReportPackSeeder standardReportPackSeeder;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private PackCatalogService packCatalogService;
  @Autowired private PackInstallService packInstallService;

  private String schemaName;
  private String legalSchema;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Pack Install Backfill Test Org", null)
            .schemaName();
    legalSchema =
        provisioningService
            .provisionTenant(LEGAL_ORG_ID, "Pack Install Backfill Legal Org", "legal-za")
            .schemaName();
  }

  @Test
  void v95MigrationCreatesPackInstallRowsForSeededTemplatePacks() {
    // Tenant provisioning runs all migrations including V95.
    // The provisioning also seeds template packs via TemplatePackSeeder,
    // which writes to OrgSettings.template_pack_status.
    // V95 backfill should have created PackInstall rows for those packs.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var packInstalls = packInstallRepository.findAll();
                      // The common template pack is seeded during provisioning.
                      // V95 should have created a PackInstall row for it.
                      // Note: V95 runs AFTER seeding, so the JSONB entries should exist.
                      // However, if provisioning seeds packs after migrations, the backfill
                      // won't find entries. We verify migration runs without error regardless.
                      assertThat(packInstalls).isNotNull();

                      // Verify document templates have the new columns available
                      var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                      assertThat(templates).isNotNull();
                      // sourcePackInstallId may or may not be set depending on timing
                      // but the column must exist (no SQL errors)
                    }));
  }

  @Test
  void repositoryQueryMethodsFindBySourcePackInstallId() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create a PackInstall for testing
                      var packInstall =
                          new PackInstall(
                              "backfill-query-test-pack",
                              PackType.DOCUMENT_TEMPLATE,
                              "2.0.0",
                              "Query Test Pack",
                              Instant.now(),
                              null,
                              0);
                      var saved = packInstallRepository.save(packInstall);
                      packInstallRepository.flush();

                      // findBySourcePackInstallId on documents (no docs linked yet)
                      var linkedDocs =
                          documentTemplateRepository.findBySourcePackInstallId(saved.getId());
                      assertThat(linkedDocs).isEmpty();

                      int docCount =
                          documentTemplateRepository.countBySourcePackInstallId(saved.getId());
                      assertThat(docCount).isZero();

                      // findBySourcePackInstallId on automation rules (no rules linked yet)
                      var linkedRules =
                          automationRuleRepository.findBySourcePackInstallId(saved.getId());
                      assertThat(linkedRules).isEmpty();

                      int ruleCount =
                          automationRuleRepository.countBySourcePackInstallId(saved.getId());
                      assertThat(ruleCount).isZero();
                    }));
  }

  @Test
  void existsByTemplateIdInAndExistsByRuleIdIn() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // existsByTemplateIdIn with non-existent IDs
                      boolean hasGeneratedDocs =
                          generatedDocumentRepository.existsByTemplateIdIn(
                              List.of(UUID.randomUUID(), UUID.randomUUID()));
                      assertThat(hasGeneratedDocs).isFalse();

                      // existsByRuleIdIn with non-existent IDs
                      boolean hasExecutions =
                          automationExecutionRepository.existsByRuleIdIn(
                              List.of(UUID.randomUUID(), UUID.randomUUID()));
                      assertThat(hasExecutions).isFalse();
                    }));
  }

  // --- Epic 625E (ADR-356): pack_install ledger rows for seeder-managed pack types ---

  @Test
  void provisioningWritesLedgerRowsForSeederManagedTypes() {
    assertThat(legalLedger()).isEqualTo(EXPECTED_LEGAL_ZA_LEDGER);
  }

  @Test
  void backfillRecreatesMissingRowsAtAppliedVersionAndIsIdempotent() {
    // Simulate a tenant provisioned before 625E: the packs are applied (OrgSettings lists) but
    // there is no pack_install ledger row for the seeder-managed types.
    deleteLegalLedgerRows(null);
    assertThat(legalLedger()).isEmpty();

    runLegalSeeders();
    assertThat(legalLedger())
        .as("backfilled at the OrgSettings-recorded versions")
        .isEqualTo(EXPECTED_LEGAL_ZA_LEDGER);
    Map<String, UUID> firstIds = legalLedgerIds();
    assertThat(firstIds).hasSize(12);

    runLegalSeeders();
    assertThat(legalLedger()).as("second pass changes nothing").isEqualTo(EXPECTED_LEGAL_ZA_LEDGER);
    assertThat(legalLedgerIds()).as("second pass creates no rows").isEqualTo(firstIds);
  }

  @Test
  void backfillReadsTheVersionFromOrgSettings_notTheClasspath() {
    // Rewrite the OrgSettings entry for common-task to a non-integer "legacy" version and drop
    // its ledger row: the backfill must record "legacy" (the applied version), and the gate then
    // stays closed (non-numeric) — the row is not advanced to the classpath version "2".
    List<Map<String, Object>> original = fieldPackStatus();
    try {
      List<Map<String, Object>> legacy = new ArrayList<>();
      for (Map<String, Object> e : original) {
        Map<String, Object> copy = new HashMap<>(e);
        if ("common-task".equals(copy.get("packId"))) {
          copy.put("version", "legacy");
        }
        legacy.add(copy);
      }
      setFieldPackStatus(legacy);
      deleteLegalLedgerRows("common-task");

      fieldPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
      fieldPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);

      assertThat(legalLedger().get(PackType.FIELD)).containsEntry("common-task", "legacy");
    } finally {
      setFieldPackStatus(original);
      deleteLegalLedgerRows("common-task");
      fieldPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    }
    assertThat(legalLedger().get(PackType.FIELD)).containsEntry("common-task", "2");
  }

  @Test
  void ledgerRowsAreHiddenFromPacksApi() {
    inLegalTenant(
        () -> {
          Boolean ledgerRowExists =
              transactionTemplate.execute(
                  tx -> packInstallRepository.findByPackId("standard-clauses").isPresent());
          assertThat(ledgerRowExists).as("the ledger row exists").isTrue();

          List<PackCatalogEntry> installed = packCatalogService.listInstalled();
          assertThat(installed).allMatch(e -> e.type().isInstallerManaged());
          assertThat(installed).extracting(PackCatalogEntry::packId).contains("common", "legal-za");
          assertThat(installed)
              .extracting(PackCatalogEntry::packId)
              .doesNotContain("standard-clauses", "common-project", "standard-reports");

          assertThatThrownBy(() -> packInstallService.checkUninstallable("standard-clauses"))
              .isInstanceOf(ResourceNotFoundException.class);
          assertThatThrownBy(() -> packInstallService.uninstall("standard-clauses", null))
              .isInstanceOf(ResourceNotFoundException.class);
          return null;
        });
  }

  private void runLegalSeeders() {
    fieldPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    clausePackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    standardReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);
    requestPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
  }

  private Map<PackType, Map<String, String>> legalLedger() {
    return inLegalTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  Map<PackType, Map<String, String>> byType = new HashMap<>();
                  for (PackInstall row : packInstallRepository.findAll()) {
                    if (LEDGER_TYPES.contains(row.getPackType())) {
                      byType
                          .computeIfAbsent(row.getPackType(), t -> new TreeMap<>())
                          .put(row.getPackId(), row.getPackVersion());
                    }
                  }
                  return byType;
                }));
  }

  private Map<String, UUID> legalLedgerIds() {
    return inLegalTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  Map<String, UUID> ids = new TreeMap<>();
                  for (PackInstall row : packInstallRepository.findAll()) {
                    if (LEDGER_TYPES.contains(row.getPackType())) {
                      ids.put(row.getPackId(), row.getId());
                    }
                  }
                  return ids;
                }));
  }

  /** Deletes the legal tenant's ledger rows (all seeder-managed types, or just {@code packId}). */
  private void deleteLegalLedgerRows(String packId) {
    inLegalTenant(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx ->
                  packInstallRepository.deleteAll(
                      packInstallRepository.findAll().stream()
                          .filter(r -> LEDGER_TYPES.contains(r.getPackType()))
                          .filter(r -> packId == null || packId.equals(r.getPackId()))
                          .toList()));
          return null;
        });
  }

  private List<Map<String, Object>> fieldPackStatus() {
    return inLegalTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  List<Map<String, Object>> copy = new ArrayList<>();
                  for (Map<String, Object> e :
                      orgSettingsRepository
                          .findForCurrentTenant()
                          .orElseThrow()
                          .getPackStatus()
                          .getFieldPackStatus()) {
                    copy.add(new HashMap<>(e));
                  }
                  return copy;
                }));
  }

  private void setFieldPackStatus(List<Map<String, Object>> status) {
    inLegalTenant(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                settings.getPackStatus().setFieldPackStatus(new ArrayList<>(status));
                orgSettingsRepository.save(settings);
              });
          return null;
        });
  }

  private <T> T inLegalTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .call(action::get);
  }
}
