package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplateSource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Hibernate test of the legal-za v8 pack content reconcile (Phase 88 626D, ADR-356): the
 * startup path {@code PackInstallService.internalInstall} → {@code TemplatePackInstaller.reconcile}
 * (version gate → refresh pass → pristine snapshot → {@code
 * TemplatePackSeeder.resolveReconcileTarget} → content-hash re-pin) takes every rewritten template
 * from its v7 body to the shipped v8 body, keeps firm edits, never touches a clone, and is a no-op
 * at equal version and on a second run.
 *
 * <p>A v7 tenant is simulated by writing each rewritten row's exact v7 body (test fixture copied
 * from git before the edit) with its install-time hash pinned (pristine), then rewinding {@code
 * pack_install.pack_version} by one. Each test re-runs the simulation, so tests are
 * order-independent; assertions target this tenant's rows by pack key only (no global counts).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalZaTemplateReconcileIntegrationTest {

  private static final String ORG_ID = "org_legal_za_template_reconcile_test";
  private static final String PACK_ID = "legal-za";
  private static final String CURRENT_VERSION = "8";
  private static final List<String> RECONCILED_KEYS =
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
   * A legal-za template outside the reconcile block with no clause links: deleted to force a
   * create.
   */
  private static final String MISSING_KEY = "notice-of-motion";

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TemplatePackInstaller templatePackInstaller;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Legal ZA Template Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  /** (a) Pristine v7 rows reach the v8 bodies, hashes re-pinned, nothing reads as edited. */
  @Test
  void pristineV7Templates_reachTheV8Bodies_andStayPristine() {
    simulateV7Tenant(rows -> {}, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped v8 body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s hash re-pinned to the new content", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
    String reason = uninstallReason();
    assertThat(reason == null ? "" : reason)
        .as("reconciled rows must not read as edited")
        .doesNotContain("edited");
  }

  /** (b) A firm-edited body survives untouched and still reads as edited. */
  @Test
  void firmEditedBody_survives_andIsLoggedAtInfo() {
    Map<String, Object> edited = withExtraParagraph(v7Body("fee-note-za"), "Our own payment terms");
    var pinned = new ArrayList<String>();
    simulateV7Tenant(
        rows -> {
          var feeNote = rows.get("fee-note-za");
          pinned.add(feeNote.getContentHash()); // v7 hash stays → the edit reads as edited
          feeNote.updateContent(feeNote.getName(), feeNote.getDescription(), edited, null);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var feeNote = packRow("fee-note-za");
          assertThat(ContentHashUtil.hashOf(feeNote.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(edited));
          assertThat(feeNote.getContentHash()).isEqualTo(pinned.getFirst());
          var general = packRow("engagement-letter-general");
          assertThat(ContentHashUtil.hashOf(general.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-general")));
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=legal-za")
                    && m.contains("'fee-note-za'"));
    assertThat(uninstallReason()).contains("edited");
  }

  /**
   * (c) The refresh pass skips a row whose css was edited (stored hash ≠ content + css), but its
   * body is still byte-identical to v7 — the reconcile block applies the v8 body, keeps the css and
   * does not re-pin (the row was not pristine).
   */
  @Test
  void cssEditedRowWithV7Body_getsTheV8BodyFromTheReconcileBlock_cssKept_notRepinned() {
    String css = "p { color: #333333; }";
    var pinned = new ArrayList<String>();
    simulateV7Tenant(
        rows -> {
          var row = rows.get("engagement-letter-conveyancing");
          pinned.add(row.getContentHash());
          row.updateContent(row.getName(), row.getDescription(), row.getContent(), css);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var row = packRow("engagement-letter-conveyancing");
          assertThat(ContentHashUtil.hashOf(row.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-conveyancing")));
          assertThat(row.getCss()).isEqualTo(css);
          assertThat(row.getContentHash()).isEqualTo(pinned.getFirst());
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile applied")
                    && m.contains("'engagement-letter-conveyancing'"));
  }

  /**
   * (d) A clone shares {@code packId} + {@code packTemplateKey} with its source. Reconcile must not
   * throw on it (it used to: a single-result finder over both rows) and must leave it
   * byte-identical while the pack row is reconciled.
   */
  @Test
  void clone_survivesByteIdentical_andReconcileDoesNotThrow() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId = inTenantTx(() -> saveCloneOf("fee-note-za", "-custom-626d"));
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(clone.getSource()).isEqualTo(TemplateSource.ORG_CUSTOM);
            assertThat(ContentHashUtil.hashOf(clone.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            assertThat(ContentHashUtil.hashOf(packRow("fee-note-za").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("fee-note-za")));
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /**
   * 626D review F-3: a version bump that re-creates a missing pack template tags the new row with
   * the install — and never sweeps in a clone (clones copy {@code packId} and leave {@code
   * source_pack_install_id} null). The clone stays untagged with its hash unchanged and is not
   * counted as an item of the install.
   */
  @Test
  void versionBumpCreatingAMissingTemplate_neverTagsAClone() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId = inTenantTx(() -> saveCloneOf("fee-note-za", "-custom-626d-f3"));
    String cloneHashBefore =
        inTenantTx(
            () -> documentTemplateRepository.findById(cloneId).orElseThrow().getContentHash());
    inTenantTx(
        () -> {
          documentTemplateRepository.delete(packRow(MISSING_KEY));
          return null;
        });
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            UUID installId = packInstallRepository.findByPackId(PACK_ID).orElseThrow().getId();
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(clone.getSourcePackInstallId()).as("clone stays untagged").isNull();
            assertThat(clone.getContentHash()).isEqualTo(cloneHashBefore);
            var recreated = packRow(MISSING_KEY);
            assertThat(recreated.getSourcePackInstallId()).isEqualTo(installId);
            assertThat(documentTemplateRepository.findBySourcePackInstallId(installId))
                .extracting(DocumentTemplate::getId)
                .contains(recreated.getId())
                .doesNotContain(cloneId);
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /**
   * 626D review F-5: two pack-seeded rows for one key (no unique constraint backs the pair) never
   * throw at boot — the installer's refresh pass and the reconcile resolver each WARN and skip that
   * template, and the rest of the pack is still reconciled.
   */
  @Test
  void twoPackSeededRowsForOneKey_warnAndSkip_onBothPaths_neverThrow() {
    simulateV7Tenant(rows -> {}, true);
    UUID originalId = inTenantTx(() -> packRow("fee-note-za").getId());
    UUID duplicateId =
        inTenantTx(
            () -> {
              var original = packRow("fee-note-za");
              var dup =
                  new DocumentTemplate(
                      original.getPrimaryEntityType(),
                      original.getName() + " (duplicate)",
                      original.getSlug() + "-dup-626d",
                      original.getCategory(),
                      original.getContent());
              dup.setSource(TemplateSource.PLATFORM);
              dup.setPackId(original.getPackId());
              dup.setPackTemplateKey(original.getPackTemplateKey());
              dup.setSourcePackInstallId(original.getSourcePackInstallId());
              dup.setContentHash(original.getContentHash());
              return documentTemplateRepository.save(dup).getId();
            });
    try {
      var logs =
          captureLogs(
              () -> packInstallService.internalInstall(PACK_ID, schema),
              TemplatePackInstaller.class,
              TemplatePackSeeder.class);

      inTenantTx(
          () -> {
            for (UUID id : List.of(originalId, duplicateId)) {
              assertThat(
                      ContentHashUtil.hashOf(
                          documentTemplateRepository.findById(id).orElseThrow().getContent()))
                  .as("ambiguous fee-note row %s left at v7", id)
                  .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            }
            assertThat(ContentHashUtil.hashOf(packRow("engagement-letter-general").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-general")));
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
      assertThat(logs)
          .anyMatch(m -> m.contains("'fee-note-za'") && m.contains("2 pack-seeded rows"))
          .anyMatch(m -> m.startsWith("Reconcile entry ignored") && m.contains("ambiguous"));
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(duplicateId);
            return null;
          });
    }
  }

  /**
   * The resolver's ownership predicate lives in JPQL ({@code findPackSeededByInstall}): a clone is
   * never a target even in the worst case of carrying this install's tag — it survives
   * byte-identical while the pack row is reconciled.
   */
  @Test
  void cloneTaggedWithTheInstall_isNeverATarget() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId =
        inTenantTx(
            () -> {
              UUID id = saveCloneOf("fee-note-za", "-custom-626d-tagged");
              var clone = documentTemplateRepository.findById(id).orElseThrow();
              clone.setSourcePackInstallId(packRow("fee-note-za").getSourcePackInstallId());
              documentTemplateRepository.save(clone);
              return id;
            });
    try {
      inTenantTx(
          () -> {
            var pack = packRow("fee-note-za");
            assertThat(
                    documentTemplateRepository.findPackSeededByInstall(
                        pack.getSourcePackInstallId(), "fee-note-za"))
                .extracting(DocumentTemplate::getId)
                .containsExactly(pack.getId());
            return null;
          });

      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(ContentHashUtil.hashOf(clone.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            assertThat(ContentHashUtil.hashOf(packRow("fee-note-za").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("fee-note-za")));
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /** (e) Equal version: the gate stays closed and nothing is written. */
  @Test
  void equalVersion_isANoOp() {
    simulateV7Tenant(rows -> {}, false);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : RECONCILED_KEYS) {
            assertThat(ContentHashUtil.hashOf(packRow(key).getContent()))
                .as("%s untouched at equal version", key)
                .isEqualTo(ContentHashUtil.hashOf(v7Body(key)));
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /** (f) A second run after the reconcile changes nothing. */
  @Test
  void secondRun_changesNothing() {
    simulateV7Tenant(rows -> {}, true);
    packInstallService.internalInstall(PACK_ID, schema);
    Map<String, Snapshot> first = snapshot();

    packInstallService.internalInstall(PACK_ID, schema);

    assertThat(snapshot()).isEqualTo(first);
    inTenantTx(
        () -> {
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  // --- helpers ---

  private record Snapshot(String bodyHash, String contentHash, Instant updatedAt) {}

  private Map<String, Snapshot> snapshot() {
    return inTenantTx(
        () -> {
          var out = new LinkedHashMap<String, Snapshot>();
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            out.put(
                key,
                new Snapshot(
                    ContentHashUtil.hashOf(row.getContent()),
                    row.getContentHash(),
                    row.getUpdatedAt()));
          }
          return out;
        });
  }

  /**
   * Writes the v7 body into every rewritten pack row with its install-time hash pinned (pristine),
   * applies {@code firmEdits}, then (optionally) rewinds the recorded install version by one.
   */
  private void simulateV7Tenant(Consumer<Map<String, DocumentTemplate>> firmEdits, boolean rewind) {
    inTenantTx(
        () -> {
          var rows = new LinkedHashMap<String, DocumentTemplate>();
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            Map<String, Object> v7 = v7Body(key);
            row.updateContent(row.getName(), row.getDescription(), v7, null);
            row.setContentHash(templatePackInstaller.computeContentHash(v7, null));
            rows.put(key, row);
          }
          firmEdits.accept(rows);
          documentTemplateRepository.saveAll(rows.values());

          if (rewind) {
            String current = installedVersion();
            String previous = String.valueOf(Integer.parseInt(current) - 1);
            assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, current, previous))
                .as("rewind %s %s -> %s", PACK_ID, current, previous)
                .isEqualTo(1);
          }
          return null;
        });
  }

  /** The pack-seeded (PLATFORM, non-clone) row for a key — clones share packId + key. */
  private DocumentTemplate packRow(String key) {
    var rows =
        documentTemplateRepository.findByPackTemplateKey(key).stream()
            .filter(dt -> PACK_ID.equals(dt.getPackId()))
            .filter(dt -> dt.getSource() == TemplateSource.PLATFORM)
            .filter(dt -> dt.getSourceTemplateId() == null)
            .toList();
    assertThat(rows).as("one pack row for %s", key).hasSize(1);
    return rows.getFirst();
  }

  private String installedVersion() {
    return packInstallRepository.findByPackId(PACK_ID).orElseThrow().getPackVersion();
  }

  private String uninstallReason() {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> templatePackInstaller.checkUninstallable(PACK_ID, schema).blockingReason());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static Map<String, Object> v7Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v7/" + key + ".json");
  }

  private static Map<String, Object> shippedBody(String key) {
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

  private static List<String> captureSeederLogs(Runnable action) {
    return captureLogs(action, TemplatePackSeeder.class);
  }

  private static List<String> captureLogs(Runnable action, Class<?>... sources) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    var loggers = new ArrayList<Logger>();
    for (Class<?> source : sources) {
      Logger logger = (Logger) LoggerFactory.getLogger(source);
      logger.addAppender(appender);
      logger.setLevel(Level.INFO);
      loggers.add(logger);
    }
    try {
      action.run();
    } finally {
      for (Logger logger : loggers) {
        logger.detachAppender(appender);
        logger.setLevel(null);
      }
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  /** Mirrors {@code DocumentTemplateService.cloneTemplate}: ORG_CUSTOM, same pack id + key. */
  private UUID saveCloneOf(String key, String slugSuffix) {
    var original = packRow(key);
    var clone =
        new DocumentTemplate(
            original.getPrimaryEntityType(),
            original.getName() + " (Custom)",
            original.getSlug() + slugSuffix,
            original.getCategory(),
            original.getContent());
    clone.setDescription(original.getDescription());
    clone.setSourceTemplateId(original.getId());
    clone.setPackId(original.getPackId());
    clone.setPackTemplateKey(original.getPackTemplateKey());
    return documentTemplateRepository.save(clone).getId();
  }
}
