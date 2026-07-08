package io.b2mash.b2b.b2bstrawman.automation.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.TemplateActionDefinition;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit test for {@link AutomationTemplateSeeder}'s handling of the optional {@code defaultEnabled}
 * template flag (LZKC-013). No Spring context — repositories are mocked and {@code
 * applyPackContent} is invoked directly.
 */
class AutomationTemplateSeederTest {

  private AutomationRuleRepository ruleRepository;
  private AutomationTemplateSeeder seeder;

  @BeforeEach
  void setUp() {
    ruleRepository = mock(AutomationRuleRepository.class);
    when(ruleRepository.save(any(AutomationRule.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var actionRepository = mock(AutomationActionRepository.class);
    when(actionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    seeder =
        new AutomationTemplateSeeder(
            mock(ResourcePatternResolver.class),
            new ObjectMapper(),
            ruleRepository,
            actionRepository,
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class));
  }

  @Test
  void applyPack_defaultEnabledFalse_seedsRuleDisabled_othersStayEnabled() {
    var pack =
        new AutomationTemplatePack(
            "test-pack",
            1,
            null,
            List.of(
                template("opt-out-rule", Boolean.FALSE),
                template("absent-flag-rule", null),
                template("explicit-true-rule", Boolean.TRUE)));

    seeder.applyPackContent(pack, null, "tenant_test");

    ArgumentCaptor<AutomationRule> captor = ArgumentCaptor.forClass(AutomationRule.class);
    Mockito.verify(ruleRepository, Mockito.times(3)).save(captor.capture());
    List<AutomationRule> savedRules = captor.getAllValues();

    assertThat(savedRules).hasSize(3);
    assertThat(ruleBySlug(savedRules, "opt-out-rule").isEnabled())
        .as("defaultEnabled=false must seed the rule disabled")
        .isFalse();
    assertThat(ruleBySlug(savedRules, "absent-flag-rule").isEnabled())
        .as("absent defaultEnabled must keep the historical enabled default")
        .isTrue();
    assertThat(ruleBySlug(savedRules, "explicit-true-rule").isEnabled())
        .as("defaultEnabled=true must seed the rule enabled")
        .isTrue();
  }

  @Test
  void seedEnabled_onlyExplicitFalseDisables() {
    assertThat(template("a", Boolean.FALSE).seedEnabled()).isFalse();
    assertThat(template("b", null).seedEnabled()).isTrue();
    assertThat(template("c", Boolean.TRUE).seedEnabled()).isTrue();
  }

  private static AutomationRule ruleBySlug(List<AutomationRule> rules, String slug) {
    return rules.stream()
        .filter(r -> slug.equals(r.getTemplateSlug()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No saved rule with slug " + slug));
  }

  private static AutomationTemplateDefinition template(String slug, Boolean defaultEnabled) {
    return new AutomationTemplateDefinition(
        slug,
        "Rule " + slug,
        "Test rule " + slug,
        "workflow",
        TriggerType.TASK_STATUS_CHANGED,
        Map.of("toStatus", "DONE"),
        List.of(),
        List.of(
            new TemplateActionDefinition(
                ActionType.CREATE_TASK, Map.of("taskName", "Follow-up"), null, null, 0)),
        defaultEnabled);
  }
}
