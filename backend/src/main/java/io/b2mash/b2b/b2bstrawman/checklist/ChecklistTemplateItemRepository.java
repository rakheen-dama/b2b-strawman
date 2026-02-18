package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistTemplateItemRepository
    extends JpaRepository<ChecklistTemplateItem, UUID> {

  List<ChecklistTemplateItem> findByTemplateIdOrderBySortOrder(UUID templateId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM ChecklistTemplateItem i WHERE i.templateId = :templateId")
  void deleteByTemplateId(@Param("templateId") UUID templateId);
}
