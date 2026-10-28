package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.LinkedHashMap;
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
 * Unit test for the ADR-356 {@code document_template} reconcile resolver of {@link
 * TemplatePackSeeder} (Phase 88 626D, v9 in 626E): the legal-za {@code reconcile} block takes a
 * pristine v7 body (and, for the three templates re-edited in v9, a pristine v8 body) to the
 * shipped body under the one rule; firm-edited bodies and clones are never written. No Spring
 * context: repositories are mocked and {@code reconcileInstalledPack} is called directly with the
 * real classpath {@code pack.json}. The real-Hibernate path (installer gate, refresh pass, hash
 * re-pin) is covered by {@code packs.LegalZaTemplateReconcileIntegrationTest}.
 */
class TemplatePackSeederReconcileTest {

  static final String PACK_ID = "legal-za";
  static final String PACK_JSON = "template-packs/legal-za/pack.json";
  static final String V7_FIXTURES = "fixtures/template-packs/legal-za-v7/";
  static final String V8_FIXTURES = "fixtures/template-packs/legal-za-v8/";

  /**
   * Every template body rewritten in v8 (626.8 + ruling R-2), one v7 → shipped reconcile entry
   * each.
   */
  static final List<String> RECONCILED_KEYS =
      List.of(
          "engagement-letter-litigation",
          "engagement-letter-conveyancing",
          "engagement-letter-general",
          "fee-note-za",
          "statement-of-account",
          "matter-closure-letter",
          "deed-of-transfer",
          "bond-cancellation-instruction");

  /**
   * Template bodies re-edited in v9 (626E: fee-note DRAFT conditionals, SoA {@code categoryLabel},
   * closure-letter VAT line), one extra v8 → shipped entry each, appended after the v7 entries.
   */
  static final List<String> V9_EDITED_KEYS =
      List.of("fee-note-za", "statement-of-account", "matter-closure-letter");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final UUID installId = UUID.randomUUID();
  private final Resource packResource = new ClassPathResource(PACK_JSON);
  private DocumentTemplateRepository templateRepository;
  private PackInstallRepository packInstallRepository;
  private TemplatePackSeeder seeder;
  private TemplatePackDefinition shippedPack;

