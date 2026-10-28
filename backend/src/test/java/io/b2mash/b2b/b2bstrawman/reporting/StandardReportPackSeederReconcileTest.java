package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Hibernate test of the ADR-356 {@code report_definition} resolver (628C, U21 terminology):
 * the Java-defined {@link StandardReportPackSeeder#RECONCILE} block (v1 → v2) reaches an existing
 * tenant through {@code seedForTenant} → version gate → {@code resolveReconcileTarget}. {@code
 * name} / {@code description} use string equality; {@code parameterSchema} / {@code
 * columnDefinitions} / {@code templateBody} use whole-value {@code sha256:} equality, so this test
 * also proves the JSONB round-trip: rows are rewritten to the v1 values ({@link StandardReportsV1},
 * a verbatim copy of the v1 factories), committed, and re-read by the seeder's own transaction
 * before the one rule hashes them.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StandardReportPackSeederReconcileTest {

  private static final String ORG_ID = "org_report_pack_reconcile_test";
  static final List<String> SLUGS = List.of("timesheet", "invoice-aging", "project-profitability");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private StandardReportPackSeeder standardReportPackSeeder;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Report Pack Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void pristineV1Reports_areReconciledToV2() {
    simulateV1(null, true);

    var logs = captureLogs(() -> standardReportPackSeeder.seedForTenant(schema, ORG_ID));

    inTenantTx(
        () -> {
          var aging = report("invoice-aging");
          assertThat(aging.getName()).isEqualTo("Fee Note Aging Report");
          assertThat(aging.getDescription())
              .isEqualTo("Outstanding fee notes grouped by age bucket.");
          assertThat(paramLabel(aging, "customerId")).isEqualTo("Client");
          assertThat(columnLabel(aging, "invoiceNumber")).isEqualTo("Fee Note #");
          assertThat(columnLabel(aging, "customerName")).isEqualTo("Client");

          var profitability = report("project-profitability");
          assertThat(profitability.getName()).isEqualTo("Matter Profitability Report");
          assertThat(profitability.getDescription())
              .isEqualTo("Revenue vs. cost per matter for a date range.");
          assertThat(paramLabel(profitability, "projectId")).isEqualTo("Matter");
          assertThat(paramLabel(profitability, "customerId")).isEqualTo("Client");
          assertThat(columnLabel(profitability, "projectName")).isEqualTo("Matter");
          assertThat(columnLabel(profitability, "customerName")).isEqualTo("Client");

          var timesheet = report("timesheet");
          assertThat(timesheet.getName()).isEqualTo("Timesheet Report");
          assertThat(timesheet.getDescription())
              .isEqualTo("Time entries grouped by member, matter, or date for a given period.");
          assertThat(paramLabel(timesheet, "projectId")).isEqualTo("Matter");
          assertThat(param(timesheet, "groupBy"))
              .containsEntry("options", List.of("member", "project", "date"))
              .containsEntry(
                  "optionLabels", Map.of("member", "Member", "project", "Matter", "date", "Date"));

          for (String slug : SLUGS) {
            var row = report(slug);
            var shipped = StandardReportPackSeeder.shipped(slug);
            assertThat(row.getTemplateBody()).as(slug).isEqualTo(shipped.getTemplateBody());
            assertThat(ContentHashUtil.hashOf(row.getParameterSchema()))
                .as("%s parameterSchema", slug)
                .isEqualTo(ContentHashUtil.hashOf(shipped.getParameterSchema()));
            assertThat(ContentHashUtil.hashOf(row.getColumnDefinitions()))
                .as("%s columnDefinitions", slug)
                .isEqualTo(ContentHashUtil.hashOf(shipped.getColumnDefinitions()));
          }
          assertThat(ledgerVersion()).isEqualTo("2");
          return null;
        });
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile skipped"));
    assertThat(logs)
        .filteredOn(m -> m.startsWith("Reconcile applied"))
        .hasSize(StandardReportPackSeeder.RECONCILE.size());
  }

  @Test
  void renamedReport_keepsName_restReconciled() {
    simulateV1("Our aging report", true);

    var logs = captureLogs(() -> standardReportPackSeeder.seedForTenant(schema, ORG_ID));

    inTenantTx(
        () -> {
          var aging = report("invoice-aging");
          assertThat(aging.getName()).isEqualTo("Our aging report");
          assertThat(aging.getDescription())
              .isEqualTo("Outstanding fee notes grouped by age bucket.");
          assertThat(columnLabel(aging, "invoiceNumber")).isEqualTo("Fee Note #");
          assertThat(report("project-profitability").getName())
              .isEqualTo("Matter Profitability Report");
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=standard-reports")
                    && m.contains("report_definition 'invoice-aging' field name"));
  }

  @Test
  void equalVersion_isANoOp() {
    simulateV1(null, false);

    standardReportPackSeeder.seedForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          for (String slug : SLUGS) {
            var v1 = StandardReportsV1.bySlug(slug);
            var row = report(slug);
            assertThat(row.getName()).as(slug).isEqualTo(v1.getName());
            assertThat(row.getDescription()).as(slug).isEqualTo(v1.getDescription());
            assertThat(row.getTemplateBody()).as(slug).isEqualTo(v1.getTemplateBody());
          }
          return null;
        });
    // restore the shipped values for sibling tests
    simulateV1(null, true);
    standardReportPackSeeder.seedForTenant(schema, ORG_ID);
  }

  /**
   * Guard (no tenant needed): the Java {@code RECONCILE} block parses; every {@code from} is the v1
   * value (string) or the {@code sha256:} of the v1 value ({@link StandardReportsV1}); every {@code
   * to} is the shipped value or its hash — a factory edit without updating {@code to} fails here,
   * not at a tenant's boot.
   */
  @Test
  void shippedReconcile_fromIsV1_toIsShipped() {
    assertThat(StandardReportPackSeeder.PACK_VERSION).isEqualTo(2);
    var entries =
        PackReconcileEntry.parse(
            StandardReportPackSeeder.PACK_ID, StandardReportPackSeeder.RECONCILE);
    assertThat(entries).isNotEmpty();
    for (PackReconcileEntry e : entries) {
      String where = e.key() + "." + e.field();
      assertThat(e.entity()).as(where).isEqualTo("report_definition");
      assertThat(e.key()).as(where).isIn(SLUGS);
      var v1 = StandardReportsV1.bySlug(e.key());
      var shipped = StandardReportPackSeeder.shipped(e.key());
      switch (e.field()) {
        case "name" -> {
          assertThat(e.from()).as(where).isEqualTo(v1.getName());
          assertThat(e.to()).as(where).isEqualTo(shipped.getName());
        }
        case "description" -> {
          assertThat(e.from()).as(where).isEqualTo(v1.getDescription());
          assertThat(e.to()).as(where).isEqualTo(shipped.getDescription());
        }
        case "parameterSchema" -> {
          assertThat(e.from()).as(where).isEqualTo(hashRef(v1.getParameterSchema()));
          assertThat(e.to()).as(where).isEqualTo(hashRef(shipped.getParameterSchema()));
        }
        case "columnDefinitions" -> {
          assertThat(e.from()).as(where).isEqualTo(hashRef(v1.getColumnDefinitions()));
          assertThat(e.to()).as(where).isEqualTo(hashRef(shipped.getColumnDefinitions()));
        }
        case "templateBody" -> {
          assertThat(e.from()).as(where).isEqualTo(hashRef(v1.getTemplateBody()));
          assertThat(e.to()).as(where).isEqualTo(hashRef(shipped.getTemplateBody()));
        }
        default -> throw new AssertionError("Unexpected field " + where);
      }
      assertThat(e.to()).as(where).isNotEqualTo(e.from());
    }
    // every v1 -> shipped difference is covered by an entry (nothing left stale on old tenants)
    for (String slug : SLUGS) {
      var v1 = StandardReportsV1.bySlug(slug);
      var shipped = StandardReportPackSeeder.shipped(slug);
      assertCovered(entries, slug, "name", v1.getName(), shipped.getName());
      assertCovered(entries, slug, "description", v1.getDescription(), shipped.getDescription());
      assertCovered(
          entries,
          slug,
          "parameterSchema",
          hashRef(v1.getParameterSchema()),
          hashRef(shipped.getParameterSchema()));
      assertCovered(
          entries,
          slug,
          "columnDefinitions",
          hashRef(v1.getColumnDefinitions()),
          hashRef(shipped.getColumnDefinitions()));
      assertCovered(
          entries,
          slug,
          "templateBody",
          hashRef(v1.getTemplateBody()),
          hashRef(shipped.getTemplateBody()));
      assertThat(shipped.getSlug()).isEqualTo(v1.getSlug());
      assertThat(shipped.getCategory()).isEqualTo(v1.getCategory());
    }
  }

  // --- helpers ---

  private static void assertCovered(
      List<PackReconcileEntry> entries, String slug, String field, Object v1, Object shipped) {
    boolean hasEntry =
        entries.stream().anyMatch(e -> slug.equals(e.key()) && field.equals(e.field()));
    assertThat(hasEntry)
        .as("%s.%s changed v1 -> shipped, so it needs a reconcile entry", slug, field)
        .isEqualTo(!v1.equals(shipped));
  }

  /**
   * Rewrites every standard report to its exact v1 values (or, for {@code invoice-aging}, a firm
   * name when {@code agingName} is set), committed in its own transaction; when {@code rewind},
   * rewinds the ledger 2 → 1.
   */
  private void simulateV1(String agingName, boolean rewind) {
    inTenantTx(
        () -> {
          for (String slug : SLUGS) {
            var v1 = StandardReportsV1.bySlug(slug);
            var row = report(slug);
            row.rename(
                agingName != null && "invoice-aging".equals(slug) ? agingName : v1.getName());
            row.setDescription(v1.getDescription());
            row.updateDefinition(
                v1.getTemplateBody(), v1.getParameterSchema(), v1.getColumnDefinitions());
            reportDefinitionRepository.save(row);
          }
          if (rewind) {
            assertThat(
                    packInstallRepository.compareAndSetVersion(
                        StandardReportPackSeeder.PACK_ID, "2", "1"))
                .as("rewind standard-reports 2 -> 1")
                .isEqualTo(1);
          }
          return null;
        });
  }

  private ReportDefinition report(String slug) {
    return reportDefinitionRepository.findBySlug(slug).orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> param(ReportDefinition def, String name) {
    return ((List<Map<String, Object>>) def.getParameterSchema().get("parameters"))
        .stream()
            .filter(p -> name.equals(p.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(def.getSlug() + " has no parameter " + name));
  }

  private static Object paramLabel(ReportDefinition def, String name) {
    return param(def, name).get("label");
  }

  @SuppressWarnings("unchecked")
  private static Object columnLabel(ReportDefinition def, String key) {
    return ((List<Map<String, Object>>) def.getColumnDefinitions().get("columns"))
        .stream()
            .filter(c -> key.equals(c.get("key")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(def.getSlug() + " has no column " + key))
            .get("label");
  }

  private static String hashRef(Object value) {
    return PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(value);
  }

  private String ledgerVersion() {
    return packInstallRepository
        .findByPackId(StandardReportPackSeeder.PACK_ID)
        .orElseThrow()
        .getPackVersion();
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(StandardReportPackSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
