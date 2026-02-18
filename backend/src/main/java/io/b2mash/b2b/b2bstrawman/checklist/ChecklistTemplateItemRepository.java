package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateItemRepository
    extends JpaRepository<ChecklistTemplateItem, UUID> {

  List<ChecklistTemplateItem> findByTemplateIdOrderBySortOrder(UUID templateId);

  void deleteByTemplateId(UUID templateId);
}