  @BeforeEach
  void setUp() throws IOException {
    templateRepository = mock(DocumentTemplateRepository.class);
    packInstallRepository = mock(PackInstallRepository.class);
    var install =
        new PackInstall(
            PACK_ID,
            PackType.DOCUMENT_TEMPLATE,
            "9",
            "SA Legal Templates",
            Instant.now(),
            null,
            17);
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

  // --- shipped-pack guard (rulings R-11 / R-12) ---

  /**
   * The shipped legal-za {@code reconcile} block: one entry per v8-rewritten template whose {@code
   * from} is the v7 body, then one entry per v9-re-edited template whose {@code from} is the v8
   * body (test fixtures copied from git before each edit); every {@code to} is the body shipped on
   * the classpath — a later body tweak without re-computing {@code to} fails here, not at a
   * tenant's boot.
   */
  @Test
  void shippedPack_reconcileBlock_takesEachPriorBodyToTheShippedBody() {
    assertThat(shippedPack.version()).isEqualTo(9);
    var entries = PackReconcileEntry.parse(PACK_ID, shippedPack.reconcile());
    var expectedKeys = new ArrayList<>(RECONCILED_KEYS);
    expectedKeys.addAll(V9_EDITED_KEYS);
    assertThat(entries).extracting(PackReconcileEntry::key).containsExactlyElementsOf(expectedKeys);

    for (int i = 0; i < entries.size(); i++) {
      PackReconcileEntry entry = entries.get(i);
      boolean fromV8 = i >= RECONCILED_KEYS.size();
      assertThat(entry.entity()).as(entry.key()).isEqualTo("document_template");
      assertThat(entry.field()).as(entry.key()).isEqualTo("content");
      assertThat(shippedPack.templates())
          .as("reconcile key %s is a templateKey of the pack", entry.key())
          .anyMatch(t -> entry.key().equals(t.templateKey()));
      assertThat(entry.from())
          .as("%s from = sha256 of the %s body", entry.key(), fromV8 ? "v8" : "v7")
          .isEqualTo(hashRef(fromV8 ? v8Body(entry.key()) : v7Body(entry.key())));
      assertThat(entry.to())
          .as("%s to = sha256 of the shipped body", entry.key())
          .isEqualTo(hashRef(shippedBody(entry.key())));
      assertThat(entry.to()).as(entry.key()).isNotEqualTo(entry.from());
    }
  }

  // --- the one rule ---

  @Test
  void pristineV7Rows_areUpdatedToTheShippedBody_cssKept() {
    var rows = new ArrayList<DocumentTemplate>();
    for (String key : RECONCILED_KEYS) {
      rows.add(platformRow(key, v7Body(key)));
    }
    rows.getFirst().setCss("p { margin: 0; }");
    stubInstallRows(rows);

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    for (DocumentTemplate row : rows) {
      assertThat(ContentHashUtil.hashOf(row.getContent()))
          .as("%s carries the shipped body", row.getPackTemplateKey())
          .isEqualTo(ContentHashUtil.hashOf(shippedBody(row.getPackTemplateKey())));
    }
    assertThat(rows.getFirst().getCss()).isEqualTo("p { margin: 0; }");
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile applied")).hasSize(8);
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
  }

  @Test
  void firmEditedRow_isLeftAlone_andLoggedAtInfo() {
    Map<String, Object> edited = withExtraParagraph(v7Body("fee-note-za"), "Our own wording");
    var feeNote = platformRow("fee-note-za", edited);
    var general = platformRow("engagement-letter-general", v7Body("engagement-letter-general"));
    stubInstallRows(List.of(feeNote, general));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(feeNote.getContent()).isEqualTo(edited);
    assertThat(ContentHashUtil.hashOf(general.getContent()))
        .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-general")));
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=legal-za")
                    && m.contains("'fee-note-za'"));
  }

  @Test
  void pristineV8Rows_ofTheV9EditedTemplates_reachTheV9Body() {
    var rows = new ArrayList<DocumentTemplate>();
    for (String key : V9_EDITED_KEYS) {
      rows.add(platformRow(key, v8Body(key)));
    }
    stubInstallRows(rows);

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    for (DocumentTemplate row : rows) {
      assertThat(ContentHashUtil.hashOf(row.getContent()))
          .as("%s carries the shipped v9 body", row.getPackTemplateKey())
          .isEqualTo(ContentHashUtil.hashOf(shippedBody(row.getPackTemplateKey())));
    }
    assertThat(logs)
        .filteredOn(m -> m.startsWith("Reconcile applied"))
        .hasSize(V9_EDITED_KEYS.size());
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
  }

