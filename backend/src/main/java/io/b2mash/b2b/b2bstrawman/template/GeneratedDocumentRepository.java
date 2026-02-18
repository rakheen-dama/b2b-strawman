package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
  @Query(
      "SELECT gd FROM GeneratedDocument gd WHERE gd.primaryEntityType = :entityType"
          + " AND gd.primaryEntityId = :entityId ORDER BY gd.generatedAt DESC")
  List<GeneratedDocument> findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
      @Param("entityType") TemplateEntityType entityType, @Param("entityId") UUID entityId);

  @Query(
      "SELECT gd FROM GeneratedDocument gd WHERE gd.templateId = :templateId ORDER BY gd.generatedAt DESC")
  List<GeneratedDocument> findByTemplateIdOrderByGeneratedAtDesc(
      @Param("templateId") UUID templateId);

  @Query(
      "SELECT gd FROM GeneratedDocument gd WHERE gd.generatedBy = :generatedBy ORDER BY gd.generatedAt DESC")
  List<GeneratedDocument> findByGeneratedByOrderByGeneratedAtDesc(
      @Param("generatedBy") UUID generatedBy);
}
