package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {
  @Query(
      "SELECT fd FROM FieldDefinition fd WHERE fd.entityType = :entityType AND fd.active = true"
          + " ORDER BY fd.sortOrder")
  List<FieldDefinition> findByEntityTypeAndActiveTrueOrderBySortOrder(
      @Param("entityType") EntityType entityType);

  @Query("SELECT fd FROM FieldDefinition fd WHERE fd.entityType = :entityType AND fd.slug = :slug")
  Optional<FieldDefinition> findByEntityTypeAndSlug(
      @Param("entityType") EntityType entityType, @Param("slug") String slug);

  @Query(
      value =
          "SELECT * FROM field_definitions "
              + "WHERE entity_type = :entityType AND active = true "
              + "AND required_for_contexts @> CAST(:context AS jsonb) "
              + "ORDER BY sort_order",
      nativeQuery = true)
  List<FieldDefinition> findRequiredForContext(
      @Param("entityType") String entityType, @Param("context") String context);

  @Query(
      "SELECT CASE WHEN COUNT(fd) > 0 THEN true ELSE false END FROM FieldDefinition fd"
          + " WHERE fd.packId = :packId AND fd.active = true")
  boolean existsByPackIdAndActiveTrue(@Param("packId") String packId);

  @Query("SELECT fd FROM FieldDefinition fd WHERE fd.fieldType = :fieldType AND fd.active = true")
  List<FieldDefinition> findByFieldTypeAndActiveTrue(@Param("fieldType") FieldType fieldType);
}
