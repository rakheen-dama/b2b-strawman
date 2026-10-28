package io.b2mash.b2b.b2bstrawman.automation.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.TemplateActionDefinition;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test for {@link AutomationTemplateSeeder}: the optional {@code defaultEnabled} template flag
 * (LZKC-013) and the ADR-356 {@code automation_rule} reconcile resolver (625F, G-B13 / D4). No
 * Spring context — repositories are mocked; {@code applyPackContent} and {@code
 * reconcileInstalledPack} are invoked directly. The real-Hibernate path (installer gate + hash
 * re-pin) is covered by {@code packs.AutomationCommonReconcileIntegrationTest}.
 */
class AutomationTemplateSeederTest {

  static final String SLUG = "document-review-notification";
  static final String V2_NAME = "Document Review Notification";
  static final String V2_DESC = "Notifies project owner when a document is accepted";
  static final String V2_TITLE = "Document accepted: {{document.name}}";
  static final String V2_MESSAGE = "A document has been accepted in project {{project.name}}.";
  static final String V3_NAME = "Document uploaded — notify matter lead";
  static final String V3_DESC = "Notifies the matter lead when a document is uploaded";
  static final String V3_TITLE = "Document uploaded: {{document.name}}";
  static final String V3_MESSAGE = "{{document.name}} was uploaded to the matter.";
  static final UUID SYSTEM = new UUID(0L, 0L);
  static final String PINNED_HASH = "a".repeat(64);

  private AutomationRuleRepository ruleRepository;
  private AutomationActionRepository actionRepository;
  private AutomationTemplateSeeder seeder;

