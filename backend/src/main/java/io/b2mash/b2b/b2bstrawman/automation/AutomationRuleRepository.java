package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
  List<AutomationRule> findByEnabledAndTriggerType(boolean enabled, TriggerType triggerType);
}
