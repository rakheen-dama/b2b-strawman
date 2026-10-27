package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder.LoadedPack;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackDefinition;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder.TemplateContent;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Installer → reconcile-hook wiring for document template packs (ADR-356): the hook runs only when
 * the version gate opens, and the installer — not the reconcile target — re-pins the content hash
 * of templates that were pristine before the hook. Pure Mockito, no Spring context.
 */
class TemplatePackInstallerReconcileTest {

  private static final String PACK_ID = "test-templates";
  private static final String TENANT = "tenant_test";

  private final PackInstallRepository packInstallRepository = mock(PackInstallRepository.class);
  private final DocumentTemplateRepository documentTemplateRepository =
      mock(DocumentTemplateRepository.class);
  private final GeneratedDocumentRepository generatedDocumentRepository =
      mock(GeneratedDocumentRepository.class);
  private final TemplatePackSeeder seeder = mock(TemplatePackSeeder.class);
  private final Resource resource = new ByteArrayResource(new byte[0]);

  private TemplatePackInstaller installer;
  private UUID installId;

  @BeforeEach
  void setUp() {
    installer =
        new TemplatePackInstaller(
            packInstallRepository,
            documentTemplateRepository,
            generatedDocumentRepository,
            seeder,
            new ObjectMapper());
    installId = UUID.randomUUID();
    when(packInstallRepository.advancePackVersion(anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(1);
  }

  @Test
  void gateOpen_invokesHookExactlyOnce() {
    givenInstalledAt("1");
    givenClasspathPack(2, List.of());

    assertThat(installer.reconcile(PACK_ID, TENANT)).isTrue();

    verify(seeder, times(1)).reconcileInstalledPack(any(), eq(resource), eq(TENANT));
  }

  @Test
  void equalVersion_neverInvokesHook() {
    givenInstalledAt("2");
    givenClasspathPack(2, List.of());

    assertThat(installer.reconcile(PACK_ID, TENANT)).isFalse();

    verify(seeder, never()).reconcileInstalledPack(any(), any(), any());
  }

  @Test
  void nonNumericInstalledVersion_neverInvokesHook() {
    givenInstalledAt("v1");
    givenClasspathPack(2, List.of());

    assertThat(installer.reconcile(PACK_ID, TENANT)).isFalse();

    verify(seeder, never()).reconcileInstalledPack(any(), any(), any());
  }

  @Test
  void pristineRowMutatedByHook_hashRepinned_editedRowNotRepinned() {
    givenInstalledAt("1");
    givenClasspathPack(2, List.of());

    var pristine = template("pristine", body("Old pristine"));
    pristine.setContentHash(hashOf(pristine));
    // Tenant edited this row after install: stored hash is the install-time body's hash.
    var edited = template("edited", body("Tenant wording"));
    String editedStoredHash = installer.computeContentHash(body("Shipped wording"), null);
    edited.setContentHash(editedStoredHash);
    when(documentTemplateRepository.findBySourcePackInstallId(installId))
        .thenReturn(List.of(pristine, edited));

    doAnswer(
            inv -> {
              // A reconcile target writes the new value only — never the content hash.
              pristine.updateContent("pristine", null, body("New pristine"), null);
              edited.updateContent("edited", null, body("New for edited"), null);
              return null;
            })
        .when(seeder)
        .reconcileInstalledPack(any(), any(), any());

    installer.reconcile(PACK_ID, TENANT);

    assertThat(pristine.getContentHash()).isEqualTo(hashOf(pristine));
    assertThat(edited.getContentHash()).isEqualTo(editedStoredHash);
    verify(documentTemplateRepository).saveAll(List.of(pristine));

    // Uninstall gate: only the row edited before the hook counts as edited.
    var check = installer.checkUninstallable(PACK_ID, TENANT);
    assertThat(check.canUninstall()).isFalse();
    assertThat(check.blockingReason()).isEqualTo("1 of 2 templates have been edited");
  }

  @Test
  void rowRefreshedByCreateRefreshPass_isCoveredBySnapshotAndRepinnedAfterHook() {
    givenInstalledAt("1");
    var def =
        new TemplatePackTemplate(
            "letter", "letter", "COVER_LETTER", "PROJECT", "letter.json", null, null, 0, null);
    givenClasspathPack(2, List.of(def));

    // Pristine at v1 content; the classpath pack ships v2 content → refreshIfUnmodified updates it.
    var row = template("letter", body("v1 body"));
    row.setContentHash(hashOf(row));
    when(documentTemplateRepository.findByPackIdAndPackTemplateKey(PACK_ID, "letter"))
        .thenReturn(Optional.of(row));
    when(seeder.loadTemplateContent(def, resource))
        .thenReturn(new TemplateContent(body("v2 body"), null));
    // The snapshot query runs after the refresh pass, so it sees the refreshed row.
    when(documentTemplateRepository.findBySourcePackInstallId(installId)).thenReturn(List.of(row));

    doAnswer(
            inv -> {
              row.updateContent("letter", null, body("v2 body, reconciled"), null);
              return null;
            })
        .when(seeder)
        .reconcileInstalledPack(any(), any(), any());

    installer.reconcile(PACK_ID, TENANT);

    assertThat(row.getContent()).isEqualTo(body("v2 body, reconciled"));
    assertThat(row.getContentHash()).isEqualTo(hashOf(row));
  }

  @Test
  void hookChangingNothing_repinsNothing() {
    givenInstalledAt("1");
    givenClasspathPack(2, List.of());
    var pristine = template("pristine", body("Unchanged"));
    pristine.setContentHash(hashOf(pristine));
    when(documentTemplateRepository.findBySourcePackInstallId(installId))
        .thenReturn(List.of(pristine));

    installer.reconcile(PACK_ID, TENANT);

    verify(documentTemplateRepository, never()).saveAll(any());
  }

  // --- helpers ---

  private void givenInstalledAt(String version) {
    var install =
        new PackInstall(
            PACK_ID, PackType.DOCUMENT_TEMPLATE, version, "Test", Instant.now(), null, 1);
    ReflectionTestUtils.setField(install, "id", installId);
    when(packInstallRepository.findByPackId(PACK_ID)).thenReturn(Optional.of(install));
  }

  private void givenClasspathPack(int version, List<TemplatePackTemplate> templates) {
    var pack = new TemplatePackDefinition(PACK_ID, version, null, "Test", "desc", templates, null);
    when(seeder.getAvailablePacks()).thenReturn(List.of(new LoadedPack<>(pack, resource)));
  }

  private DocumentTemplate template(String name, Map<String, Object> content) {
    var dt =
        new DocumentTemplate(
            TemplateEntityType.PROJECT, name, name, TemplateCategory.COVER_LETTER, content);
    ReflectionTestUtils.setField(dt, "id", UUID.randomUUID());
    dt.setSourcePackInstallId(installId);
    return dt;
  }

  private String hashOf(DocumentTemplate dt) {
    return installer.computeContentHash(dt.getContent(), dt.getCss());
  }

  private static Map<String, Object> body(String text) {
    return Map.of(
        "type",
        "doc",
        "content",
        List.of(
            Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text)))));
  }
}
