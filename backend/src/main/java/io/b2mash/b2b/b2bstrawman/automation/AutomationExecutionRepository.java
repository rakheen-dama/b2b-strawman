package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
  List<AutomationExecution> findByRuleIdOrderByStartedAtDesc(UUID ruleId);

  List<AutomationExecution> findByStatus(ExecutionStatus status);

  List<AutomationExecution> findByRuleIdAndStatus(UUID ruleId, ExecutionStatus status);

  List<AutomationExecution> findAllByOrderByCreatedAtDesc();

  Page<AutomationExecution> findByRuleIdAndStatus(
      UUID ruleId, ExecutionStatus status, Pageable pageable);

  Page<AutomationExecution> findByRuleId(UUID ruleId, Pageable pageable);

  Page<AutomationExecution> findByStatus(ExecutionStatus status, Pageable pageable);

  Page<AutomationExecution> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
