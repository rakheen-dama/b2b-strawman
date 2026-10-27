package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
  List<AutomationRule> findByEnabledAndTriggerType(boolean enabled, TriggerType triggerType);

  List<AutomationRule> findByEnabled(boolean enabled);

  List<AutomationRule> findByTriggerType(TriggerType triggerType);

  List<AutomationRule> findAllByOrderByCreatedAtDesc();

  List<AutomationRule> findBySourcePackInstallId(UUID sourcePackInstallId);

  int countBySourcePackInstallId(UUID sourcePackInstallId);

  List<AutomationRule> findByTemplateSlugInAndSourcePackInstallIdIsNullAndSource(
      List<String> templateSlugs, RuleSource source);

  /**
   * Install-tagged rules with this template slug and source (ADR-356 reconcile target lookup; the
   * caller narrows to SYSTEM-created rows so API-activated copies are never targeted).
   */
  List<AutomationRule> findByTemplateSlugAndSourceAndSourcePackInstallIdIsNotNull(
      String templateSlug, RuleSource source);
}
