package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistInstanceItemRepository
    extends JpaRepository<ChecklistInstanceItem, UUID> {

  List<ChecklistInstanceItem> findByInstanceIdOrderBySortOrder(UUID instanceId);

  List<ChecklistInstanceItem> findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(
      List<UUID> instanceIds);

  List<ChecklistInstanceItem> findByDependsOnItemIdAndStatus(UUID dependsOnItemId, String status);

  boolean existsByInstanceIdAndRequiredAndStatusNot(
      UUID instanceId, boolean required, String status);
}
