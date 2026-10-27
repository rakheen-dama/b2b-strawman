package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationExecutionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder.LoadedPack;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Installer → reconcile-hook wiring for automation packs (ADR-356): the hook runs only when the
 * real {@link PackVersionGate} opens, and the installer — not the reconcile target — re-pins the
 * content hash of rules that were pristine before the hook (e.g. 625F's action-config title
 * reconcile). Pure Mockito, no Spring context.
 */
class AutomationPackInstallerReconcileTest {

  private static final String PACK_ID = "test-automations";
  private static final String TENANT = "tenant_test";

  private final PackInstallRepository packInstallRepository = mock(PackInstallRepository.class);
  private final AutomationRuleRepository ruleRepository = mock(AutomationRuleRepository.class);
  private final AutomationActionRepository actionRepository =
      mock(AutomationActionRepository.class);
  private final AutomationExecutionRepository executionRepository =
      mock(AutomationExecutionRepository.class);
  private final AutomationTemplateSeeder seeder = mock(AutomationTemplateSeeder.class);
  private final Resource resource = new ByteArrayResource(new byte[0]);

  private AutomationPackInstaller installer;
  private UUID installId;

  @BeforeEach
  void setUp() {
    installer =
        new AutomationPackInstaller(
            packInstallRepository,
            ruleRepository,
            actionRepository,
            executionRepository,
            seeder,
            new ObjectMapper(),
            new PackVersionGate(packInstallRepository));
    installId = UUID.randomUUID();
    when(packInstallRepository.compareAndSetVersion(anyString(), anyString(), anyString()))
        .thenReturn(1);
    var pack = new AutomationTemplatePack(PACK_ID, 2, null, List.of(), null);
    when(seeder.getAvailablePacks()).thenReturn(List.of(new LoadedPack<>(pack, resource)));
  }

  @Test
  void gateOpen_invokesHookExactlyOnce() {
    givenInstalledAt("1");

    assertThat(installer.reconcile(PACK_ID, TENANT)).isTrue();

    verify(seeder, times(1)).reconcileInstalledPack(any(), eq(resource), eq(TENANT));
  }

  @Test
  void equalVersion_neverInvokesHook() {
    givenInstalledAt("2");

    assertThat(installer.reconcile(PACK_ID, TENANT)).isFalse();

    verify(seeder, never()).reconcileInstalledPack(any(), any(), any());
  }

  @Test
  void nonNumericInstalledVersion_neverInvokesHook() {
    givenInstalledAt("v1");

    assertThat(installer.reconcile(PACK_ID, TENANT)).isFalse();

    verify(seeder, never()).reconcileInstalledPack(any(), any(), any());
  }

  @Test
  void pristineRuleMutatedByHook_hashRepinned_editedRuleNotRepinned() {
    givenInstalledAt("1");

    var pristine = rule("pristine-rule");
    var pristineAction = action(pristine, "Shipped title");
    pristine.setContentHash(installer.computeRuleHash(pristine));

    var edited = rule("edited-rule");
    var editedAction = action(edited, "Shipped title");
    String editedStoredHash = installer.computeRuleHash(edited);
    edited.setContentHash(editedStoredHash);
    // Tenant edits the action after install.
    updateTitle(editedAction, "Tenant title");

    when(ruleRepository.findBySourcePackInstallId(installId)).thenReturn(List.of(pristine, edited));

    doAnswer(
            inv -> {
              // A reconcile target writes the new value only — never the content hash.
              updateTitle(pristineAction, "New shipped title");
              updateTitle(editedAction, "New shipped title");
              return null;
            })
        .when(seeder)
        .reconcileInstalledPack(any(), any(), any());

    installer.reconcile(PACK_ID, TENANT);

    assertThat(pristine.getContentHash()).isEqualTo(installer.computeRuleHash(pristine));
    assertThat(edited.getContentHash()).isEqualTo(editedStoredHash);
    verify(ruleRepository).saveAll(List.of(pristine));

    // Uninstall gate: only the rule edited before the hook counts as edited.
    var check = installer.checkUninstallable(PACK_ID, TENANT);
    assertThat(check.canUninstall()).isFalse();
    assertThat(check.blockingReason()).isEqualTo("1 of 2 rules have been edited");
  }

  @Test
  void hookChangingNothing_repinsNothing() {
    givenInstalledAt("1");
    var pristine = rule("pristine-rule");
    action(pristine, "Shipped title");
    pristine.setContentHash(installer.computeRuleHash(pristine));
    when(ruleRepository.findBySourcePackInstallId(installId)).thenReturn(List.of(pristine));

    installer.reconcile(PACK_ID, TENANT);

    verify(ruleRepository, never()).saveAll(any());
  }

  // --- helpers ---

  private void givenInstalledAt(String version) {
    var install =
        new PackInstall(
            PACK_ID, PackType.AUTOMATION_TEMPLATE, version, PACK_ID, Instant.now(), null, 1);
    ReflectionTestUtils.setField(install, "id", installId);
    when(packInstallRepository.findByPackId(PACK_ID)).thenReturn(Optional.of(install));
  }

  private AutomationRule rule(String slug) {
    var rule =
        new AutomationRule(
            slug,
            null,
            TriggerType.TASK_STATUS_CHANGED,
            Map.of("toStatus", "DONE"),
            List.of(),
            RuleSource.TEMPLATE,
            slug,
            null);
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    rule.setSourcePackInstallId(installId);
    return rule;
  }

  private AutomationAction action(AutomationRule rule, String title) {
    var action =
        new AutomationAction(
            rule.getId(), 0, ActionType.CREATE_TASK, Map.of("title", title), null, null);
    when(actionRepository.findByRuleIdOrderBySortOrder(rule.getId())).thenReturn(List.of(action));
    return action;
  }

  private static void updateTitle(AutomationAction action, String title) {
    action.update(
        action.getActionType(),
        Map.of("title", title),
        action.getSortOrder(),
        action.getDelayDuration(),
        action.getDelayUnit());
  }
}