  @Test
  void firmEditedV8Row_isLeftAlone() {
    Map<String, Object> edited =
        withExtraParagraph(v8Body("statement-of-account"), "Our own SoA wording");
    var soa = platformRow("statement-of-account", edited);
    stubInstallRows(List.of(soa));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(soa.getContent()).isEqualTo(edited);
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=legal-za")
                    && m.contains("'statement-of-account'"));
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile applied"));
  }

  /**
   * The resolver asks only for this install's pack-seeded rows ({@code findPackSeededByInstall}:
   * PLATFORM, not a clone, tagged with the install — the predicate is JPQL, proven against real
   * Hibernate in {@code LegalZaTemplateReconcileIntegrationTest}); none for the key → no target.
   */
  @Test
  void noPackSeededRowForTheKey_resolvesNoTarget() {
    var general = platformRow("engagement-letter-general", v7Body("engagement-letter-general"));
    stubInstallRows(List.of(general));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    // fee-note-za has two entries since v9 (v7 → shipped, v8 → shipped): resolved once per entry.
    verify(templateRepository, times(2)).findPackSeededByInstall(installId, "fee-note-za");
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (no pack-seeded row)")
                    && m.contains("fee-note-za"));
  }

  @Test
  void rowAlreadyAtTheShippedBody_isNotWritten() {
    var row = platformRow("fee-note-za", shippedBody("fee-note-za"));
    Instant before = row.getUpdatedAt();
    stubInstallRows(List.of(row));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(row.getUpdatedAt()).isEqualTo(before);
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile applied"));
  }

  @Test
  void twoPackSeededRowsForOneKey_isAmbiguous_warnsAndWritesNeither() {
    Map<String, Object> v7 = v7Body("fee-note-za");
    var a = platformRow("fee-note-za", v7);
    var b = platformRow("fee-note-za", v7);
    stubInstallRows(List.of(a, b));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(shippedPack, packResource, "t"));

    assertThat(a.getContent()).isSameAs(v7);
    assertThat(b.getContent()).isSameAs(v7);
    assertThat(logs).anyMatch(m -> m.startsWith("Reconcile entry ignored") && m.contains("ambig"));
  }

  @Test
  void unsupportedEntityFieldOrValue_warnsAndNeverThrows() {
    Map<String, Object> v7 = v7Body("fee-note-za");
    var row = platformRow("fee-note-za", v7);
    stubInstallRows(List.of(row));
    String to = hashRef(shippedBody("fee-note-za"));
    String from = hashRef(v7);
    var pack =
        packWith(
            new PackReconcileEntry("clause", "fee-note-za", "content", from, to),
            new PackReconcileEntry("document_template", "fee-note-za", "css", from, to),
            new PackReconcileEntry("document_template", "fee-note-za", "content", "a", "b"),
            new PackReconcileEntry("document_template", "no-such-key", "content", from, to));

    var logs = new ArrayList<String>();
    assertThatCode(
            () ->
                logs.addAll(
                    captureLogs(() -> seeder.reconcileInstalledPack(pack, packResource, "t"))))
        .doesNotThrowAnyException();

    assertThat(row.getContent()).isSameAs(v7);
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(4);
  }

  @Test
  void toHashNotMatchingTheShippedBody_warnsAndSkips() {
    Map<String, Object> v7 = v7Body("fee-note-za");
    var row = platformRow("fee-note-za", v7);
    stubInstallRows(List.of(row));
    var pack =
        packWith(
            new PackReconcileEntry(
                "document_template",
                "fee-note-za",
                "content",
                hashRef(v7),
                PackReconcileEntry.HASH_PREFIX + "0".repeat(64)));

    var logs = captureLogs(() -> seeder.reconcileInstalledPack(pack, packResource, "t"));

    assertThat(row.getContent()).isSameAs(v7);
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile entry ignored")
                    && m.contains("to-hash does not match the shipped body"));
  }

  @Test
  void noInstallRow_resolvesNoTarget() {
    when(packInstallRepository.findByPackId(PACK_ID)).thenReturn(Optional.empty());
    Map<String, Object> v7 = v7Body("fee-note-za");
    var row = platformRow("fee-note-za", v7);
    stubInstallRows(List.of(row));

    seeder.reconcileInstalledPack(shippedPack, packResource, "t");

    assertThat(row.getContent()).isSameAs(v7);
  }

  // --- helpers ---

  /** Stubs the install-scoped finder per key (the JPQL itself is exercised by real Hibernate). */
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

  private DocumentTemplate platformRow(String key, Map<String, Object> content) {
    var dt =
        new DocumentTemplate(TemplateEntityType.PROJECT, key, key, TemplateCategory.OTHER, content);
    TestIds.withId(dt, UUID.randomUUID());
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

  static Map<String, Object> v7Body(String key) {
    return readBody(V7_FIXTURES + key + ".json");
  }

  static Map<String, Object> v8Body(String key) {
    return readBody(V8_FIXTURES + key + ".json");
  }

  static Map<String, Object> shippedBody(String key) {
    return readBody("template-packs/legal-za/" + key + ".json");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readBody(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JSON.readValue(in, Map.class);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> withExtraParagraph(Map<String, Object> body, String text) {
    var copy = new LinkedHashMap<>(body);
    var content = new ArrayList<>((List<Object>) body.get("content"));
    content.add(
        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))));
    copy.put("content", content);
    return copy;
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
