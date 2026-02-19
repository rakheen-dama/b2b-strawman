package io.b2mash.b2b.b2bstrawman.projecttemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateTaskRepository extends JpaRepository<TemplateTask, UUID> {
  List<TemplateTask> findByTemplateIdOrderBySortOrder(UUID templateId);

  void deleteByTemplateId(UUID templateId);
}
