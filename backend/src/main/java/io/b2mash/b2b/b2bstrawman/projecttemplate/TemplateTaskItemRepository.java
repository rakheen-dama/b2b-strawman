package io.b2mash.b2b.b2bstrawman.projecttemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface TemplateTaskItemRepository extends JpaRepository<TemplateTaskItem, UUID> {
  List<TemplateTaskItem> findByTemplateTaskIdOrderBySortOrder(UUID templateTaskId);

  List<TemplateTaskItem> findByTemplateTaskIdInOrderBySortOrder(Collection<UUID> templateTaskIds);

  @Modifying(clearAutomatically = true)
  void deleteByTemplateTaskId(UUID templateTaskId);

  @Modifying(clearAutomatically = true)
  void deleteByTemplateTaskIdIn(Collection<UUID> templateTaskIds);
}
