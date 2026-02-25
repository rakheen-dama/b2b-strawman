package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldGroupRepository extends JpaRepository<FieldGroup, UUID> {
  @Query(
      "SELECT fg FROM FieldGroup fg WHERE fg.entityType = :entityType AND fg.active = true"
          + " ORDER BY fg.sortOrder")
  List<FieldGroup> findByEntityTypeAndActiveTrueOrderBySortOrder(
      @Param("entityType") EntityType entityType);

  @Query("SELECT fg FROM FieldGroup fg WHERE fg.entityType = :entityType AND fg.slug = :slug")
  Optional<FieldGroup> findByEntityTypeAndSlug(
      @Param("entityType") EntityType entityType, @Param("slug") String slug);

  List<FieldGroup> findByEntityTypeAndAutoApplyTrueAndActiveTrue(EntityType entityType);
}
