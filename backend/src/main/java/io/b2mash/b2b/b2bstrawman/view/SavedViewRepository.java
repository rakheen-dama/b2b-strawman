package io.b2mash.b2b.b2bstrawman.view;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT v FROM SavedView v WHERE v.id = :id")
  Optional<SavedView> findOneById(@Param("id") UUID id);

  /** Returns all shared views for an entity type, ordered by sort_order. */
  @Query(
      "SELECT v FROM SavedView v WHERE v.entityType = :entityType AND v.shared = true ORDER BY"
          + " v.sortOrder")
  List<SavedView> findByEntityTypeAndSharedTrueOrderBySortOrder(
      @Param("entityType") String entityType);

  /** Returns all personal views for a specific member and entity type, ordered by sort_order. */
  @Query(
      "SELECT v FROM SavedView v WHERE v.entityType = :entityType AND v.createdBy = :createdBy"
          + " ORDER BY v.sortOrder")
  List<SavedView> findByEntityTypeAndCreatedByOrderBySortOrder(
      @Param("entityType") String entityType, @Param("createdBy") UUID createdBy);

  /** Checks for an existing shared view with the same entity type and name (duplicate check). */
  @Query(
      "SELECT v FROM SavedView v WHERE v.entityType = :entityType AND v.name = :name AND v.shared"
          + " = true")
  Optional<SavedView> findSharedByEntityTypeAndName(
      @Param("entityType") String entityType, @Param("name") String name);
}
