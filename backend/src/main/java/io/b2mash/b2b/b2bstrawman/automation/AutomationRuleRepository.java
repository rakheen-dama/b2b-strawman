package io.b2mash.b2b.b2bstrawman.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
   * Rules with this template slug and source tagged with the {@code pack_install} row of {@code
   * packId} (ADR-356 reconcile target lookup — a same-slug rule from another pack's install is
   * never returned; the caller narrows to SYSTEM-created rows so API-activated copies are never
   * targeted).
   */
  @Query(
      "SELECT r FROM AutomationRule r WHERE r.templateSlug = :templateSlug AND r.source = :source"
          + " AND r.sourcePackInstallId IN"
          + " (SELECT p.id FROM PackInstall p WHERE p.packId = :packId)")
  List<AutomationRule> findPackSeededByTemplateSlug(
      @Param("packId") String packId,
      @Param("templateSlug") String templateSlug,
      @Param("source") RuleSource source);
}
