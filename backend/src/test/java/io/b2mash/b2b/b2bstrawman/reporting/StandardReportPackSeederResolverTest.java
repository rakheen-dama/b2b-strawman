package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test (mocked repository) for the edges of {@link
 * StandardReportPackSeeder#resolveReconcileTarget} the integration test cannot reach: a non-system
 * row carrying a pack slug is never a target, and unsupported entries are WARN-logged and skipped,
 * never thrown. The applied / edited / equal-version paths are {@code
 * StandardReportPackSeederReconcileTest}.
 */
class StandardReportPackSeederResolverTest {

  private ReportDefinitionRepository repository;
  private StandardReportPackSeeder seeder;

  @BeforeEach
  void setUp() {
    repository = mock(ReportDefinitionRepository.class);
    seeder =
        new StandardReportPackSeeder(
            repository,
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class),
            mock(PackVersionGate.class));
  }

  @Test
  void nonSystemRowWithAPackSlug_isNeverWritten() {
    var rows = new ArrayList<ReportDefinition>();
    for (String slug : StandardReportPackSeederReconcileTest.SLUGS) {
      var v1 = StandardReportsV1.bySlug(slug);
      ReflectionTestUtils.setField(v1, "isSystem", false);
      when(repository.findBySlug(slug)).thenReturn(Optional.of(v1));
      rows.add(v1);
    }

    var logs = captureLogs(() -> seeder.reconcileExistingPack("t"));

    for (ReportDefinition row : rows) {
      var v1 = StandardReportsV1.bySlug(row.getSlug());
      assertThat(row.getName()).isEqualTo(v1.getName());
      assertThat(row.getDescription()).isEqualTo(v1.getDescription());
      assertThat(row.getTemplateBody()).isEqualTo(v1.getTemplateBody());
    }
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile applied"));
    assertThat(logs)
        .filteredOn(m -> m.startsWith("Reconcile skipped (no pack-seeded row)"))
        .hasSize(StandardReportPackSeeder.RECONCILE.size());
  }

  @Test
  void unsupportedEntries_warnAndNeverThrow() {
    var entries =
        List.of(
            new PackReconcileEntry("report_category", "timesheet", "name", "a", "Timesheet Report"),
            new PackReconcileEntry("report_definition", "no-such-report", "name", "a", "b"),
            new PackReconcileEntry("report_definition", "timesheet", "category", "a", "b"),
            new PackReconcileEntry("report_definition", "timesheet", "name", "a", 7),
            new PackReconcileEntry(
                "report_definition", "invoice-aging", "name", "a", "Not shipped"),
            new PackReconcileEntry(
                "report_definition",
                "invoice-aging",
                "templateBody",
                "a",
                PackReconcileEntry.HASH_PREFIX + "0".repeat(64)));

    var results = new ArrayList<Optional<PackReconcileEntry.Target>>();
    var logs = new ArrayList<String>();
    assertThatCode(
            () ->
                logs.addAll(
                    captureLogs(
                        () -> entries.forEach(e -> results.add(seeder.resolveReconcileTarget(e))))))
        .doesNotThrowAnyException();

    assertThat(results).allMatch(Optional::isEmpty);
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(6);
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
