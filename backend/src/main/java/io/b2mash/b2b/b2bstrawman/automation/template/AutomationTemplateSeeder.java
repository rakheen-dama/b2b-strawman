package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads automation template packs ({@code classpath:automation-templates/*.json}) and creates their
 * rules for a tenant. Automation packs are installer-managed: new tenants get them through {@code
 * AutomationPackInstaller.install} → {@link #applyPackContent}; existing tenants reach the ADR-356
 * {@code reconcile} block through {@code AutomationPackInstaller.reconcile} (version gate) → {@link
 * #reconcileInstalledPack}, which resolves {@code automation_rule} entries via {@link
 * #resolveReconcileTarget}.
 */
@Service
public class AutomationTemplateSeeder extends AbstractPackSeeder<AutomationTemplatePack> {

  private static final String PACK_LOCATION = "classpath:automation-templates/*.json";
  private static final UUID SYSTEM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final String RULE_ENTITY = "automation_rule";

  /**
   * {@code actions[<sortOrder>].actionConfig.<key>} — a string value inside one action's config.
   */
  private static final Pattern ACTION_CONFIG_FIELD =
      Pattern.compile("actions\\[(\\d{1,9})]\\.actionConfig\\.([A-Za-z][A-Za-z0-9]*)");

  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;

  public AutomationTemplateSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    super(
        resourceResolver,
        objectMapper,
        orgSettingsRepository,
        tenantTransactionHelper,
        packVersionGate);
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<AutomationTemplatePack> getPackDefinitionType() {
    return AutomationTemplatePack.class;
  }

  @Override
  protected String getPackTypeName() {
    return "automation";
  }

  @Override
  protected PackType getPackType() {
    return PackType.AUTOMATION_TEMPLATE;
  }

  @Override
  protected String getPackId(AutomationTemplatePack pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(AutomationTemplatePack pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(AutomationTemplatePack pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    return settings.getPackStatus().isAutomationPackApplied(packId);
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, AutomationTemplatePack pack) {
    settings.getPackStatus().recordAutomationPackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(AutomationTemplatePack pack, Resource packResource, String tenantId) {
    for (var template : pack.templates()) {
      var rule =
          new AutomationRule(
              template.name(),
              template.description(),
              template.triggerType(),
              template.triggerConfig(),
              template.conditions(),
              RuleSource.TEMPLATE,
              template.slug(),
              SYSTEM_USER_ID);
      if (!template.seedEnabled()) {
        // LZKC-013: template opted out of default-on seeding (e.g. task-completion-chain).
        // The constructor sets enabled=true, so flip it before the initial save.
        rule.toggle();
      }
      rule = ruleRepository.save(rule);

      for (var actionDef : template.actions()) {
        var action =
            new AutomationAction(
                rule.getId(),
                actionDef.sortOrder(),
                actionDef.actionType(),
                actionDef.actionConfig(),
                actionDef.delayDuration(),
                actionDef.delayUnit());
        actionRepository.save(action);
      }
    }
  }

  /**
   * ADR-356 resolver for {@code automation_rule} entries: the single pack-seeded rule with this
   * {@code templateSlug} (source TEMPLATE, tagged with THIS pack's install, created by the system
   * user — another pack's same-slug rule and API-activated copies of a template are never targets).
   * Supported fields: {@code name}, {@code description}, {@code enabled} (boolean) and {@code
   * actions[<sortOrder>].actionConfig.<key>} (string). Anything else is logged at WARN and skipped
   * — never thrown, so one bad entry cannot abort a tenant's startup reconcile. Targets mutate the
   * managed entities only; {@code AutomationPackInstaller} re-pins content hashes.
   */
  @Override
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      AutomationTemplatePack pack, PackReconcileEntry entry, Resource packResource) {
    if (!RULE_ENTITY.equals(entry.entity())) {
      log.warn(
          "Reconcile entry ignored: automation pack {} has unsupported entity '{}' (key {})",
          pack.packId(),
          entry.entity(),
          entry.key());
      return Optional.empty();
    }
    List<AutomationRule> rules =
        ruleRepository
            .findPackSeededByTemplateSlug(pack.packId(), entry.key(), RuleSource.TEMPLATE)
            .stream()
            .filter(r -> SYSTEM_USER_ID.equals(r.getCreatedBy()))
            .toList();
    if (rules.size() > 1) {
      log.warn(
          "Reconcile entry ignored: {} pack-seeded rules share slug '{}' (automation pack {})",
          rules.size(),
          entry.key(),
          pack.packId());
      return Optional.empty();
    }
    if (rules.isEmpty()) {
      return Optional.empty(); // the framework logs "Reconcile skipped (no pack-seeded row)"
    }
    AutomationRule rule = rules.getFirst();
    return switch (entry.field()) {
      case "name" ->
          stringTarget(
              pack,
              entry,
              rule::getName,
              v ->
                  rule.update(
                      v,
                      rule.getDescription(),
                      rule.getTriggerType(),
                      rule.getTriggerConfig(),
                      rule.getConditions()));
      case "description" ->
          stringTarget(
              pack,
              entry,
              rule::getDescription,
              v ->
                  rule.update(
                      rule.getName(),
                      v,
                      rule.getTriggerType(),
                      rule.getTriggerConfig(),
                      rule.getConditions()));
      case "enabled" -> {
        if (!(entry.to() instanceof Boolean)) {
          yield unsupported(pack, entry);
        }
        yield Optional.of(
            target(
                rule::isEnabled,
                to -> {
                  if (rule.isEnabled() != (Boolean) to) {
                    rule.toggle();
                  }
                }));
      }
      default -> actionConfigTarget(pack, entry, rule);
    };
  }

  /** {@code actions[<sortOrder>].actionConfig.<key>}: a string value in one action's config. */
  private Optional<PackReconcileEntry.Target> actionConfigTarget(
      AutomationTemplatePack pack, PackReconcileEntry entry, AutomationRule rule) {
    Matcher m = ACTION_CONFIG_FIELD.matcher(entry.field());
    if (!m.matches() || !(entry.to() instanceof String)) {
      return unsupported(pack, entry);
    }
    int sortOrder = Integer.parseInt(m.group(1));
    String key = m.group(2);
    return actionRepository.findByRuleIdOrderBySortOrder(rule.getId()).stream()
        .filter(a -> a.getSortOrder() == sortOrder)
        .findFirst()
        .map(
            a ->
                target(
                    () -> a.getActionConfig() == null ? null : a.getActionConfig().get(key),
                    to -> {
                      // A new map, so Hibernate's JSON dirty check sees the change.
                      Map<String, Object> config =
                          new LinkedHashMap<>(
                              a.getActionConfig() == null ? Map.of() : a.getActionConfig());
                      config.put(key, to);
                      a.update(
                          a.getActionType(),
                          config,
                          a.getSortOrder(),
                          a.getDelayDuration(),
                          a.getDelayUnit());
                    }));
  }

  private Optional<PackReconcileEntry.Target> stringTarget(
      AutomationTemplatePack pack,
      PackReconcileEntry entry,
      Supplier<Object> get,
      Consumer<String> set) {
    if (!(entry.to() instanceof String)) {
      return unsupported(pack, entry);
    }
    return Optional.of(target(get, to -> set.accept((String) to)));
  }

  private Optional<PackReconcileEntry.Target> unsupported(
      AutomationTemplatePack pack, PackReconcileEntry entry) {
    log.warn(
        "Reconcile entry ignored: automation pack {} rule '{}' unsupported field '{}' or value"
            + " type",
        pack.packId(),
        entry.key(),
        entry.field());
    return Optional.empty();
  }

  private static PackReconcileEntry.Target target(Supplier<Object> get, Consumer<Object> set) {
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return get.get();
      }

      @Override
      public void apply(Object to) {
        set.accept(to);
      }
    };
  }

  /**
   * Returns all available automation packs loaded from the classpath. Used by {@code
   * AutomationPackInstaller} to build the pack catalog.
   */
  public List<LoadedPack<AutomationTemplatePack>> getAvailablePacks() {
    return loadPacks();
  }

  /**
   * Applies a single pack's content (creates AutomationRule and AutomationAction rows). Public
   * delegate for {@link #applyPack} to allow cross-package access from {@code
   * AutomationPackInstaller}.
   */
  public void applyPackContent(
      AutomationTemplatePack pack, Resource packResource, String tenantId) {
    applyPack(pack, packResource, tenantId);
  }
}
