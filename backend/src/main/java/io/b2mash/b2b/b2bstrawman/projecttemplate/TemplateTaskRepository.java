package io.b2mash.b2b.b2bstrawman.projecttemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateTaskRepository extends JpaRepository<TemplateTask, UUID> {
  List<TemplateTask> findByTemplateIdOrderBySortOrder(UUID templateId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM TemplateTask t WHERE t.templateId = :templateId")
  void deleteByTemplateId(@Param("templateId") UUID templateId);
}
