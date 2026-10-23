package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistInstanceRepository extends JpaRepository<ChecklistInstance, UUID> {

  List<ChecklistInstance> findByCustomerId(UUID customerId);

  List<ChecklistInstance> findByCustomerIdIn(List<UUID> customerIds);

  /**
   * The given clients' onboarding checklist instances (any status): instances whose template has
   * {@code auto_instantiate = true} — the product's own definition of an onboarding template, the
   * set Start Onboarding instantiates (ADR-348, arch §13.3.6 A1). No {@code active} filter: a
   * deactivated onboarding template's instances still record the client's onboarding. The template
   * selector is a subquery so the FICA batch stays one statement here (Clients list cap, Epic
   * 615B).
   */
  @Query(
      """
      SELECT i FROM ChecklistInstance i
      WHERE i.customerId IN :customerIds
        AND i.templateId IN (SELECT t.id FROM ChecklistTemplate t WHERE t.autoInstantiate = true)
      """)
  List<ChecklistInstance> findOnboardingByCustomerIdIn(
      @Param("customerIds") Collection<UUID> customerIds);

  boolean existsByCustomerIdAndTemplateId(UUID customerId, UUID templateId);

  boolean existsByCustomerIdAndStatusNot(UUID customerId, String status);
}
