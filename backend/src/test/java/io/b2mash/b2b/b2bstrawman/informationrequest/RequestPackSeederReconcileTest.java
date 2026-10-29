package io.b2mash.b2b.b2bstrawman.informationrequest;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit test for the ADR-356 {@code request_template} resolver of {@link RequestPackSeeder} (628C):
 * {@code key} is the pack id (one template per request pack), {@code field} is {@code name} or
 * {@code description} (string equality), the target is the pack's single {@code PLATFORM} row. No
 * shipped request pack carries a {@code reconcile} block yet (zero legacy text), so a synthetic
 * pack drives the resolver.
 */
class RequestPackSeederReconcileTest {

  static final String PACK_ID = "synthetic-request-pack";
  static final String OLD_NAME = "Old Intake Pack";
  static final String NEW_NAME = "New Intake Pack";
  static final String OLD_DESC = "Old description";
  static final String NEW_DESC = "New description";

  private RequestTemplateRepository repository;
  private RequestPackSeeder seeder;

  @BeforeEach
  void setUp() {
    repository = mock(RequestTemplateRepository.class);
    seeder =
        new RequestPackSeeder(
            mock(ResourcePatternResolver.class),
            new ObjectMapper(),
            repository,
            mock(RequestTemplateItemRepository.class),
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class),
            mock(PackVersionGate.class));
  }

  @Test
  void pristinePlatformRow_isRenamedAndReworded() {
    var row = platformRow(OLD_NAME, OLD_DESC);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(row));

    var logs =
        captureLogs(() -> seeder.reconcileInstalledPack(pack(nameEntry(), descEntry()), null, "t"));

    assertThat(row.getName()).isEqualTo(NEW_NAME);
    assertThat(row.getDescription()).isEqualTo(NEW_DESC);
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile applied")).hasSize(2);
  }

  @Test
  void firmEditedName_isLeftAlone_descriptionStillReconciled() {
    var row = platformRow("Our own intake", OLD_DESC);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(row));

    var logs =
        captureLogs(() -> seeder.reconcileInstalledPack(pack(nameEntry(), descEntry()), null, "t"));

    assertThat(row.getName()).isEqualTo("Our own intake");
    assertThat(row.getDescription()).isEqualTo(NEW_DESC);
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("request_template '" + PACK_ID + "' field name"));
  }

  @Test
  void customRow_isNeverResolved() {
    // Worst case: a CUSTOM row that carries the pack id (duplicates normally have none).
    var custom = new RequestTemplate(OLD_NAME, OLD_DESC, TemplateSource.CUSTOM);
    custom.setPackId(PACK_ID);
    var platform = platformRow(OLD_NAME, OLD_DESC);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(custom, platform));

    seeder.reconcileInstalledPack(pack(nameEntry(), descEntry()), null, "t");

    assertThat(custom.getName()).isEqualTo(OLD_NAME);
    assertThat(custom.getDescription()).isEqualTo(OLD_DESC);
    assertThat(platform.getName()).isEqualTo(NEW_NAME);
  }

  @Test
  void onlyCustomRows_resolveNoTarget() {
    var custom = new RequestTemplate(OLD_NAME, OLD_DESC, TemplateSource.CUSTOM);
    custom.setPackId(PACK_ID);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(custom));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(pack(nameEntry()), null, "t"));

    assertThat(custom.getName()).isEqualTo(OLD_NAME);
    assertThat(logs).anyMatch(m -> m.startsWith("Reconcile skipped (no pack-seeded row)"));
  }

  @Test
  void twoPlatformRows_areAmbiguous_warnAndWriteNeither() {
    var a = platformRow(OLD_NAME, OLD_DESC);
    var b = platformRow(OLD_NAME, OLD_DESC);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(a, b));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(pack(nameEntry()), null, "t"));

    assertThat(a.getName()).isEqualTo(OLD_NAME);
    assertThat(b.getName()).isEqualTo(OLD_NAME);
    assertThat(logs).anyMatch(m -> m.startsWith("Reconcile entry ignored") && m.contains("ambig"));
  }

  @Test
  void unsupportedEntries_warnAndNeverThrow() {
    var row = platformRow(OLD_NAME, OLD_DESC);
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(row));
    var pack =
        pack(
            new PackReconcileEntry("request_template", "other-pack", "name", OLD_NAME, NEW_NAME),
            new PackReconcileEntry("request_template", PACK_ID, "items", OLD_NAME, NEW_NAME),
            new PackReconcileEntry("request_template", PACK_ID, "name", OLD_NAME, 7),
            new PackReconcileEntry("request_template", PACK_ID, "name", OLD_NAME, "Not shipped"),
            new PackReconcileEntry("request_item", PACK_ID, "name", OLD_NAME, NEW_NAME));

    var logs = new ArrayList<String>();
    assertThatCode(
            () -> logs.addAll(captureLogs(() -> seeder.reconcileInstalledPack(pack, null, "t"))))
        .doesNotThrowAnyException();

    assertThat(row.getName()).isEqualTo(OLD_NAME);
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(5);
  }

  @Test
  void rowAlreadyAtTheShippedValue_isNotWritten() {
    var row = platformRow(NEW_NAME, NEW_DESC);
    var before = row.getUpdatedAt();
    when(repository.findByPackId(PACK_ID)).thenReturn(List.of(row));

    var logs =
        captureLogs(() -> seeder.reconcileInstalledPack(pack(nameEntry(), descEntry()), null, "t"));

    assertThat(row.getUpdatedAt()).isEqualTo(before);
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile applied"));
  }

  // --- helpers ---

  private static PackReconcileEntry nameEntry() {
    return new PackReconcileEntry("request_template", PACK_ID, "name", OLD_NAME, NEW_NAME);
  }

  private static PackReconcileEntry descEntry() {
    return new PackReconcileEntry("request_template", PACK_ID, "description", OLD_DESC, NEW_DESC);
  }

  private static RequestPackDefinition pack(PackReconcileEntry... entries) {
    return new RequestPackDefinition(
        PACK_ID, 2, "legal-za", NEW_NAME, NEW_DESC, List.of(), List.of(entries));
  }

  private static RequestTemplate platformRow(String name, String description) {
    var row = new RequestTemplate(name, description, TemplateSource.PLATFORM);
    row.setPackId(PACK_ID);
    return row;
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(RequestPackSeeder.class);
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
