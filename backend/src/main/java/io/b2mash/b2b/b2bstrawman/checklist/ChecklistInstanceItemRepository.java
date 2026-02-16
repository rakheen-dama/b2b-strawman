package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistInstanceItemRepository
    extends JpaRepository<ChecklistInstanceItem, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT cii FROM ChecklistInstanceItem cii WHERE cii.id = :id")
  Optional<ChecklistInstanceItem> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT cii FROM ChecklistInstanceItem cii WHERE cii.instanceId = :instanceId"
          + " ORDER BY cii.sortOrder")
  List<ChecklistInstanceItem> findByInstanceIdOrderBySortOrder(
      @Param("instanceId") UUID instanceId);

  @Query(
      "SELECT COUNT(cii) FROM ChecklistInstanceItem cii"
          + " WHERE cii.instanceId = :instanceId AND cii.required = :required")
  long countByInstanceIdAndRequired(
      @Param("instanceId") UUID instanceId, @Param("required") boolean required);

  @Query(
      "SELECT COUNT(cii) FROM ChecklistInstanceItem cii"
          + " WHERE cii.instanceId = :instanceId AND cii.required = :required"
          + " AND cii.status = :status")
  long countByInstanceIdAndRequiredAndStatus(
      @Param("instanceId") UUID instanceId,
      @Param("required") boolean required,
      @Param("status") String status);

  @Query("SELECT cii FROM ChecklistInstanceItem cii WHERE cii.dependsOnItemId = :itemId")
  List<ChecklistInstanceItem> findByDependsOnItemId(@Param("itemId") UUID itemId);
}
