package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT gd FROM GeneratedDocument gd WHERE gd.id = :id")
  Optional<GeneratedDocument> findOneById(@Param("id") UUID id);

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
