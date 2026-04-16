package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackDefinition;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // Create PackInstall row
    var install =
        new PackInstall(
            packId,
            PackType.DOCUMENT_TEMPLATE,
            String.valueOf(pack.version()),
            pack.name(),
            Instant.now(),
            memberId != null ? UUID.fromString(memberId) : null,
            pack.templates().size());
    install = packInstallRepository.save(install);

    // Check if templates already exist from a prior seeder run (e.g., during provisioning).
    // If so, tag them with this install. Otherwise, create them fresh.
    var existingTemplates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
    var untaggedPackTemplates =
        existingTemplates.stream()
            .filter(dt -> packId.equals(dt.getPackId()) && dt.getSourcePackInstallId() == null)
            .toList();

    UUID installId = install.getId();
    if (!untaggedPackTemplates.isEmpty()) {
      // Templates already created by seeder — tag them
      for (DocumentTemplate dt : untaggedPackTemplates) {
        dt.setSourcePackInstallId(installId);
        dt.setContentHash(computeTemplateHash(dt));
        documentTemplateRepository.save(dt);
      }
    } else {
      // No existing templates — create via seeder
      templatePackSeeder.applyPackContent(pack, loadedPack.resource(), tenantId);

      // Tag the newly created templates
      var allTemplates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
      for (DocumentTemplate dt : allTemplates) {
        if (packId.equals(dt.getPackId()) && dt.getSourcePackInstallId() == null) {
          dt.setSourcePackInstallId(installId);
          dt.setContentHash(computeTemplateHash(dt));
          documentTemplateRepository.save(dt);
        }
      }
    }

    log.info(
        "Installed template pack {} with {} templates for tenant {}",
        packId,
        pack.templates().size(),
        tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public UninstallCheck checkUninstallable(String packId, String tenantId) {
    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

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

    // Gate 2: Generated document references
    List<UUID> templateIds = templates.stream().map(DocumentTemplate::getId).toList();
    if (generatedDocumentRepository.existsByTemplateIdIn(templateIds)) {
      long genCount =
          templates.stream()
              .filter(dt -> generatedDocumentRepository.existsByTemplateIdIn(List.of(dt.getId())))
              .count();
      reasons.add(
          genCount
              + (genCount == 1 ? " template has" : " templates have")
              + " been used to generate documents");
    }

    // Gate 3: Clone references
    if (documentTemplateRepository.existsBySourceTemplateIdIn(templateIds)) {
      long cloneCount =
          templates.stream()
              .filter(
                  dt -> documentTemplateRepository.existsBySourceTemplateIdIn(List.of(dt.getId())))
              .count();
      reasons.add(
          cloneCount + (cloneCount == 1 ? " template has" : " templates have") + " been cloned");
    }

    if (reasons.isEmpty()) {
      return new UninstallCheck(true, null);
    }
    return new UninstallCheck(false, String.join("; ", reasons));
  }

  @Override
  @Transactional
  public void uninstall(String packId, String tenantId, String memberId) {
    var check = checkUninstallable(packId, tenantId);
    if (!check.canUninstall()) {
      throw new ResourceConflictException("Uninstall blocked", check.blockingReason());
    }

    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

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

  private String computeTemplateHash(DocumentTemplate dt) {
    if (dt.getContent() == null) {
      return null;
    }
    var node = objectMapper.valueToTree(dt.getContent());
    String canonical = ContentHashUtil.canonicalizeJson(node);
    return ContentHashUtil.computeHash(canonical);
  }
}
