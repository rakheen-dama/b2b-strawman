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
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F40 (Phase 89 630A): the trust report pack reaches legal-za tenants through the real
 * provisioning path, on the 625E shape (ADR-356), and never reaches profile-less tenants. Phase 92
 * 650C: the pack is at v4 — nine reports; an installed v3 pack gains the three new reports, loses
 * {@code interest-allocation} and has every shipped v3 field moved to the head, while firm edits
 * survive.
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
          "trust-position",
          "client-ledger-statement",
          "trust-reconciliation",
          "investment-register",
          "trust-interest-charges-schedule",
          "unidentified-deposits-age-analysis",
          "section-35-data-pack");

  /** The reports v4 adds to an installed pack. */
  private static final List<String> ADDED =
      List.of(
          "trust-position",
          "trust-interest-charges-schedule",
          "unidentified-deposits-age-analysis");

  private static final String RETIRED_SLUG = "interest-allocation";

  /** The six reports shipped at v3 and v4. */
  private static final List<String> SURVIVORS =
      TrustReportsV3.SLUGS.stream().filter(s -> !RETIRED_SLUG.equals(s)).toList();

  private static final List<String> FIELDS =
      List.of("name", "description", "parameterSchema", "columnDefinitions", "templateBody");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustReportPackSeeder trustReportPackSeeder;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

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

  /** A fresh legal-za tenant gets the nine v4 reports (no interest-allocation) at v4. */
  @Test
  @Order(1)
  void freshLegalZaTenant_hasNineTrustDefinitionsAndLedgerRow() {
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          List<ReportDefinition> trust = trustDefinitions();
          assertThat(trust).hasSize(9);
          assertThat(trust)
              .extracting(ReportDefinition::getSlug)
              .containsExactlyInAnyOrderElementsOf(TRUST_SLUGS)
              .doesNotContain(RETIRED_SLUG);
          assertThat(trust).allMatch(ReportDefinition::isSystem);
          for (String slug : TRUST_SLUGS) {
            assertEqualsShipped(report(slug), slug);
          }

          PackInstall row = packInstallRepository.findByPackId("trust-reports").orElseThrow();
          assertThat(row.getPackType()).isEqualTo(PackType.REPORT);
          assertThat(row.getPackVersion()).isEqualTo("4");

          assertThat(trustPackStatus())
              .singleElement()
              .satisfies(e -> assertThat(((Number) e.get("version")).intValue()).isEqualTo(4));
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

  /** Second pass: no duplicate rows, no second status entry, ledger row untouched (v4 gate). */
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
          assertThat(trustDefinitions()).hasSize(9);
          assertThat(trustPackStatus()).hasSize(1);
          PackInstall after = packInstallRepository.findByPackId("trust-reports").orElseThrow();
          assertThat(after.getId()).isEqualTo(before.getId());
          assertThat(after.getPackVersion()).isEqualTo("4");
          return null;
        });
  }

  /** The 625E reconcile shape is live: the resolver targets shipped system rows, never others. */
  @Test
  @Order(4)
  void reconcileResolver_targetsShippedSystemRowsOnly() {
    assertThat(TrustReportPackSeeder.RECONCILE).isNotEmpty();
    assertThat(TrustReportPackSeeder.SLUGS).containsExactlyElementsOf(TRUST_SLUGS);
    assertThat(TrustReportPackSeeder.shipped(RETIRED_SLUG)).isNull();
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

  /**
   * Guard (no tenant needed): the v4 block is exactly three adds and one retire, then the ADR-356
   * cumulative chain — for every (survivor, field) one entry from each prior shipped value that
   * differs from the head ({@link TrustReportsV3} for every field; {@link TrustReportsV1} / {@link
   * TrustReportsV2} for the section-35 body), each {@code to} the head. A {@code to} that is not
   * the head is WARN-skipped at every tenant's boot, so this pins every literal.
   */
  @Test
  @Order(5)
  void reconcileBlock_isMembershipThenTheCumulativeFieldChain() {
    assertThat(TrustReportPackSeeder.PACK_VERSION).isEqualTo(4);
    var entries =
        PackReconcileEntry.parse(TrustReportPackSeeder.PACK_ID, TrustReportPackSeeder.RECONCILE);
    assertThat(entries).allMatch(e -> TrustReportPackSeeder.REPORT_ENTITY.equals(e.entity()));

    var membership = entries.stream().filter(TrustReportPackSeeder::isMembership).toList();
    var expectedMembership = new ArrayList<String>();
    ADDED.forEach(s -> expectedMembership.add(s + ": null -> trust-reports"));
    expectedMembership.add(RETIRED_SLUG + ": trust-reports -> retired");
    assertThat(membership)
        .extracting(e -> e.key() + ": " + e.from() + " -> " + e.to())
        .containsExactlyElementsOf(expectedMembership);
    // Membership entries come first (applied before every field entry)
    assertThat(entries.subList(0, membership.size())).containsExactlyElementsOf(membership);

    var fieldEntries =
        entries.stream()
            .filter(e -> !TrustReportPackSeeder.isMembership(e))
            .map(e -> e.key() + "." + e.field() + ": " + e.from() + " -> " + e.to())
            .toList();
    var expected = new ArrayList<String>();
    for (String slug : SURVIVORS) {
      for (String field : FIELDS) {
        Object head = value(TrustReportPackSeeder.shipped(slug), field);
        for (Object prior : priorValues(slug, field)) {
          if (!prior.equals(head)) {
            expected.add(slug + "." + field + ": " + prior + " -> " + head);
          }
        }
      }
    }
    assertThat(fieldEntries).containsExactlyInAnyOrderElementsOf(expected);
  }

  /**
   * OQ #10 (ruling #11): no foreign key references {@code report_definitions}, so the retired
   * {@code interest-allocation} row is deleted, not hidden. A future FK fails here and must be
   * re-decided (the retire step would have to hide the row instead).
   */
  @Test
  @Order(6)
  void noForeignKeyReferencesReportDefinitions_soTheRetiredRowIsDeleted() {
    Long fks =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM pg_constraint con
              JOIN pg_class dst ON dst.oid = con.confrelid
              JOIN pg_namespace ns ON ns.oid = dst.relnamespace
             WHERE con.contype = 'f' AND dst.relname = 'report_definitions'
               AND ns.nspname = ?""",
            Long.class,
            legalSchema);
    Long table =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
             WHERE c.relname = 'report_definitions' AND ns.nspname = ?""",
            Long.class,
            legalSchema);
    assertThat(table).as("precondition: the tenant schema has report_definitions").isEqualTo(1L);
    assertThat(fks).isZero();
  }

  /** Installed at v1 (v1 body, ledger at 1) → the next pass reconciles the body to the v4 head. */
  @Test
  @Order(7)
  void v1Section35Template_isReconciledToTheHead() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35(TrustReportsV1.SECTION_35_TEMPLATE, "1");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(report("section-35-data-pack").getTemplateBody()).isEqualTo(shippedBody);
          assertThat(ledgerVersion()).isEqualTo("4");
          assertThat(trustDefinitions()).hasSize(9);
          return null;
        });
  }

  /** G89-F48: installed at v2 (v2 body, ledger at 2) → the next pass reconciles to the head. */
  @Test
  @Order(8)
  void v2Section35Template_isReconciledToTheHead() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35(TrustReportsV2.SECTION_35_TEMPLATE, "2");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(report("section-35-data-pack").getTemplateBody()).isEqualTo(shippedBody);
          assertThat(shippedBody)
              .contains("'WHITESPACE', 2, 'COMMA'")
              .contains(TrustReportWords.TRUST_ASSETS_NOTE);
          assertThat(ledgerVersion()).isEqualTo("4");
          return null;
        });
  }

  /** One rule: a body that is neither a prior body nor the head is left alone. */
  @Test
  @Order(9)
  void editedSection35Template_survivesReconcile() {
    String shippedBody = TrustReportPackSeeder.shipped("section-35-data-pack").getTemplateBody();
    simulateInstalledSection35("<html>firm edit</html>", "2");

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(report("section-35-data-pack").getTemplateBody())
              .isEqualTo("<html>firm edit</html>");
          assertThat(ledgerVersion()).isEqualTo("4");
          var row = report("section-35-data-pack");
          row.updateTemplate(shippedBody);
          reportDefinitionRepository.save(row);
          return null;
        });
  }

  /**
   * §16.8.6: a tenant installed at v3 (the six survivors at their exact v3 values, an {@code
   * interest-allocation} row, none of the three new reports, ledger at 3) → the next pass creates
   * the three exactly as a fresh install does, deletes {@code interest-allocation}, and moves every
   * survivor field to the head.
   */
  @Test
  @Order(10)
  void installedAtV3_isReconciledToV4_membershipAndFields() {
    simulateInstalledV3();
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(reportDefinitionRepository.findBySlug(RETIRED_SLUG)).isPresent();
          assertThat(trustDefinitions()).hasSize(7);
          assertThat(report("client-trust-balances").getName()).isEqualTo("Client Trust Balances");
          return null;
        });

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(trustDefinitions())
              .extracting(ReportDefinition::getSlug)
              .containsExactlyInAnyOrderElementsOf(TRUST_SLUGS);
          assertThat(reportDefinitionRepository.findBySlug(RETIRED_SLUG)).isEmpty();
          for (String slug : TRUST_SLUGS) {
            assertEqualsShipped(report(slug), slug);
          }
          for (String slug : ADDED) {
            assertThat(report(slug).getSortOrder())
                .isEqualTo(TrustReportPackSeeder.shipped(slug).getSortOrder());
            assertThat(report(slug).isSystem()).isTrue();
          }
          assertThat(ledgerVersion()).isEqualTo("4");
          return null;
        });

    // Equal version is a no-op: an edit made at v4 is not overwritten, a deleted row not re-added
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var row = report("client-trust-balances");
          row.rename("Our creditors list");
          reportDefinitionRepository.save(row);
          return null;
        });
    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var row = report("client-trust-balances");
          assertThat(row.getName()).isEqualTo("Our creditors list");
          row.rename(TrustReportPackSeeder.shipped("client-trust-balances").getName());
          reportDefinitionRepository.save(row);
          return null;
        });
  }

  /**
   * One rule at 3 → 4: a firm-edited survivor field is left alone (the others still move); a row
   * the firm already owns under a new slug is never written; {@code interest-allocation} is deleted
   * even where the firm edited it (ruling #11 — its query no longer exists).
   */
  @Test
  @Order(11)
  void installedAtV3_firmEditsSurvive_butTheRetiredReportIsDeletedEvenIfEdited() {
    simulateInstalledV3();
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var receipts = report("trust-receipts-payments");
          receipts.updateTemplate("<html>firm edit</html>");
          reportDefinitionRepository.save(receipts);
          var retired = report(RETIRED_SLUG);
          retired.updateTemplate("<html>firm edit of interest allocation</html>");
          retired.rename("Our interest allocation");
          reportDefinitionRepository.save(retired);
          // A row already owning a new slug, created by the firm (not a system row), is left alone
          var position = TrustReportPackSeeder.shipped("trust-position");
          position.rename("Our trust position");
          reportDefinitionRepository.save(position);
          return null;
        });
    // No entity path creates a non-system report definition (is_system is fixed true by the
    // constructor), so the firm's row is made non-system in the table
    jdbcTemplate.update(
        "UPDATE \"%s\".report_definitions SET is_system = false WHERE slug = 'trust-position'"
            .formatted(legalSchema));
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          assertThat(report("trust-position").isSystem()).as("precondition: firm-owned").isFalse();
          return null;
        });

    trustReportPackSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var receipts = report("trust-receipts-payments");
          assertThat(receipts.getTemplateBody()).isEqualTo("<html>firm edit</html>");
          // The receipts report's other changed field still reached the head
          assertThat(receipts.getColumnDefinitions())
              .isEqualTo(
                  TrustReportPackSeeder.shipped("trust-receipts-payments").getColumnDefinitions());
          assertThat(reportDefinitionRepository.findBySlug(RETIRED_SLUG)).isEmpty();
          var firmPosition = report("trust-position");
          assertThat(firmPosition.getName()).isEqualTo("Our trust position");
          assertThat(firmPosition.isSystem()).as("still the firm's row").isFalse();
          for (String slug : List.of("client-trust-balances", "investment-register")) {
            assertEqualsShipped(report(slug), slug);
          }
          assertThat(trustDefinitions()).hasSize(9);
          assertThat(ledgerVersion()).isEqualTo("4");

          receipts.updateTemplate(
              TrustReportPackSeeder.shipped("trust-receipts-payments").getTemplateBody());
          reportDefinitionRepository.save(receipts);
          var position = report("trust-position");
          position.rename(TrustReportPackSeeder.shipped("trust-position").getName());
          reportDefinitionRepository.save(position);
          return null;
        });
    jdbcTemplate.update(
        "UPDATE \"%s\".report_definitions SET is_system = true WHERE slug = 'trust-position'"
            .formatted(legalSchema));
  }

  // --- helpers ---

  /**
   * Rewinds the tenant to an installed v3 pack: each survivor at its exact v3 values, the three v4
   * reports absent, a v3 {@code interest-allocation} row, ledger 4 → 3.
   */
  private void simulateInstalledV3() {
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          for (String slug : SURVIVORS) {
            var v3 = TrustReportsV3.bySlug(slug);
            var row = report(slug);
            row.rename(v3.getName());
            row.updateDescription(v3.getDescription());
            row.updateDefinition(
                v3.getTemplateBody(), v3.getParameterSchema(), v3.getColumnDefinitions());
            reportDefinitionRepository.save(row);
          }
          for (String slug : ADDED) {
            reportDefinitionRepository
                .findBySlug(slug)
                .ifPresent(reportDefinitionRepository::delete);
          }
          if (reportDefinitionRepository.findBySlug(RETIRED_SLUG).isEmpty()) {
            reportDefinitionRepository.save(TrustReportsV3.bySlug(RETIRED_SLUG));
          }
          assertThat(
                  packInstallRepository.compareAndSetVersion(
                      TrustReportPackSeeder.PACK_ID, "4", "3"))
              .as("rewind trust-reports 4 -> 3")
              .isEqualTo(1);
          return null;
        });
  }

  /** Sets the section-35 body to {@code body} and rewinds the ledger 4 → {@code version}. */
  private void simulateInstalledSection35(String body, String version) {
    in(
        legalSchema,
        LEGAL_ORG_ID,
        () -> {
          var row = report("section-35-data-pack");
          row.updateTemplate(body);
          reportDefinitionRepository.save(row);
          assertThat(
                  packInstallRepository.compareAndSetVersion(
                      TrustReportPackSeeder.PACK_ID, "4", version))
              .as("rewind trust-reports 4 -> %s", version)
              .isEqualTo(1);
          return null;
        });
  }

  /** The reconcile representation of each prior shipped value of (slug, field), oldest first. */
  private static List<Object> priorValues(String slug, String field) {
    var priors = new LinkedHashSet<Object>();
    if ("section-35-data-pack".equals(slug) && "templateBody".equals(field)) {
      priors.add(hashRef(TrustReportsV1.SECTION_35_TEMPLATE));
      priors.add(hashRef(TrustReportsV2.SECTION_35_TEMPLATE));
    }
    priors.add(value(TrustReportsV3.bySlug(slug), field));
    return List.copyOf(priors);
  }

  /** A field's reconcile representation: the string, or {@code sha256:} of a whole value. */
  private static Object value(ReportDefinition d, String field) {
    return switch (field) {
      case "name" -> d.getName();
      case "description" -> d.getDescription();
      case "parameterSchema" -> hashRef(d.getParameterSchema());
      case "columnDefinitions" -> hashRef(d.getColumnDefinitions());
      case "templateBody" -> hashRef(d.getTemplateBody());
      default -> throw new IllegalArgumentException(field);
    };
  }

  private static void assertEqualsShipped(ReportDefinition row, String slug) {
    var shipped = TrustReportPackSeeder.shipped(slug);
    assertThat(row.getName()).as("%s name", slug).isEqualTo(shipped.getName());
    assertThat(row.getDescription()).as("%s description", slug).isEqualTo(shipped.getDescription());
    assertThat(row.getParameterSchema())
        .as("%s parameterSchema", slug)
        .isEqualTo(shipped.getParameterSchema());
    assertThat(row.getColumnDefinitions())
        .as("%s columnDefinitions", slug)
        .isEqualTo(shipped.getColumnDefinitions());
    assertThat(row.getTemplateBody())
        .as("%s templateBody", slug)
        .isEqualTo(shipped.getTemplateBody());
  }

  private String ledgerVersion() {
    return packInstallRepository.findByPackId("trust-reports").orElseThrow().getPackVersion();
  }

  private ReportDefinition report(String slug) {
    return reportDefinitionRepository.findBySlug(slug).orElseThrow();
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
