package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistInstanceRepository extends JpaRepository<ChecklistInstance, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT ci FROM ChecklistInstance ci WHERE ci.id = :id")
  Optional<ChecklistInstance> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT ci FROM ChecklistInstance ci WHERE ci.customerId = :customerId"
          + " ORDER BY ci.startedAt DESC")
  List<ChecklistInstance> findByCustomerIdOrderByStartedAtDesc(
      @Param("customerId") UUID customerId);

  @Query(
      "SELECT COUNT(ci) FROM ChecklistInstance ci WHERE ci.customerId = :customerId"
          + " AND ci.status NOT IN :excludedStatuses")
  long countByCustomerIdAndStatusNotIn(
      @Param("customerId") UUID customerId,
      @Param("excludedStatuses") List<String> excludedStatuses);

  @Query(
      "SELECT CASE WHEN COUNT(ci) > 0 THEN true ELSE false END FROM ChecklistInstance ci"
          + " WHERE ci.customerId = :customerId AND ci.templateId = :templateId")
  boolean existsByCustomerIdAndTemplateId(
      @Param("customerId") UUID customerId, @Param("templateId") UUID templateId);
}
