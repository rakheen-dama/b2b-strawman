package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
  List<AutomationExecution> findByRuleIdOrderByStartedAtDesc(UUID ruleId);

  List<AutomationExecution> findByStatus(ExecutionStatus status);

  List<AutomationExecution> findByRuleIdAndStatus(UUID ruleId, ExecutionStatus status);

  List<AutomationExecution> findAllByOrderByCreatedAtDesc();
}
