package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldGroupRepository extends JpaRepository<FieldGroup, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT fg FROM FieldGroup fg WHERE fg.id = :id")
  Optional<FieldGroup> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT fg FROM FieldGroup fg WHERE fg.entityType = :entityType AND fg.active = true"
          + " ORDER BY fg.sortOrder")
  List<FieldGroup> findByEntityTypeAndActiveTrueOrderBySortOrder(
      @Param("entityType") String entityType);

  @Query("SELECT fg FROM FieldGroup fg WHERE fg.entityType = :entityType AND fg.slug = :slug")
  Optional<FieldGroup> findByEntityTypeAndSlug(
      @Param("entityType") String entityType, @Param("slug") String slug);
}
