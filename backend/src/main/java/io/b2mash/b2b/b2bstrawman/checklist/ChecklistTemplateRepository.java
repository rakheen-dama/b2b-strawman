package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

  List<ChecklistTemplate> findByActiveOrderBySortOrder(boolean active);

  List<ChecklistTemplate> findByActiveAndAutoInstantiateAndCustomerTypeIn(
      boolean active, boolean autoInstantiate, List<String> customerTypes);

  boolean existsBySlug(String slug);

  Optional<ChecklistTemplate> findBySlug(String slug);
}
