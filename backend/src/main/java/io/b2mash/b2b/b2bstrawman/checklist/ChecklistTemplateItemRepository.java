package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistTemplateItemRepository
    extends JpaRepository<ChecklistTemplateItem, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT cti FROM ChecklistTemplateItem cti WHERE cti.id = :id")
  Optional<ChecklistTemplateItem> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT cti FROM ChecklistTemplateItem cti WHERE cti.templateId = :templateId"
          + " ORDER BY cti.sortOrder")
  List<ChecklistTemplateItem> findByTemplateIdOrderBySortOrder(
      @Param("templateId") UUID templateId);

  @Modifying
  @Query(
      "DELETE FROM ChecklistTemplateItem cti WHERE cti.templateId = :templateId"
          + " AND cti.id NOT IN :keepIds")
  void deleteByTemplateIdAndIdNotIn(
      @Param("templateId") UUID templateId, @Param("keepIds") List<UUID> keepIds);
}