  @BeforeEach
  void setUp() {
    ruleRepository = mock(AutomationRuleRepository.class);
    when(ruleRepository.save(any(AutomationRule.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    actionRepository = mock(AutomationActionRepository.class);
    when(actionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    seeder =
        new AutomationTemplateSeeder(
            mock(ResourcePatternResolver.class),
            new ObjectMapper(),
            ruleRepository,
            actionRepository,
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class),
            mock(PackVersionGate.class));
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
                template("explicit-true-rule", Boolean.TRUE)),
            null);

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

  // --- ADR-356 reconcile resolver (625F) ---

  @Test
  void reconcile_pristineRule_renamedRetitledAndDisabled() {
    var seeded = v2Rule(SYSTEM);

    seeder.reconcileInstalledPack(v3Pack(), null, "tenant_test");

    assertThat(seeded.rule().getName()).isEqualTo(V3_NAME);
    assertThat(seeded.rule().getDescription()).isEqualTo(V3_DESC);
    assertThat(seeded.action().getActionConfig())
        .containsEntry("title", V3_TITLE)
        .containsEntry("message", V3_MESSAGE)
        .containsEntry("recipientType", "PROJECT_OWNER");
    assertThat(seeded.rule().isEnabled()).isFalse();
    assertThat(seeded.rule().getContentHash())
        .as("targets never write content hashes — the installer re-pins")
        .isEqualTo(PINNED_HASH);
  }

  @Test
  void reconcile_firmRenamedRule_keepsItsName() {
    var seeded = v2Rule(SYSTEM);
    seeded
        .rule()
        .update("Our upload alert", V2_DESC, TriggerType.DOCUMENT_ACCEPTED, Map.of(), List.of());

    seeder.reconcileInstalledPack(v3Pack(), null, "tenant_test");

    assertThat(seeded.rule().getName()).isEqualTo("Our upload alert");
    assertThat(seeded.rule().getDescription()).isEqualTo(V3_DESC);
    assertThat(seeded.action().getActionConfig()).containsEntry("title", V3_TITLE);
    assertThat(seeded.rule().isEnabled()).isFalse();
  }

  @Test
  void reconcile_firmDisabledRule_staysDisabled() {
    var seeded = v2Rule(SYSTEM);
    seeded.rule().toggle(); // firm switched it off

    seeder.reconcileInstalledPack(v3Pack(), null, "tenant_test");

    assertThat(seeded.rule().isEnabled())
        .as("enabled is already at 'to' — never toggled back on")
        .isFalse();
    assertThat(seeded.rule().getName()).isEqualTo(V3_NAME);
    assertThat(seeded.action().getActionConfig()).containsEntry("title", V3_TITLE);
  }

  @Test
  void reconcile_firmEditedTitle_keepsTitle() {
    var seeded = v2Rule(SYSTEM);
    var edited = new LinkedHashMap<>(seeded.action().getActionConfig());
    edited.put("title", "Custom: {{document.name}}");
    seeded.action().update(ActionType.SEND_NOTIFICATION, edited, 0, null, null);

    seeder.reconcileInstalledPack(v3Pack(), null, "tenant_test");

    assertThat(seeded.action().getActionConfig())
        .containsEntry("title", "Custom: {{document.name}}")
        .containsEntry("message", V3_MESSAGE);
    assertThat(seeded.rule().getName()).isEqualTo(V3_NAME);
    assertThat(seeded.rule().isEnabled()).isFalse();
  }

  @Test
  void reconcile_apiActivatedCopyIsNeverTargeted() {
    // A tagged TEMPLATE row with the slug but created by a member (e.g. an activated copy swept up
    // by a re-install's tagging) must never be resolved.
    var copy = v2Rule(UUID.randomUUID());

    seeder.reconcileInstalledPack(v3Pack(), null, "tenant_test");

    assertThat(copy.rule().getName()).isEqualTo(V2_NAME);
    assertThat(copy.rule().getDescription()).isEqualTo(V2_DESC);
    assertThat(copy.action().getActionConfig())
        .containsEntry("title", V2_TITLE)
        .containsEntry("message", V2_MESSAGE);
    assertThat(copy.rule().isEnabled()).isTrue();
  }

  @Test
  void reconcile_unsupportedFieldOrEntity_isIgnoredWithoutException() {
    var seeded = v2Rule(SYSTEM);
    var pack =
        new AutomationTemplatePack(
            "automation-common",
            3,
            null,
            List.of(),
            List.of(
                new PackReconcileEntry(
                    "automation_rule", SLUG, "triggerType", "DOCUMENT_ACCEPTED", "SCHEDULED"),
                new PackReconcileEntry(
                    "automation_rule", SLUG, "actions[0].actionConfig.title", V2_TITLE, 42),
                new PackReconcileEntry("automation_rule", SLUG, "enabled", true, "false"),
                new PackReconcileEntry("automation_rule", SLUG, "name", V2_NAME, true),
                new PackReconcileEntry("document_template", SLUG, "name", V2_NAME, V3_NAME),
                new PackReconcileEntry(
                    "automation_rule",
                    SLUG,
                    "actions[99999999999].actionConfig.title",
                    V2_TITLE,
                    V3_TITLE)));

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(AutomationTemplateSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      assertThatCode(() -> seeder.reconcileInstalledPack(pack, null, "tenant_test"))
          .doesNotThrowAnyException();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }

    assertThat(seeded.rule().getName()).isEqualTo(V2_NAME);
    assertThat(seeded.rule().getTriggerType()).isEqualTo(TriggerType.DOCUMENT_ACCEPTED);
    assertThat(seeded.rule().isEnabled()).isTrue();
    assertThat(seeded.action().getActionConfig()).containsEntry("title", V2_TITLE);
    assertThat(appender.list)
        .filteredOn(e -> e.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .hasSize(6)
        .allMatch(m -> m.startsWith("Reconcile entry ignored"));
  }

  @Test
  void reconcile_unknownActionSortOrder_isNoTarget() {
    var seeded = v2Rule(SYSTEM);
    var pack =
        new AutomationTemplatePack(
            "automation-common",
            3,
            null,
            List.of(),
            List.of(
                new PackReconcileEntry(
                    "automation_rule", SLUG, "actions[7].actionConfig.title", V2_TITLE, V3_TITLE)));

    assertThatCode(() -> seeder.reconcileInstalledPack(pack, null, "tenant_test"))
        .doesNotThrowAnyException();
    assertThat(seeded.action().getActionConfig()).containsEntry("title", V2_TITLE);
  }

  /**
   * Guard for the shipped pack (R-6): every entry of the real {@code common.json} reconcile block
   * resolves to a supported target and takes a pristine v2 rule exactly to the v3 template in the
   * same file — a later edit cannot silently ship a mismatched {@code to}.
   */
  @Test
  void shippedCommonPack_reconcileBlockTakesV2RuleToTheV3Template() throws IOException {
    AutomationTemplatePack pack = shippedCommonPack();
    assertThat(pack.version()).isEqualTo(4);
    assertThat(pack.reconcile()).isNotEmpty();
    assertThat(pack.reconcile())
        .allSatisfy(e -> assertThat(e.entity()).isEqualTo("automation_rule"));
    // v2 -> v3 entries (625F); the v3 -> v4 entries (628C) target other slugs — see the guard
    // below.
    assertThat(pack.reconcile()).filteredOn(e -> SLUG.equals(e.key())).hasSize(5);
    var template =
        pack.templates().stream().filter(t -> SLUG.equals(t.slug())).findFirst().orElseThrow();
    var templateConfig = template.actions().getFirst().actionConfig();

    var seeded = v2Rule(SYSTEM);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(AutomationTemplateSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      seeder.reconcileInstalledPack(pack, null, "tenant_test");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }

    assertThat(seeded.rule().getName()).isEqualTo(template.name()).isEqualTo(V3_NAME);
    assertThat(seeded.rule().getDescription()).isEqualTo(template.description()).isEqualTo(V3_DESC);
    assertThat(seeded.action().getActionConfig()).isEqualTo(templateConfig);
    assertThat(templateConfig)
        .containsEntry("title", V3_TITLE)
        .containsEntry("message", V3_MESSAGE);
    assertThat(seeded.rule().isEnabled()).isEqualTo(template.seedEnabled()).isFalse();
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(m -> m.startsWith("Reconcile entry ignored"));
  }

  /**
   * Guard for the v3 -> v4 entries (628C, U21 terminology): pristine v3 {@code
   * overdue-invoice-reminder} and {@code budget-alert-escalation} rules reach exactly the v4
   * templates shipped in the same file (name, description and every action-config string), with no
   * entry ignored — a later JSON edit without updating {@code to} fails here.
   */
  @Test
  void shippedCommonPack_v4EntriesTakeV3RulesToTheV4Templates() throws IOException {
    AutomationTemplatePack pack = shippedCommonPack();
    var v4Entries = pack.reconcile().stream().filter(e -> V4_SLUGS.contains(e.key())).toList();
    assertThat(v4Entries).hasSize(10);
    assertThat(v4Entries).allSatisfy(e -> assertThat(e.to()).isNotEqualTo(e.from()));

    var overdue =
        v3Rule(
            OVERDUE_SLUG,
            "Overdue Invoice Reminder",
            "Sends email to customer when invoice becomes overdue, then notifies project owner"
                + " after 7 days",
            List.of(
                Map.of(
                    "recipientType",
                    "CUSTOMER_CONTACT",
                    "subject",
                    "Invoice {{invoice.invoiceNumber}} is overdue",
                    "body",
                    "Dear {{customer.name}},\n\nYour invoice {{invoice.invoiceNumber}} is now"
                        + " overdue. Please arrange payment at your earliest convenience."),
                Map.of(
                    "recipientType",
                    "PROJECT_OWNER",
                    "title",
                    "Invoice {{invoice.invoiceNumber}} still overdue",
                    "message",
                    "Invoice {{invoice.invoiceNumber}} for {{customer.name}} has been overdue for 7"
                        + " days.")));
    var budget =
        v3Rule(
            BUDGET_SLUG,
            "Budget Alert Escalation",
            "Notifies admins when a project budget threshold is reached",
            List.of(
                Map.of(
                    "recipientType",
                    "ORG_ADMINS",
                    "title",
                    "Budget alert: {{project.name}}",
                    "message",
                    "Project {{project.name}} has reached its budget threshold.")));

    var logs = captureSeederLogs(() -> seeder.reconcileInstalledPack(pack, null, "tenant_test"));

    for (SeededRules seeded : List.of(overdue, budget)) {
      var template =
          pack.templates().stream()
              .filter(t -> seeded.rule().getTemplateSlug().equals(t.slug()))
              .findFirst()
              .orElseThrow();
      assertThat(seeded.rule().getName()).isEqualTo(template.name());
      assertThat(seeded.rule().getDescription()).isEqualTo(template.description());
      for (AutomationAction action : seeded.actions()) {
        var shipped =
            template.actions().stream()
                .filter(a -> a.sortOrder() == action.getSortOrder())
                .findFirst()
                .orElseThrow();
        assertThat(action.getActionConfig())
            .as("%s actions[%d]", template.slug(), action.getSortOrder())
            .isEqualTo(shipped.actionConfig());
      }
    }
    assertThat(overdue.rule().getName()).isEqualTo("Overdue Fee Note Reminder");
    assertThat(budget.rule().getName()).isEqualTo("Fee Estimate Alert Escalation");
    assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile applied")).hasSize(10);
  }

  @Test
  void reconcile_sameSlugRuleOfAnotherPack_isNeverResolved() {
    // The only same-slug rule is tagged with automation-common's install; a pack with a different
    // packId reconciling the same slug must not resolve it (pack-scoped lookup).
    var foreign = v2Rule(SYSTEM);
    var otherPack =
        new AutomationTemplatePack(
            "automation-legal-za",
            3,
            null,
            List.of(),
            List.of(
                entry("name", V2_NAME, V3_NAME),
                entry("actions[0].actionConfig.title", V2_TITLE, V3_TITLE),
                entry("enabled", true, false)));

    seeder.reconcileInstalledPack(otherPack, null, "tenant_test");

    assertThat(foreign.rule().getName()).isEqualTo(V2_NAME);
    assertThat(foreign.rule().isEnabled()).isTrue();
    assertThat(foreign.action().getActionConfig()).containsEntry("title", V2_TITLE);
    Mockito.verify(ruleRepository, Mockito.atLeastOnce())
        .findPackSeededByTemplateSlug("automation-legal-za", SLUG, RuleSource.TEMPLATE);
  }

  private record SeededRules(AutomationRule rule, List<AutomationAction> actions) {}

  static final String OVERDUE_SLUG = "overdue-invoice-reminder";
  static final String BUDGET_SLUG = "budget-alert-escalation";
  static final List<String> V4_SLUGS = List.of(OVERDUE_SLUG, BUDGET_SLUG);

  /** A pristine v3 rule (one action per config, sortOrder = list index), stubbed as pack-seeded. */
  private SeededRules v3Rule(
      String slug, String name, String description, List<Map<String, Object>> configs) {
    var rule =
        new AutomationRule(
            name,
            description,
            TriggerType.INVOICE_STATUS_CHANGED,
            Map.of(),
            List.of(),
            RuleSource.TEMPLATE,
            slug,
            SYSTEM);
    TestIds.withId(rule, UUID.randomUUID());
    rule.setSourcePackInstallId(UUID.randomUUID());
    rule.setContentHash(PINNED_HASH);
    var actions = new java.util.ArrayList<AutomationAction>();
    for (int i = 0; i < configs.size(); i++) {
      actions.add(
          new AutomationAction(
              rule.getId(),
              i,
              i == 0 && OVERDUE_SLUG.equals(slug)
                  ? ActionType.SEND_EMAIL
                  : ActionType.SEND_NOTIFICATION,
              new LinkedHashMap<>(configs.get(i)),
              null,
              null));
    }
    when(ruleRepository.findPackSeededByTemplateSlug(
            "automation-common", slug, RuleSource.TEMPLATE))
        .thenReturn(List.of(rule));
    when(actionRepository.findByRuleIdOrderBySortOrder(rule.getId())).thenReturn(actions);
    return new SeededRules(rule, actions);
  }

  private static AutomationTemplatePack shippedCommonPack() throws IOException {
    try (InputStream in =
        new ClassPathResource("automation-templates/common.json").getInputStream()) {
      return JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build()
          .readValue(in, AutomationTemplatePack.class);
    }
  }

  private static List<String> captureSeederLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(AutomationTemplateSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private record SeededRule(AutomationRule rule, AutomationAction action) {}

  /**
   * A v2 {@code document-review-notification} rule + action, stubbed as the only TEMPLATE row with
   * the slug tagged with {@code automation-common}'s install.
   */
  private SeededRule v2Rule(UUID createdBy) {
    var rule =
        new AutomationRule(
            V2_NAME,
            V2_DESC,
            TriggerType.DOCUMENT_ACCEPTED,
            Map.of(),
            List.of(),
            RuleSource.TEMPLATE,
            SLUG,
            createdBy);
    TestIds.withId(rule, UUID.randomUUID());
    rule.setSourcePackInstallId(UUID.randomUUID());
    rule.setContentHash(PINNED_HASH);
    var action =
        new AutomationAction(
            rule.getId(),
            0,
            ActionType.SEND_NOTIFICATION,
            new LinkedHashMap<>(
                Map.of("recipientType", "PROJECT_OWNER", "title", V2_TITLE, "message", V2_MESSAGE)),
            null,
            null);
    when(ruleRepository.findPackSeededByTemplateSlug(
            "automation-common", SLUG, RuleSource.TEMPLATE))
        .thenReturn(List.of(rule));
    when(actionRepository.findByRuleIdOrderBySortOrder(rule.getId())).thenReturn(List.of(action));
    return new SeededRule(rule, action);
  }

  private static AutomationTemplatePack v3Pack() {
    return new AutomationTemplatePack(
        "automation-common",
        3,
        null,
        List.of(),
        List.of(
            entry("name", V2_NAME, V3_NAME),
            entry("description", V2_DESC, V3_DESC),
            entry("actions[0].actionConfig.title", V2_TITLE, V3_TITLE),
            entry("actions[0].actionConfig.message", V2_MESSAGE, V3_MESSAGE),
            entry("enabled", true, false)));
  }

  private static PackReconcileEntry entry(String field, Object from, Object to) {
    return new PackReconcileEntry("automation_rule", SLUG, field, from, to);
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
