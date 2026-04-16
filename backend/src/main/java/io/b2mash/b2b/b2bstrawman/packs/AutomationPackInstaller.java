package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationExecutionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Installs and uninstalls automation template packs. Wraps {@link
 * AutomationTemplateSeeder#applyPackContent} with {@link PackInstall} tracking, content hash
 * computation, and uninstall gate checks.
 */
@Service
public class AutomationPackInstaller implements PackInstaller {

  private static final Logger log = LoggerFactory.getLogger(AutomationPackInstaller.class);

  private final PackInstallRepository packInstallRepository;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final AutomationTemplateSeeder automationTemplateSeeder;
  private final ObjectMapper objectMapper;

  public AutomationPackInstaller(
      PackInstallRepository packInstallRepository,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationExecutionRepository executionRepository,
      AutomationTemplateSeeder automationTemplateSeeder,
      ObjectMapper objectMapper) {
    this.packInstallRepository = packInstallRepository;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.executionRepository = executionRepository;
    this.automationTemplateSeeder = automationTemplateSeeder;
    this.objectMapper = objectMapper;
  }

  @Override
  public PackType type() {
    return PackType.AUTOMATION_TEMPLATE;
  }

  @Override
  public List<PackCatalogEntry> availablePacks() {
    return automationTemplateSeeder.getAvailablePacks().stream()
        .map(
            loaded -> {
              var pack = loaded.definition();
              return new PackCatalogEntry(
                  pack.packId(),
                  pack.packId(),
                  null,
                  String.valueOf(pack.version()),
                  PackType.AUTOMATION_TEMPLATE,
                  pack.verticalProfile(),
                  pack.templates().size(),
                  false,
                  null);
            })
        .toList();
  }

  @Override
  @Transactional
  public void install(String packId, String tenantId, String memberId) {
    // Idempotency check
    if (packInstallRepository.findByPackId(packId).isPresent()) {
      log.info("Automation pack {} already installed, skipping", packId);
      return;
    }

    var loadedPack =
        automationTemplateSeeder.getAvailablePacks().stream()
            .filter(lp -> packId.equals(lp.definition().packId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Pack", packId));

    AutomationTemplatePack pack = loadedPack.definition();

    // Create PackInstall row
    var install =
        new PackInstall(
            packId,
            PackType.AUTOMATION_TEMPLATE,
            String.valueOf(pack.version()),
            pack.packId(),
            Instant.now(),
            memberId != null ? UUID.fromString(memberId) : null,
            pack.templates().size());
    install = packInstallRepository.save(install);

    // Check if rules already exist from a prior seeder run (e.g., during provisioning).
    // If so, tag them with this install. Otherwise, create them fresh.
    var allRules = ruleRepository.findAllByOrderByCreatedAtDesc();
    UUID installId = install.getId();
    var untaggedRules =
        allRules.stream()
            .filter(
                rule ->
                    rule.getSourcePackInstallId() == null
                        && io.b2mash.b2b.b2bstrawman.automation.RuleSource.TEMPLATE.equals(
                            rule.getSource())
                        && pack.templates().stream()
                            .anyMatch(t -> t.slug().equals(rule.getTemplateSlug())))
            .toList();

    if (!untaggedRules.isEmpty()) {
      // Rules already created by seeder — tag them
      for (AutomationRule rule : untaggedRules) {
        rule.setSourcePackInstallId(installId);
        rule.setContentHash(computeRuleHash(rule));
        ruleRepository.save(rule);
      }
    } else {
      // No existing rules — create via seeder
      automationTemplateSeeder.applyPackContent(pack, loadedPack.resource(), tenantId);

      // Tag the newly created rules
      var freshRules = ruleRepository.findAllByOrderByCreatedAtDesc();
      for (AutomationRule rule : freshRules) {
        if (rule.getSourcePackInstallId() == null
            && io.b2mash.b2b.b2bstrawman.automation.RuleSource.TEMPLATE.equals(rule.getSource())) {
          boolean belongsToPack =
              pack.templates().stream().anyMatch(t -> t.slug().equals(rule.getTemplateSlug()));
          if (belongsToPack) {
            rule.setSourcePackInstallId(installId);
            rule.setContentHash(computeRuleHash(rule));
            ruleRepository.save(rule);
          }
        }
      }
    }

    log.info(
        "Installed automation pack {} with {} rules for tenant {}",
        packId,
        pack.templates().size(),
        tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public UninstallCheck checkUninstallable(String packId, String tenantId) {
    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    var rules = ruleRepository.findBySourcePackInstallId(install.getId());
    if (rules.isEmpty()) {
      return new UninstallCheck(true, null);
    }

    int totalCount = rules.size();
    List<String> reasons = new ArrayList<>();

    // Gate 1: Content hash mismatch (edited rules)
    int editedCount = 0;
    for (AutomationRule rule : rules) {
      String currentHash = computeRuleHash(rule);
      if (rule.getContentHash() != null && !rule.getContentHash().equals(currentHash)) {
        editedCount++;
      }
    }
    if (editedCount > 0) {
      reasons.add(editedCount + " of " + totalCount + " rules have been edited");
    }

    // Gate 2: Execution references
    List<UUID> ruleIds = rules.stream().map(AutomationRule::getId).toList();
    if (executionRepository.existsByRuleIdIn(ruleIds)) {
      long execCount =
          rules.stream()
              .filter(rule -> executionRepository.existsByRuleIdIn(List.of(rule.getId())))
              .count();
      reasons.add(execCount + (execCount == 1 ? " rule has" : " rules have") + " been executed");
    }

    if (reasons.isEmpty()) {
      return new UninstallCheck(true, null);
    }
    return new UninstallCheck(false, String.join("; ", reasons));
  }

  @Override
  @Transactional
  public void uninstall(String packId, String tenantId, String memberId) {
    var check = checkUninstallable(packId, tenantId);
    if (!check.canUninstall()) {
      throw new ResourceConflictException("Uninstall blocked", check.blockingReason());
    }

    var install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    // Delete all rules and their actions created by this install
    var rules = ruleRepository.findBySourcePackInstallId(install.getId());
    for (AutomationRule rule : rules) {
      actionRepository.deleteByRuleId(rule.getId());
    }
    ruleRepository.deleteAll(rules);

    // Delete the PackInstall row
    packInstallRepository.delete(install);

    log.info(
        "Uninstalled automation pack {} ({} rules removed) for tenant {}",
        packId,
        rules.size(),
        tenantId);
  }

  private String computeRuleHash(AutomationRule rule) {
    Map<String, Object> hashInput = new LinkedHashMap<>();
    hashInput.put("triggerConfig", rule.getTriggerConfig());
    hashInput.put("conditions", rule.getConditions());

    List<AutomationAction> actions = actionRepository.findByRuleIdOrderBySortOrder(rule.getId());
    List<Map<String, Object>> actionMaps =
        actions.stream()
            .map(
                a -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("actionType", a.getActionType().name());
                  m.put("actionConfig", a.getActionConfig());
                  m.put("sortOrder", a.getSortOrder());
                  m.put("delayDuration", a.getDelayDuration());
                  m.put("delayUnit", a.getDelayUnit() != null ? a.getDelayUnit().name() : null);
                  return m;
                })
            .toList();
    hashInput.put("actions", actionMaps);

    var node = objectMapper.valueToTree(hashInput);
    String canonical = ContentHashUtil.canonicalizeJson(node);
    return ContentHashUtil.computeHash(canonical);
  }
}
