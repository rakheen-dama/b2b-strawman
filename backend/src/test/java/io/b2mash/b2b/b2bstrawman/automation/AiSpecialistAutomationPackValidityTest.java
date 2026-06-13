package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistRegistry;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pack-validity guard for the seeded {@code INVOKE_AI_SPECIALIST} automation actions (OBS-505).
 *
 * <p>The 6 AI-specialist actions shipped across {@code ai-specialist-common.json}, {@code
 * ai-specialist-legal-za.json} and {@code ai-specialist-consulting-za.json} must each reference (a)
 * a specialist id that is actually registered in {@link SpecialistRegistry}, and (b) a context
 * variable that resolves against the {@link AutomationContext} produced by the action's own trigger
 * type. Before the OBS-505 fix the actions used uppercase ids ({@code BILLING}/{@code
 * INTAKE}/{@code INBOX}) that 404 in {@code requireById}, and the unresolvable {@code
 * {{event.entityId}}} placeholder (there is no {@code event} section in any context) — so every
 * seeded action threw at execution time and could never run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiSpecialistAutomationPackValidityTest {

  @Autowired private AutomationTemplateSeeder seeder;
  @Autowired private SpecialistRegistry specialistRegistry;
  @Autowired private VariableResolver variableResolver;

  private List<AiSpecialistAction> aiSpecialistActions() {
    return seeder.getAvailablePacks().stream()
        .map(loaded -> loaded.definition())
        .flatMap(pack -> pack.templates().stream().map(t -> new PackTemplate(pack, t)))
        .flatMap(
            pt ->
                pt.template().actions().stream()
                    .filter(a -> a.actionType() == ActionType.INVOKE_AI_SPECIALIST)
                    .map(a -> new AiSpecialistAction(pt.pack(), pt.template(), a)))
        .toList();
  }

  @Test
  void everyShippedAiSpecialistActionReferencesARegisteredSpecialist() {
    var actions = aiSpecialistActions();
    assertThat(actions)
        .as("all 3 ai-specialist packs should ship INVOKE_AI_SPECIALIST actions")
        .hasSize(6);

    for (AiSpecialistAction action : actions) {
      Object specialistId = action.action().actionConfig().get("specialistId");
      assertThat(specialistId)
          .as("specialistId present for %s/%s", action.pack().packId(), action.template().slug())
          .isInstanceOf(String.class);
      assertThat(specialistRegistry.findById((String) specialistId))
          .as(
              "specialist id '%s' (pack %s, template %s) must be registered — requireById would 404"
                  + " otherwise",
              specialistId, action.pack().packId(), action.template().slug())
          .isPresent();
    }
  }

  @Test
  void everyShippedAiSpecialistActionContextRefResolvesAgainstItsTriggerContext() {
    for (AiSpecialistAction action : aiSpecialistActions()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> contextRef =
          (Map<String, Object>) action.action().actionConfig().get("contextRef");
      assertThat(contextRef)
          .as("contextRef present for %s/%s", action.pack().packId(), action.template().slug())
          .isNotNull();
      String entityIdTemplate = (String) contextRef.get("entityId");

      // Regression guard: the broken seed used {{event.entityId}}, and no trigger context ever
      // produces an `event` section, so the placeholder could never resolve.
      assertThat(entityIdTemplate)
          .as(
              "entityId placeholder for %s/%s must not reference the non-existent `event` section",
              action.pack().packId(), action.template().slug())
          .doesNotContain("{{event.");

      TriggerType triggerType = action.template().triggerType();

      // SCHEDULED actions resolve under the scheduled per-active-project context model (OBS-505
      // addendum). The poll handler fans out across active projects and builds a {{project.*}}
      // context via AutomationContext.buildScheduledProjectContext; assert the contextRef resolves
      // there rather than skipping. The no-`event`-section guard above still applies.
      Map<String, Map<String, Object>> context =
          triggerType == TriggerType.SCHEDULED
              ? buildSampleScheduledProjectContext()
              : buildSampleContext(triggerType);
      String resolved = variableResolver.resolve(entityIdTemplate, context);
      assertThatCode(() -> UUID.fromString(resolved))
          .as(
              "entityId '%s' -> '%s' for %s/%s (trigger %s) must resolve to a valid UUID",
              entityIdTemplate,
              resolved,
              action.pack().packId(),
              action.template().slug(),
              triggerType)
          .doesNotThrowAnyException();
    }
  }

  /**
   * Builds a representative scheduled per-active-project context, mirroring what the poll handler
   * constructs for each active project when fanning out a SCHEDULED project-scoped rule.
   */
  private Map<String, Map<String, Object>> buildSampleScheduledProjectContext() {
    UUID actorId = UUID.randomUUID();
    var rule =
        new AutomationRule(
            "guard-test-scheduled-rule",
            null,
            TriggerType.SCHEDULED,
            Map.of(),
            List.of(),
            RuleSource.TEMPLATE,
            "guard-test",
            actorId);
    var project =
        io.b2mash.b2b.b2bstrawman.testutil.TestIds.withId(
            new io.b2mash.b2b.b2bstrawman.project.Project("Matter X", "desc", actorId),
            UUID.randomUUID());
    return AutomationContext.buildScheduledProjectContext(rule, project);
  }

  /**
   * Builds a representative {@link AutomationContext} for the trigger types in use by the packs.
   */
  private Map<String, Map<String, Object>> buildSampleContext(TriggerType triggerType) {
    UUID entityId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Instant now = Instant.now();
    var rule =
        new AutomationRule(
            "guard-test-rule",
            null,
            triggerType,
            Map.of(),
            List.of(),
            RuleSource.TEMPLATE,
            "guard-test",
            actorId);

    DomainEvent event =
        switch (triggerType) {
          case INVOICE_STATUS_CHANGED ->
              new InvoiceSentEvent(
                  "invoice.sent",
                  "invoice",
                  entityId,
                  projectId,
                  actorId,
                  "Tester",
                  "tenant_guard",
                  "org_guard",
                  now,
                  Map.of("customer_id", customerId.toString(), "total", "100.00"),
                  actorId,
                  "INV-0001",
                  "Acme");
          case INFORMATION_REQUEST_COMPLETED ->
              new InformationRequestCompletedEvent(
                  "information_request.completed",
                  "information_request",
                  entityId,
                  projectId,
                  actorId,
                  "Tester",
                  "tenant_guard",
                  "org_guard",
                  now,
                  Map.of(),
                  entityId,
                  customerId,
                  UUID.randomUUID());
          case PROJECT_STATUS_CHANGED ->
              new ProjectReopenedEvent(
                  "project.reopened",
                  "project",
                  entityId,
                  projectId,
                  actorId,
                  "Tester",
                  "tenant_guard",
                  "org_guard",
                  now,
                  Map.of("customer_id", customerId.toString()),
                  actorId,
                  "Matter X",
                  "ON_HOLD");
          default ->
              throw new IllegalArgumentException(
                  "No sample event wired for trigger type " + triggerType);
        };

    return AutomationContext.build(triggerType, event, rule);
  }

  /**
   * Guard for the OBS-505 fan-out limitation. The scheduled poll handler fans out a SCHEDULED rule
   * per active project when <i>any</i> action targets a project contextRef, and re-fires <b>all</b>
   * of the rule's actions per project (see {@code
   * AutomationPollTriggersHandler#fireScheduledRulePerActiveProject}). So a SCHEDULED template that
   * mixes a project-scoped action with a non-project action would fire the non-project action N
   * times (once per active project) instead of once. No shipped template does this today; this test
   * fails CI if a future template introduces the mix, forcing a deliberate per-action design
   * decision rather than a silent multi-fire.
   */
  @Test
  void noShippedScheduledTemplateMixesProjectAndNonProjectActions() {
    var scheduledTemplates =
        seeder.getAvailablePacks().stream()
            .map(loaded -> loaded.definition())
            .flatMap(pack -> pack.templates().stream().map(t -> new PackTemplate(pack, t)))
            .filter(pt -> pt.template().triggerType() == TriggerType.SCHEDULED)
            .toList();

    // Sanity: the packs do ship SCHEDULED templates, so this guard is actually exercising
    // something.
    assertThat(scheduledTemplates)
        .as("expected at least one shipped SCHEDULED template to guard")
        .isNotEmpty();

    for (PackTemplate pt : scheduledTemplates) {
      var actions = pt.template().actions();
      // Distinct-action rule: a single-action template can never fan out incorrectly, so only
      // multi-action SCHEDULED templates are at risk.
      if (actions.size() <= 1) {
        continue;
      }
      boolean anyProjectScoped =
          actions.stream().anyMatch(a -> actionTargetsProject(a.actionConfig()));
      boolean anyNonProject =
          actions.stream().anyMatch(a -> !actionTargetsProject(a.actionConfig()));
      assertThat(anyProjectScoped && anyNonProject)
          .as(
              "SCHEDULED template %s/%s mixes a project-scoped action with a non-project action;"
                  + " the per-active-project fan-out would fire the non-project action once per"
                  + " active project. Split into separate rules or make the fan-out per-action"
                  + " before shipping this (OBS-505).",
              pt.pack().packId(), pt.template().slug())
          .isFalse();
    }
  }

  /**
   * Proves the guard above actually trips on a mixed-action SCHEDULED template: a synthetic
   * template carrying one project-scoped action (contextRef.entityType=project) and one non-project
   * action is detected as mixed. If this assertion failed, the guard's detection logic would be a
   * no-op and could silently let a real mixed template through.
   */
  @Test
  void mixedActionDetectionWouldCatchAMixedScheduledTemplate() {
    var projectScopedAction =
        new AutomationTemplateDefinition.TemplateActionDefinition(
            ActionType.INVOKE_AI_SPECIALIST,
            Map.of(
                "specialistId",
                "matter-summary",
                "contextRef",
                Map.of("entityType", "project", "entityId", "{{project.id}}")),
            null,
            null,
            0);
    var nonProjectAction =
        new AutomationTemplateDefinition.TemplateActionDefinition(
            ActionType.SEND_NOTIFICATION, Map.of("message", "weekly digest sent"), null, null, 1);

    assertThat(actionTargetsProject(projectScopedAction.actionConfig()))
        .as("project-scoped action must be detected as targeting a project")
        .isTrue();
    assertThat(actionTargetsProject(nonProjectAction.actionConfig()))
        .as("non-project action must NOT be detected as targeting a project")
        .isFalse();

    var mixed = List.of(projectScopedAction, nonProjectAction);
    boolean anyProjectScoped = mixed.stream().anyMatch(a -> actionTargetsProject(a.actionConfig()));
    boolean anyNonProject = mixed.stream().anyMatch(a -> !actionTargetsProject(a.actionConfig()));
    assertThat(anyProjectScoped && anyNonProject)
        .as("a project + non-project action mix must be flagged by the guard predicate")
        .isTrue();
  }

  /**
   * Mirrors the production fan-out classifier {@code
   * AutomationPollTriggersHandler#actionTargetsProject}: an action targets a project when its
   * {@code contextRef} has {@code entityType == "project"} or an {@code entityId} template
   * referencing {@code {{project.}}}.
   */
  @SuppressWarnings("unchecked")
  private static boolean actionTargetsProject(Map<String, Object> actionConfig) {
    if (actionConfig == null) {
      return false;
    }
    Object contextRefObj = actionConfig.get("contextRef");
    if (!(contextRefObj instanceof Map<?, ?> contextRef)) {
      return false;
    }
    Object entityType = ((Map<String, Object>) contextRef).get("entityType");
    if ("project".equals(entityType)) {
      return true;
    }
    Object entityId = ((Map<String, Object>) contextRef).get("entityId");
    return entityId instanceof String idTemplate && idTemplate.contains("{{project.");
  }

  private record PackTemplate(AutomationTemplatePack pack, AutomationTemplateDefinition template) {}

  private record AiSpecialistAction(
      AutomationTemplatePack pack,
      AutomationTemplateDefinition template,
      AutomationTemplateDefinition.TemplateActionDefinition action) {}
}
