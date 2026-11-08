package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstall;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportDefinition;
import io.b2mash.b2b.b2bstrawman.reporting.ReportDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F40 (Phase 89 630A): the Section 35 trust report pack reaches legal-za tenants through the
 * real provisioning path, on the 625E shape (ADR-356), and never reaches profile-less tenants.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrustReportPackSeederIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_trust_report_pack_legal";
  private static final String PLAIN_ORG_ID = "org_trust_report_pack_plain";

  private static final List<String> TRUST_SLUGS =
      List.of(
          "trust-receipts-payments",
          "client-trust-balances",
          "client-ledger-statement",
          "trust-reconciliation",
          "investment-register",
          "interest-allocation",
          "section-35-data-pack");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustReportPackSeeder trustReportPackSeeder;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private String plainSchema;

  @BeforeAll
  void provision() {
    legalSchema =
        provisioningService
            .provisionTenant(LEGAL_ORG_ID, "Trust Report Pack Legal Org", "legal-za")
            .schemaName();
    plainSchema =
        provisioningService
            .provisionTenant(PLAIN_ORG_ID, "Trust Report Pack Plain Org", null)
            .schemaName();
  }

  /** G88-F40 reproduction: at HEAD the seeder had no caller, so a fresh legal-za tenant had 0. */
  @Test
  @Order(1)
  void freshLegalZaTenant_hasSevenTrustDefinitionsAndLedgerRow() {
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          List<ReportDefinition> trust = trustDefinitions();
          assertThat(trust).hasSize(7);
          assertThat(trust)
              .extracting(ReportDefinition::getSlug)
              .containsExactlyInAnyOrderElementsOf(TRUST_SLUGS);
          assertThat(trust).allMatch(ReportDefinition::isSystem);

          PackInstall row = packInstallRepository.findByPackId("trust-reports").orElseThrow();
          assertThat(row.getPackType()).isEqualTo(PackType.REPORT);
          // G89-F48: v3 — a fresh tenant installs at the head version.
          assertThat(row.getPackVersion()).isEqualTo("3");

          assertThat(trustPackStatus())
              .singleElement()
              .satisfies(e -> assertThat(((Number) e.get("version")).intValue()).isEqualTo(3));
          return null;
        });
  }

  /** A profile-less tenant gets nothing — even when the seeder is invoked directly. */
  @Test
  @Order(2)
  void profileLessTenant_seederIsGatedOff() {
    trustReportPackSeeder.seedForTenant(plainSchema, PLAIN_ORG_ID);
    in(
        plainSchema,
        PLAIN_ORG_ID,
        () -> {
          assertThat(trustDefinitions()).isEmpty();
          assertThat(packInstallRepository.findByPackId("trust-reports")).isEmpty();
          assertThat(trustPackStatus()).isEmpty();
          return null;
        });
  }

  /** Second pass: no duplicate rows, no second status entry, ledger row untouched (v3 gate). */
  @Test
  @Order(3)
  void secondPass_isANoOp() {
    PackInstall before =
        in(
            legalSchema,
            LEGAL_ORG_ID,
            () -> packInstallRepository.findByPackId("trust-reports").orElseThrow());

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);
    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(trustDefinitions()).hasSize(7);
          assertThat(trustPackStatus()).hasSize(1);
          PackInstall after = packInstallRepository.findByPackId("trust-reports").orElseThrow();
          assertThat(after.getId()).isEqualTo(before.getId());
          assertThat(after.getPackVersion()).isEqualTo("3");
          return null;
        });
  }

  /** The 625E reconcile shape is live: the resolver targets shipped system rows, never others. */
  @Test
  @Order(4)
  void reconcileResolver_targetsShippedSystemRowsOnly() {
    assertThat(TrustReportPackSeeder.RECONCILE).isNotEmpty();
    assertThat(TrustReportPackSeeder.SLUGS).containsExactlyElementsOf(TRUST_SLUGS);
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          String shippedName = TrustReportPackSeeder.shipped("section-35-data-pack").getName();
          assertThat(
                  trustReportPackSeeder.resolveReconcileTarget(
                      new PackReconcileEntry(
                          TrustReportPackSeeder.REPORT_ENTITY,
                          "section-35-data-pack",
                          "name",
                          "Old name",
                          shippedName)))
              .isPresent()
              .get()
              .satisfies(t -> assertThat(t.currentValue()).isEqualTo(shippedName));
          assertThat(
                  trustReportPackSeeder.resolveReconcileTarget(
                      new PackReconcileEntry(
                          TrustReportPackSeeder.REPORT_ENTITY, "no-such-report", "name", "x", "y")))
              .isEmpty();
          return null;
        });
  }

  // ==========================================================================
  // G89-F18 — v2: the Section 35 data pack template prints each section's rows.
  // ==========================================================================

  /**
   * Guard (no tenant needed): the v3 block is the ADR-356 cumulative chain for the section-35
   * template body — one entry from the verbatim v1 copy ({@link TrustReportsV1}) and one from the
   * verbatim v2 copy ({@link TrustReportsV2}), both to the shipped body. {@code to} must be the
   * head or the resolver WARN-skips it at every tenant's boot.
   */
  @Test
  @Order(5)
  void reconcileBlock_movesSection35TemplateFromV1AndV2ToShipped() {
    assertThat(TrustReportPackSeeder.PACK_VERSION).isEqualTo(3);
    String head = hashRef(TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody());
    var entries =
        PackReconcileEntry.parse(TrustReportPackSeeder.PACK_ID, TrustReportPackSeeder.RECONCILE);
    assertThat(entries)
        .allSatisfy(
            e -> {
              assertThat(e.entity()).isEqualTo(TrustReportPackSeeder.REPORT_ENTITY);
              assertThat(e.key()).isEqualTo("section-35-data-pack");
              assertThat(e.field()).isEqualTo("templateBody");
              assertThat(e.to()).isEqualTo(head);
            })
        .extracting(PackReconcileEntry::from)
        .containsExactly(
            hashRef(TrustReportsV1.SECTION_35_TEMPLATE),
            hashRef(TrustReportsV2.SECTION_35_TEMPLATE));
  }

  /** Installed at v1 (v1 body, ledger at 1) → the next pass reconciles the body to v3. */
  @Test
  @Order(6)
  void v1Section35Template_isReconciledToV3() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35(TrustReportsV1.SECTION_35_TEMPLATE, "1");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(section35().getTemplateBody()).isEqualTo(shippedBody);
          assertThat(packInstallRepository.findByPackId("trust-reports").orElseThrow())
              .extracting(PackInstall::getPackVersion)
              .isEqualTo("3");
          return null;
        });

    // Equal version is a no-op: an edit made after the reconcile is not overwritten.
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var row = section35();
          row.updateTemplate("<html>edited at v3</html>");
          reportDefinitionRepository.save(row);
          return null;
        });
    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(section35().getTemplateBody()).isEqualTo("<html>edited at v3</html>");
          var row = section35();
          row.updateTemplate(shippedBody);
          reportDefinitionRepository.save(row);
          return null;
        });
  }

  /** G89-F48: installed at v2 (v2 body, ledger at 2) → the next pass reconciles to v3. */
  @Test
  @Order(7)
  void v2Section35Template_isReconciledToV3() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35(TrustReportsV2.SECTION_35_TEMPLATE, "2");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(section35().getTemplateBody()).isEqualTo(shippedBody);
          assertThat(shippedBody).contains("'WHITESPACE', 2, 'COMMA'");
          assertThat(packInstallRepository.findByPackId("trust-reports").orElseThrow())
              .extracting(PackInstall::getPackVersion)
              .isEqualTo("3");
          return null;
        });
  }

  /** One rule: a body that is neither a prior body nor the head is left alone. */
  @Test
  @Order(8)
  void editedSection35Template_survivesReconcile() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35("<html>firm edit</html>", "2");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(section35().getTemplateBody()).isEqualTo("<html>firm edit</html>");
          assertThat(packInstallRepository.findByPackId("trust-reports").orElseThrow())
              .extracting(PackInstall::getPackVersion)
              .isEqualTo("3");
          var row = section35();
          row.updateTemplate(shippedBody);
          reportDefinitionRepository.save(row);
          return null;
        });
  }

  /** Sets the section-35 body to {@code body} and rewinds the ledger 3 → {@code version}. */
  private void simulateInstalledSection35(String body, String version) {
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var row = section35();
          row.updateTemplate(body);
          reportDefinitionRepository.save(row);
          assertThat(
                  packInstallRepository.compareAndSetVersion(
                      TrustReportPackSeeder.PACK_ID, "3", version))
              .as("rewind trust-reports 3 -> %s", version)
              .isEqualTo(1);
          return null;
        });
  }

  private ReportDefinition section35() {
    return reportDefinitionRepository.findBySlug("section-35-data-pack").orElseThrow();
  }

  private static String hashRef(Object value) {
    return PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(value);
  }

  private List<ReportDefinition> trustDefinitions() {
    return reportDefinitionRepository.findAll().stream()
        .filter(d -> "TRUST".equals(d.getCategory()))
        .toList();
  }

  private List<Map<String, Object>> trustPackStatus() {
    var status =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseThrow()
            .getPackStatus()
            .getReportPackStatus();
    return status == null
        ? List.of()
        : status.stream().filter(e -> "trust-reports".equals(e.get("packId"))).toList();
  }

  private <R> R in(String schema, String orgId, Supplier<R> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .call(() -> transactionTemplate.execute(tx -> action.get()));
  }
}
