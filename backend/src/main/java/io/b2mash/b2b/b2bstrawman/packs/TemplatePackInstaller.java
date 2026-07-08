package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackDefinition;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Installs and uninstalls document template packs. Wraps {@link
 * TemplatePackSeeder#applyPackContent} with {@link PackInstall} tracking, content hash computation,
 * and uninstall gate checks.
 */
@Service
public class TemplatePackInstaller implements PackInstaller {

  private static final Logger log = LoggerFactory.getLogger(TemplatePackInstaller.class);

  private final PackInstallRepository packInstallRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final TemplatePackSeeder templatePackSeeder;
  private final ObjectMapper objectMapper;

  public TemplatePackInstaller(
      PackInstallRepository packInstallRepository,
      DocumentTemplateRepository documentTemplateRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      TemplatePackSeeder templatePackSeeder,
      ObjectMapper objectMapper) {
    this.packInstallRepository = packInstallRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.templatePackSeeder = templatePackSeeder;
    this.objectMapper = objectMapper;
  }

  @Override
  public PackType type() {
    return PackType.DOCUMENT_TEMPLATE;
  }

  @Override
  public List<PackCatalogEntry> availablePacks() {
    return templatePackSeeder.getAvailablePacks().stream()
        .map(
            loaded -> {
              var pack = loaded.definition();
              return new PackCatalogEntry(
                  pack.packId(),
                  pack.name(),
                  pack.description(),
                  String.valueOf(pack.version()),
                  PackType.DOCUMENT_TEMPLATE,
                  pack.verticalProfile(),
                  pack.templates().size(),
                  false,
                  null);
            })
        .toList();
  }

  @Override
  @Transactional
  public void install(String packId, String tenantId, String memberId) {
    // Idempotency check
    if (packInstallRepository.findByPackId(packId).isPresent()) {
      log.info("Template pack {} already installed, skipping", packId);
      return;
    }

    var loadedPack =
        templatePackSeeder.getAvailablePacks().stream()
            .filter(lp -> packId.equals(lp.definition().packId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Pack", packId));

    TemplatePackDefinition pack = loadedPack.definition();

    // Create PackInstall row — catch constraint violation for TOCTOU race
    PackInstall install;
    try {
      install =
          new PackInstall(
              packId,
              PackType.DOCUMENT_TEMPLATE,
              String.valueOf(pack.version()),
              pack.name(),
              Instant.now(),
              memberId != null ? UUID.fromString(memberId) : null,
              pack.templates().size());
      install = packInstallRepository.save(install);
    } catch (DataIntegrityViolationException e) {
      log.info("Template pack {} already installed (concurrent request), skipping", packId);
      return;
    }

    // Check if templates already exist from a prior seeder run (e.g., during provisioning).
    // If so, tag them with this install. Otherwise, create them fresh.
    var untaggedPackTemplates =
        documentTemplateRepository.findByPackIdAndSourcePackInstallIdIsNull(packId);

    UUID installId = install.getId();
    if (!untaggedPackTemplates.isEmpty()) {
      // Templates already created by seeder — tag them
      for (DocumentTemplate dt : untaggedPackTemplates) {
        dt.setSourcePackInstallId(installId);
        dt.setContentHash(computeTemplateHash(dt));
      }
      documentTemplateRepository.saveAll(untaggedPackTemplates);
    } else {
      // No existing templates — create via seeder
      templatePackSeeder.applyPackContent(pack, loadedPack.resource(), tenantId);

      // Tag the newly created templates
      var freshUntagged =
          documentTemplateRepository.findByPackIdAndSourcePackInstallIdIsNull(packId);
      for (DocumentTemplate dt : freshUntagged) {
        dt.setSourcePackInstallId(installId);
        dt.setContentHash(computeTemplateHash(dt));
      }
      documentTemplateRepository.saveAll(freshUntagged);
    }

    log.info(
        "Installed template pack {} with {} templates for tenant {}",
        packId,
        pack.templates().size(),
        tenantId);
  }

  /**
   * Reconciles an already-installed template pack with its current classpath definition. If the
   * classpath pack version is newer than the installed version:
   *
   * <ul>
   *   <li>creates any pack templates that do not exist in the tenant (matched by {@code packId +
   *       packTemplateKey}) and tags them with the existing install;
   *   <li>refreshes the content/CSS of existing pack templates the tenant has <b>not</b> modified
   *       (stored content hash still matches the row's current content) to the classpath version —
   *       this is how content fixes (e.g. LZKC-010's {@code invoice.number} placeholder typo) reach
   *       existing tenants. Tenant-edited templates are never touched, and templates without a
   *       recorded content hash are left alone (unknown provenance);
   *   <li>advances the recorded install version.
   * </ul>
   *
   * <p>Templates are never deleted. Note that a version bump restores any pack template the tenant
   * is missing (including ones a member deleted) — pack upgrades re-deliver the full pack content.
   */
  @Override
  @Transactional
  public boolean reconcile(String packId, String tenantId) {
    var installOpt = packInstallRepository.findByPackId(packId);
    if (installOpt.isEmpty()) {
      return false;
    }
    PackInstall install = installOpt.get();

    var loadedPackOpt =
        templatePackSeeder.getAvailablePacks().stream()
            .filter(lp -> packId.equals(lp.definition().packId()))
            .findFirst();
    if (loadedPackOpt.isEmpty()) {
      return false;
    }
    var loadedPack = loadedPackOpt.get();
    TemplatePackDefinition pack = loadedPack.definition();

    String installedVersion = install.getPackVersion();
    if (pack.version() <= parseVersion(installedVersion)) {
      return false;
    }

    // Atomically claim the version advance before creating anything. Under concurrent
    // reconciliation (e.g. two replicas booting simultaneously) only one transaction wins this
    // conditional UPDATE; the loser blocks on the pack_install row lock until the winner commits,
    // then matches zero rows and backs off — the same TOCTOU discipline install() gets from
    // uq_pack_install_pack_id, without a new unique constraint on document_templates.
    int claimed =
        packInstallRepository.advancePackVersion(
            packId, installedVersion, String.valueOf(pack.version()), pack.templates().size());
    if (claimed == 0) {
      log.info(
          "Template pack {} reconcile skipped for tenant {} — version advance already claimed",
          packId,
          tenantId);
      return false;
    }

    int created = 0;
    int refreshed = 0;
    for (var templateDef : pack.templates()) {
      var existing =
          documentTemplateRepository.findByPackIdAndPackTemplateKey(
              packId, templateDef.templateKey());
      if (existing.isPresent()) {
        if (refreshIfUnmodified(existing.get(), templateDef, loadedPack.resource())) {
          refreshed++;
        }
        continue;
      }
      templatePackSeeder.applySingleTemplate(pack, templateDef, loadedPack.resource());
      created++;
    }

    if (created > 0) {
      var freshUntagged =
          documentTemplateRepository.findByPackIdAndSourcePackInstallIdIsNull(packId);
      for (DocumentTemplate dt : freshUntagged) {
        dt.setSourcePackInstallId(install.getId());
        dt.setContentHash(computeTemplateHash(dt));
      }
      documentTemplateRepository.saveAll(freshUntagged);
    }

    log.info(
        "Reconciled template pack {} to v{} for tenant {} ({} new templates, {} refreshed)",
        packId,
        pack.version(),
        tenantId,
        created,
        refreshed);
    return true;
  }

  /**
   * Refreshes a pack template's content/CSS to the current classpath pack content, but only when
   * the tenant has not modified it: the stored content hash must still match the row's current
   * content. Returns {@code true} if the template was refreshed. The content hash is re-pinned to
   * the refreshed content so the template continues to read as pristine for the uninstall gate and
   * future reconciles.
   */
  private boolean refreshIfUnmodified(
      DocumentTemplate template, TemplatePackTemplate templateDef, Resource packResource) {
    String storedHash = template.getContentHash();
    if (storedHash == null) {
      return false; // unknown provenance — never overwrite
    }
    if (!storedHash.equals(computeTemplateHash(template))) {
      return false; // tenant-edited — preserve the tenant's changes
    }

    var packContent = templatePackSeeder.loadTemplateContent(templateDef, packResource);
    String packHash = computeContentHash(packContent.content(), packContent.css());
    if (storedHash.equals(packHash)) {
      return false; // already on current pack content
    }

    template.updateContent(
        template.getName(), template.getDescription(), packContent.content(), packContent.css());
    template.setContentHash(packHash);
    documentTemplateRepository.save(template);
    return true;
  }

  private static int parseVersion(String version) {
    try {
      return Integer.parseInt(version);
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public UninstallCheck checkUninstallable(String packId, String tenantId) {
    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    return computeUninstallCheck(install);
  }

  @Override
  @Transactional
  public void uninstall(String packId, String tenantId, String memberId) {
    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    var check = computeUninstallCheck(install);
    if (!check.canUninstall()) {
      throw new ResourceConflictException("Uninstall blocked", check.blockingReason());
    }

    // Delete all templates created by this install
    var templates = documentTemplateRepository.findBySourcePackInstallId(install.getId());
    documentTemplateRepository.deleteAll(templates);

    // Delete the PackInstall row
    packInstallRepository.delete(install);

    log.info(
        "Uninstalled template pack {} ({} templates removed) for tenant {}",
        packId,
        templates.size(),
        tenantId);
  }

  /**
   * Core uninstall gate logic. Private so it runs in whatever transaction the caller provides,
   * avoiding self-invocation proxy bypass.
   */
  private UninstallCheck computeUninstallCheck(PackInstall install) {
    var templates = documentTemplateRepository.findBySourcePackInstallId(install.getId());
    if (templates.isEmpty()) {
      return new UninstallCheck(true, null);
    }

    int totalCount = templates.size();
    List<String> reasons = new ArrayList<>();

    // Gate 1: Content hash mismatch (edited templates)
    int editedCount = 0;
    for (DocumentTemplate dt : templates) {
      String currentHash = computeTemplateHash(dt);
      if (dt.getContentHash() != null && !dt.getContentHash().equals(currentHash)) {
        editedCount++;
      }
    }
    if (editedCount > 0) {
      reasons.add(editedCount + " of " + totalCount + " templates have been edited");
    }

    // Gate 2: Generated document references (single bulk query, no N+1)
    List<UUID> templateIds = templates.stream().map(DocumentTemplate::getId).toList();
    long genCount =
        generatedDocumentRepository.countDistinctTemplatesWithGeneratedDocuments(templateIds);
    if (genCount > 0) {
      reasons.add(
          genCount
              + (genCount == 1 ? " template has" : " templates have")
              + " been used to generate documents");
    }

    // Gate 3: Clone references (single bulk query, no N+1)
    long cloneCount = documentTemplateRepository.countDistinctTemplatesWithClones(templateIds);
    if (cloneCount > 0) {
      reasons.add(
          cloneCount + (cloneCount == 1 ? " template has" : " templates have") + " been cloned");
    }

    if (reasons.isEmpty()) {
      return new UninstallCheck(true, null);
    }
    return new UninstallCheck(false, String.join("; ", reasons));
  }

  private String computeTemplateHash(DocumentTemplate dt) {
    return computeContentHash(dt.getContent(), dt.getCss());
  }

  /**
   * Computes the canonical content hash over a template's content JSON + CSS. Package-visible so
   * tests can pin hashes for simulated pack states.
   */
  String computeContentHash(Map<String, Object> content, String css) {
    if (content == null) {
      return null;
    }
    // Include both content and css in the hash
    var hashInput = new java.util.LinkedHashMap<String, Object>();
    hashInput.put("content", content);
    if (css != null) {
      hashInput.put("css", css);
    }
    var node = objectMapper.valueToTree(hashInput);
    String canonical = ContentHashUtil.canonicalizeJson(node);
    return ContentHashUtil.computeHash(canonical);
  }
}
