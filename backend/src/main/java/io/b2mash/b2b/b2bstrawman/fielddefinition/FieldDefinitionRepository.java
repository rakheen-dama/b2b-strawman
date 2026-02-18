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
}
