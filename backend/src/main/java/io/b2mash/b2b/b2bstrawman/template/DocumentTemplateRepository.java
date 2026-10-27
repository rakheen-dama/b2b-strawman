package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
  @Query("SELECT dt FROM DocumentTemplate dt WHERE dt.active = true ORDER BY dt.sortOrder, dt.name")
  List<DocumentTemplate> findByActiveTrueOrderBySortOrder();

  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.category = :category AND dt.active = true"
          + " ORDER BY dt.sortOrder, dt.name")
  List<DocumentTemplate> findByCategoryAndActiveTrueOrderBySortOrder(
      @Param("category") TemplateCategory category);

  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.primaryEntityType = :entityType"
          + " AND dt.active = true ORDER BY dt.sortOrder, dt.name")
  List<DocumentTemplate> findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
      @Param("entityType") TemplateEntityType entityType);

  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.format = :format AND dt.active = true"
          + " ORDER BY dt.sortOrder, dt.name")
  List<DocumentTemplate> findByFormatAndActiveTrueOrderBySortOrder(
      @Param("format") TemplateFormat format);

  @Query("SELECT dt FROM DocumentTemplate dt WHERE dt.slug = :slug")
  Optional<DocumentTemplate> findBySlug(@Param("slug") String slug);

  @Query("SELECT dt FROM DocumentTemplate dt WHERE dt.packTemplateKey = :packTemplateKey")
  List<DocumentTemplate> findByPackTemplateKey(@Param("packTemplateKey") String packTemplateKey);

  /**
   * The pack-seeded rows for a pack key: {@code PLATFORM} and not a clone, oldest first. Clones
   * share {@code packId} + {@code packTemplateKey} with their source ({@code
   * DocumentTemplateService.cloneTemplate}), so never look a pack template up by those two columns
   * alone. A {@code List} because no unique constraint backs the pair — callers decide what more
   * than one row means (the pack installer WARNs and skips; read paths take the first).
   */
  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.packId = :packId"
          + " AND dt.packTemplateKey = :packTemplateKey"
          + " AND dt.source = io.b2mash.b2b.b2bstrawman.template.TemplateSource.PLATFORM"
          + " AND dt.sourceTemplateId IS NULL"
          + " ORDER BY dt.createdAt, dt.id")
  List<DocumentTemplate> findPackSeeded(
      @Param("packId") String packId, @Param("packTemplateKey") String packTemplateKey);

  /**
   * The pack-seeded rows of one pack install for a pack key — {@code PLATFORM}, not a clone, tagged
   * with that install ({@code source_pack_install_id}): the ADR-356 reconcile target lookup ({@code
   * TemplatePackSeeder.resolveReconcileTarget}). A clone never matches, even if tagged.
   */
  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.sourcePackInstallId = :installId"
          + " AND dt.packTemplateKey = :packTemplateKey"
          + " AND dt.source = io.b2mash.b2b.b2bstrawman.template.TemplateSource.PLATFORM"
          + " AND dt.sourceTemplateId IS NULL"
          + " ORDER BY dt.createdAt, dt.id")
  List<DocumentTemplate> findPackSeededByInstall(
      @Param("installId") UUID installId, @Param("packTemplateKey") String packTemplateKey);

  @Query(
      value =
          "SELECT * FROM document_templates WHERE active = true"
              + " AND CAST(content AS text) LIKE %:variableKey%",
      nativeQuery = true)
  List<DocumentTemplate> findActiveTemplatesContainingVariable(
      @Param("variableKey") String variableKey);

  List<DocumentTemplate> findBySourcePackInstallId(UUID sourcePackInstallId);

  int countBySourcePackInstallId(UUID sourcePackInstallId);

  boolean existsBySourceTemplateIdIn(List<UUID> templateIds);

  @Query(
      "SELECT COUNT(DISTINCT dt.sourceTemplateId) FROM DocumentTemplate dt"
          + " WHERE dt.sourceTemplateId IN :templateIds")
  long countDistinctTemplatesWithClones(@Param("templateIds") List<UUID> templateIds);

  /**
   * Pack-seeded rows of a pack not yet tagged with a pack install ({@code PLATFORM}, not a clone):
   * what {@code TemplatePackInstaller} tags after seeding. Clones copy {@code packId} and leave
   * {@code source_pack_install_id} null, so a {@code packId}-only query would tag them too.
   */
  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.packId = :packId"
          + " AND dt.sourcePackInstallId IS NULL"
          + " AND dt.source = io.b2mash.b2b.b2bstrawman.template.TemplateSource.PLATFORM"
          + " AND dt.sourceTemplateId IS NULL")
  List<DocumentTemplate> findUntaggedPackSeeded(@Param("packId") String packId);
}
