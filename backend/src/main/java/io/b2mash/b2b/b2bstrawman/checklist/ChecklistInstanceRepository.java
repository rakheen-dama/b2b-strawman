package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistInstanceRepository extends JpaRepository<ChecklistInstance, UUID> {

  List<ChecklistInstance> findByCustomerId(UUID customerId);

  boolean existsByCustomerIdAndTemplateId(UUID customerId, UUID templateId);

  boolean existsByCustomerIdAndStatusNot(UUID customerId, String status);
}
