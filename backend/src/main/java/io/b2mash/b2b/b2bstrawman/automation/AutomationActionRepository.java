package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationActionRepository extends JpaRepository<AutomationAction, UUID> {
  List<AutomationAction> findByRuleIdOrderBySortOrder(UUID ruleId);

  List<AutomationAction> findByRuleIdInOrderBySortOrder(List<UUID> ruleIds);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM AutomationAction a WHERE a.ruleId = :ruleId")
  void deleteByRuleId(@Param("ruleId") UUID ruleId);
}
