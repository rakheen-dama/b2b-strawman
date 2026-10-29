package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstall;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit test for the 628C extension of {@link TemplatePackSeeder#resolveReconcileTarget}: {@code
 * document_template} {@code name} / {@code description} entries (string equality) beside the 626D
 * {@code content} entries, exercised with the real {@code template-packs/common/pack.json} (v4).
 * Repositories are mocked; the real-Hibernate path is {@code
 * packs.TemplatePackReconcileIntegrationTest#commonPackV3Names_areReconciledOnVersionBump_firmRenameSurvives}.
 */
class CommonTemplatePackReconcileTest {

  static final String PACK_ID = "common";
  static final String PACK_JSON = "template-packs/common/pack.json";
  static final String V3_FIXTURES = "fixtures/template-packs/common-v3/";

  /** The v3 ({@code fe579bdc9}) names / descriptions re-worded in v4. */
  static final Map<String, String[]> V3_NAME_AND_DESCRIPTION =
      Map.of(
          "project-summary",
          new String[] {"Project Summary Report", "Project summary report template"},
          "invoice-cover-letter",
          new String[] {"Invoice Cover Letter", "Cover letter for invoices"});

  /** Bodies re-worded in v4 (OD-2): one v3 → shipped {@code content} entry each. */
  static final List<String> CONTENT_KEYS =
      List.of("engagement-letter", "project-summary", "invoice-cover-letter");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final UUID installId = UUID.randomUUID();
  private final Resource packResource = new ClassPathResource(PACK_JSON);
  private DocumentTemplateRepository templateRepository;
  private TemplatePackSeeder seeder;
  private TemplatePackDefinition shippedPack;

  @BeforeEach
  void setUp() throws IOException {
    templateRepository = mock(DocumentTemplateRepository.class);
    PackInstallRepository packInstallRepository = mock(PackInstallRepository.class);
    var install =
        new PackInstall(
            PACK_ID, PackType.DOCUMENT_TEMPLATE, "4", "Common Templates", Instant.now(), null, 3);
    TestIds.withId(install, installId);
    when(packInstallRepository.findByPackId(PACK_ID)).thenReturn(Optional.of(install));

    seeder =
        new TemplatePackSeeder(
            mock(ResourcePatternResolver.class),
            JSON,
            templateRepository,
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class),
            mock(PackVersionGate.class),
            packInstallRepository);

    try (InputStream in = packResource.getInputStream()) {
      shippedPack = JSON.readValue(in, TemplatePackDefinition.class);
    }
  }

  // --- shipped-pack guard ---

  /**
   * Guard for the cumulative-chain convention ({@code backend/CLAUDE.md} › Pack reconcile
   * contract): every {@code to} is the head — the name / description / body hash shipped in the
   * pack — because the resolver WARN-skips any other {@code to}; every {@code from} is the value of
   * a prior version (today only v3, the first version with entries). When a field is re-edited,
   * re-point every older entry's {@code to} to the new head and add one entry whose {@code from} is
   * the previous head (legal-za v9, 626E, is the worked example).
   */
  @Test
  void shippedPack_reconcileBlock_everyToIsHead_everyFromIsV3() {
    assertThat(shippedPack.version()).isEqualTo(4);
    var entries = PackReconcileEntry.parse(PACK_ID, shippedPack.reconcile());
    assertThat(entries).hasSize(7);

    for (PackReconcileEntry entry : entries) {
      assertThat(entry.entity()).as(entry.key()).isEqualTo("document_template");
      var template = templateDef(entry.key());
      switch (entry.field()) {
        case "name" -> {
          assertThat(entry.from()).isEqualTo(V3_NAME_AND_DESCRIPTION.get(entry.key())[0]);
          assertThat(entry.to()).isEqualTo(template.name());
        }
        case "description" -> {
          assertThat(entry.from()).isEqualTo(V3_NAME_AND_DESCRIPTION.get(entry.key())[1]);
          assertThat(entry.to()).isEqualTo(template.description());
        }
        case "content" -> {
          assertThat(entry.from())
              .as("%s from = sha256 of the v3 body", entry.key())
              .isEqualTo(hashRef(v3Body(entry.key())));
          assertThat(entry.to())
              .as("%s to = sha256 of the shipped body", entry.key())
              .isEqualTo(hashRef(shippedBody(entry.key())));
        }
        default -> throw new AssertionError("Unexpected field " + entry.field());
      }
      assertThat(entry.to()).as("%s.%s", entry.key(), entry.field()).isNotEqualTo(entry.from());
    }
    assertThat(entries)
        .filteredOn(e -> "content".equals(e.field()))
        .extracting(PackReconcileEntry::key)
        .containsExactlyElementsOf(CONTENT_KEYS);
  }

  // --- the one rule ---

  @Test
  void pristineV3Rows_areRenamedAndReworded() {
    var summary = v3Row("project-summary");
    var coverLetter = v3Row("invoice-cover-letter");
    var engagement =
        row("engagement-letter", "Standard Engagement Letter", null, v3Body("engagement-letter"));
    stubInstallRows(List.of(summary, coverLetter, engagement));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(summary.getName()).isEqualTo("Matter Summary Report");
    assertThat(summary.getDescription()).isEqualTo("Matter summary report template");
    assertThat(coverLetter.getName()).isEqualTo("Fee Note Cover Letter");
    assertThat(coverLetter.getDescription()).isEqualTo("Cover letter for fee notes");
    for (DocumentTemplate dt : List.of(summary, coverLetter, engagement)) {
      assertThat(ContentHashUtil.hashOf(dt.getContent()))
          .as("%s carries the shipped body", dt.getPackTemplateKey())
          .isEqualTo(ContentHashUtil.hashOf(shippedBody(dt.getPackTemplateKey())));
    }
    assertThat(summary.getSlug())
        .as("slug is an identifier, never reconciled")
        .isEqualTo("project-summary");
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile applied")).hasSize(7);
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
  }

  @Test
  void firmRenamedTemplate_keepsName_descriptionStillReconciled() {
    var coverLetter = v3Row("invoice-cover-letter");
    coverLetter.updateContent(
        "Our own cover letter",
        coverLetter.getDescription(),
        coverLetter.getContent(),
        coverLetter.getCss());
    stubInstallRows(List.of(coverLetter));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(coverLetter.getName()).isEqualTo("Our own cover letter");
    assertThat(coverLetter.getDescription()).isEqualTo("Cover letter for fee notes");
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=common")
                    && m.contains("'invoice-cover-letter' field name"));
  }

  @Test
  void rowAlreadyAtTheShippedValues_isNotWritten() {
    var summary =
        row(
            "project-summary",
            "Matter Summary Report",
            "Matter summary report template",
            shippedBody("project-summary"));
    Instant before = summary.getUpdatedAt();
    stubInstallRows(List.of(summary));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(summary.getUpdatedAt()).isEqualTo(before);
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile applied"));
  }

  @Test
  void unsupportedNameOrDescriptionValues_warnAndNeverThrow() {
    var summary = v3Row("project-summary");
    stubInstallRows(List.of(summary));
    var pack =
        packWith(
            // non-string to
            new PackReconcileEntry(
                "document_template", "project-summary", "name", "Project Summary Report", 42),
            // a hash is a body value, not a name
            new PackReconcileEntry(
                "document_template",
                "project-summary",
                "description",
                "Project summary report template",
                PackReconcileEntry.HASH_PREFIX + "0".repeat(64)),
            // to differs from the shipped name
            new PackReconcileEntry(
                "document_template",
                "project-summary",
                "name",
                "Project Summary Report",
                "Something else"),
            // still unsupported: css
            new PackReconcileEntry("document_template", "project-summary", "css", "a", "b"));

    var logs = new ArrayList<String>();
    assertThatCode(
            () ->
                logs.addAll(
                    captureLogs(() -> seeder.reconcileInstalledPack(pack, packResource, "t"))))
        .doesNotThrowAnyException();

    assertThat(summary.getName()).isEqualTo("Project Summary Report");
    assertThat(summary.getDescription()).isEqualTo("Project summary report template");
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(4);
  }

  @Test
  void noPackSeededRowForTheKey_resolvesNoTarget() {
    stubInstallRows(List.of());

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (no pack-seeded row)")
                    && m.contains("'project-summary' field name"));
  }

  // --- helpers ---

  private void stubInstallRows(List<DocumentTemplate> rows) {
    when(templateRepository.findPackSeededByInstall(eq(installId), anyString()))
        .thenAnswer(
            inv ->
                rows.stream()
                    .filter(dt -> inv.getArgument(1, String.class).equals(dt.getPackTemplateKey()))
                    .toList());
  }

  private TemplatePackDefinition packWith(PackReconcileEntry... entries) {
    return new TemplatePackDefinition(
        shippedPack.packId(),
        shippedPack.version(),
        shippedPack.verticalProfile(),
        shippedPack.name(),
        shippedPack.description(),
        shippedPack.templates(),
        List.of(entries));
  }

  private TemplatePackTemplate templateDef(String key) {
    return shippedPack.templates().stream()
        .filter(t -> key.equals(t.templateKey()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No template " + key + " in the common pack"));
  }

  /** A pristine v3 pack-seeded row: v3 name, description and body. */
  private DocumentTemplate v3Row(String key) {
    String[] nameAndDescription = V3_NAME_AND_DESCRIPTION.get(key);
    return row(key, nameAndDescription[0], nameAndDescription[1], v3Body(key));
  }

  private DocumentTemplate row(
      String key, String name, String description, Map<String, Object> content) {
    var dt =
        new DocumentTemplate(
            TemplateEntityType.PROJECT, name, key, TemplateCategory.OTHER, content);
    TestIds.withId(dt, UUID.randomUUID());
    dt.setDescription(description);
    dt.setSource(TemplateSource.PLATFORM);
    dt.setPackId(PACK_ID);
    dt.setPackTemplateKey(key);
    dt.setSourcePackInstallId(installId);
    dt.setContentHash("a".repeat(64));
    return dt;
  }

  private static String hashRef(Map<String, Object> body) {
    return PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(body);
  }

  static Map<String, Object> v3Body(String key) {
    return readBody(V3_FIXTURES + key + ".json");
  }

  static Map<String, Object> shippedBody(String key) {
    return readBody("template-packs/common/" + key + ".json");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readBody(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JSON.readValue(in, Map.class);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(TemplatePackSeeder.class);
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
