package io.b2mash.b2b.b2bstrawman.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface TaskItemRepository extends JpaRepository<TaskItem, UUID> {
  List<TaskItem> findByTaskIdOrderBySortOrder(UUID taskId);

  @Modifying
  void deleteByTaskId(UUID taskId);

  List<TaskItem> findByTaskIdInOrderBySortOrder(Collection<UUID> taskIds);

  long countByTaskIdAndCompleted(UUID taskId, boolean completed);
}
