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

  @Query("SELECT dt FROM DocumentTemplate dt WHERE dt.slug = :slug")
  Optional<DocumentTemplate> findBySlug(@Param("slug") String slug);

  @Query("SELECT dt FROM DocumentTemplate dt WHERE dt.packTemplateKey = :packTemplateKey")
  List<DocumentTemplate> findByPackTemplateKey(@Param("packTemplateKey") String packTemplateKey);

  @Query(
      "SELECT dt FROM DocumentTemplate dt WHERE dt.packId = :packId"
          + " AND dt.packTemplateKey = :packTemplateKey")
  Optional<DocumentTemplate> findByPackIdAndPackTemplateKey(
      @Param("packId") String packId, @Param("packTemplateKey") String packTemplateKey);
}
