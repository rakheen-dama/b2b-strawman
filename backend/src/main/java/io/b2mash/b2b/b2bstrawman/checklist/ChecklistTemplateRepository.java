package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT ct FROM ChecklistTemplate ct WHERE ct.id = :id")
  Optional<ChecklistTemplate> findOneById(@Param("id") UUID id);

  @Query("SELECT ct FROM ChecklistTemplate ct WHERE ct.slug = :slug")
  Optional<ChecklistTemplate> findBySlug(@Param("slug") String slug);

  @Query(
      "SELECT ct FROM ChecklistTemplate ct WHERE ct.active = true ORDER BY ct.sortOrder, ct.name")
  List<ChecklistTemplate> findByActiveTrueOrderBySortOrder();

  @Query(
      "SELECT COUNT(ct) FROM ChecklistTemplate ct WHERE ct.customerType = :customerType"
          + " AND ct.autoInstantiate = true AND ct.active = true")
  long countByCustomerTypeAndAutoInstantiateTrueAndActiveTrue(
      @Param("customerType") String customerType);

  @Query(
      "SELECT ct FROM ChecklistTemplate ct WHERE ct.active = true"
          + " AND ct.autoInstantiate = true AND ct.customerType = :customerType")
  Optional<ChecklistTemplate> findByActiveTrueAndAutoInstantiateTrueAndCustomerType(
      @Param("customerType") String customerType);

  @Query(
      "SELECT ct FROM ChecklistTemplate ct WHERE ct.active = true"
          + " AND ct.autoInstantiate = true"
          + " AND (ct.customerType = 'ANY' OR ct.customerType = :customerType)"
          + " ORDER BY ct.sortOrder, ct.name")
  List<ChecklistTemplate> findAutoInstantiateTemplatesForCustomerType(
      @Param("customerType") String customerType);
}
