package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Hibernate test of the first ADR-356 reconcile consumer (625F, G-B13 / D4): the {@code
 * automation-common} v3 {@code reconcile} block reaches an existing tenant through the startup path
 * {@code PackInstallService.internalInstall} → {@code AutomationPackInstaller.reconcile} (version
 * gate → pristine snapshot → {@code AutomationTemplateSeeder.resolveReconcileTarget} → content-hash
 * re-pin), and the one rule keeps every firm edit.
 *
 * <p>A v2 tenant is simulated by rewriting the provisioned {@code document-review-notification}
 * rule to its exact v2 shipped state (hash pinned → pristine), rewinding {@code
 * pack_install.pack_version} 4 → 2 (the v4 entries target other slugs, already current on this
 * tenant) and the OrgSettings automation status to 2. Each test re-runs the simulation, so tests
 * are order-independent; assertions target this tenant's single seeded rule only (no global
 * counts). The 628C v3 → v4 cases (fee-note / fee-estimate wording) rewind 4 → 3 and restore the
 * shipped rule afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationCommonReconcileIntegrationTest {

  private static final String ORG_ID = "org_automation_common_reconcile_test";
  private static final String PACK_ID = "automation-common";
  private static final String OTHER_PACK_ID = "automation-legal-za";

  static final String SLUG = "document-review-notification";
  static final String V2_NAME = "Document Review Notification";
  static final String V2_DESC = "Notifies project owner when a document is accepted";
  static final String V2_TITLE = "Document accepted: {{document.name}}";
  static final String V2_MESSAGE = "A document has been accepted in project {{project.name}}.";
  static final String V3_NAME = "Document uploaded — notify matter lead";
  static final String V3_DESC = "Notifies the matter lead when a document is uploaded";
  static final String V3_TITLE = "Document uploaded: {{document.name}}";
  static final String V3_MESSAGE = "{{document.name}} was uploaded to the matter.";
  static final String CUSTOM_TITLE = "Custom: {{document.name}}";
  static final UUID SYSTEM = new UUID(0L, 0L);

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationPackInstaller automationPackInstaller;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Automation Common Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void pristineV2Rule_isReconciledOnVersionBump_andHashRepinned() {
    String v2Hash = simulateV2Tenant(rule -> {}, false, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          var rule = seededRule();
          assertThat(rule.getName()).isEqualTo(V3_NAME);
          assertThat(rule.getDescription()).isEqualTo(V3_DESC);
          assertThat(rule.isEnabled()).isFalse();
          assertThat(actionConfig(rule))
              .containsEntry("title", V3_TITLE)
              .containsEntry("message", V3_MESSAGE)
              .containsEntry("recipientType", "PROJECT_OWNER");
          assertThat(rule.getContentHash())
              .as("pristine rule's hash is re-pinned to the reconciled content")
              .isEqualTo(automationPackInstaller.computeRuleHash(rule))
              .isNotEqualTo(v2Hash);
          assertThat(packInstallRepository.findByPackId(PACK_ID).orElseThrow().getPackVersion())
              .isEqualTo("4");
          assertThat(automationStatusVersion()).isEqualTo(4);
          return null;
        });
    // Fresh tenant, no executions, every automation-common rule pristine → nothing blocks
    // uninstall.
    assertThat(uninstallReason())
        .as("re-pinned rule must not read as edited (no uninstall blocker at all)")
        .isNull();
  }

  @Test
  void firmRenamedRule_keepsName_restReconciled() {
    simulateV2Tenant(rule -> renameTo(rule, "Our upload alert"), false, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          var rule = seededRule();
          assertThat(rule.getName()).isEqualTo("Our upload alert");
          assertThat(rule.getDescription()).isEqualTo(V3_DESC);
          assertThat(rule.isEnabled()).isFalse();
          assertThat(actionConfig(rule)).containsEntry("title", V3_TITLE);
          // name is not part of the content hash → still pristine → re-pinned
          assertThat(rule.getContentHash())
              .isEqualTo(automationPackInstaller.computeRuleHash(rule));
          return null;
        });
  }

  @Test
  void firmDisabledRule_staysDisabled_restReconciled() {
    simulateV2Tenant(AutomationRule::toggle, false, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          var rule = seededRule();
          assertThat(rule.isEnabled()).isFalse();
          assertThat(rule.getName()).isEqualTo(V3_NAME);
          assertThat(actionConfig(rule)).containsEntry("title", V3_TITLE);
          return null;
        });
  }

  @Test
  void firmEditedTitle_keepsTitle_andStillReadsEdited() {
    String v2Hash = simulateV2Tenant(rule -> {}, true, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          var rule = seededRule();
          assertThat(actionConfig(rule))
              .containsEntry("title", CUSTOM_TITLE)
              .containsEntry("message", V3_MESSAGE);
          assertThat(rule.getName()).isEqualTo(V3_NAME);
          assertThat(rule.isEnabled()).isFalse();
          assertThat(rule.getContentHash())
              .as("a firm-edited rule is never re-pinned")
              .isEqualTo(v2Hash)
              .isNotEqualTo(automationPackInstaller.computeRuleHash(rule));
          return null;
        });
    assertThat(uninstallReason()).contains("have been edited");
  }

  @Test
  void apiActivatedCopy_isNeverTouched() {
    simulateV2Tenant(rule -> {}, false, false);
    // A member-created TEMPLATE copy with the same slug, tagged with the install (the re-install
    // tagging edge case) — the worst case for the resolver's SYSTEM filter.
    UUID copyId =
        inTenantTx(
            () -> {
              var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
              var copy =
                  new AutomationRule(
                      V2_NAME,
                      V2_DESC,
                      TriggerType.DOCUMENT_ACCEPTED,
                      Map.of(),
                      List.of(),
                      RuleSource.TEMPLATE,
                      SLUG,
                      UUID.randomUUID());
              copy.setSourcePackInstallId(install.getId());
              copy = ruleRepository.save(copy);
              actionRepository.save(
                  new AutomationAction(
                      copy.getId(), 0, ActionType.SEND_NOTIFICATION, v2Config(), null, null));
              assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, "4", "2"))
                  .isEqualTo(1);
              return copy.getId();
            });
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var copy = ruleRepository.findById(copyId).orElseThrow();
            assertThat(copy.getName()).isEqualTo(V2_NAME);
            assertThat(copy.getDescription()).isEqualTo(V2_DESC);
            assertThat(copy.isEnabled()).isTrue();
            assertThat(actionConfig(copy))
                .containsEntry("title", V2_TITLE)
                .containsEntry("message", V2_MESSAGE);
            // the pack-seeded rule itself was reconciled in the same pass
            assertThat(seededRule().getName()).isEqualTo(V3_NAME);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            actionRepository.deleteByRuleId(copyId);
            ruleRepository.deleteById(copyId);
            return null;
          });
    }
  }

  @Test
  void sameSlugRuleFromAnotherPack_isNeverTouched() {
    simulateV2Tenant(rule -> {}, false, false);
    // A SYSTEM-created TEMPLATE rule with the same slug, tagged with a DIFFERENT pack's install —
    // the resolver is scoped to automation-common's install, so it is neither a target nor a
    // duplicate that would make the real target ambiguous.
    UUID foreignId =
        inTenantTx(
            () -> {
              var otherInstall = packInstallRepository.findByPackId(OTHER_PACK_ID).orElseThrow();
              var foreign =
                  new AutomationRule(
                      V2_NAME,
                      V2_DESC,
                      TriggerType.DOCUMENT_ACCEPTED,
                      Map.of(),
                      List.of(),
                      RuleSource.TEMPLATE,
                      SLUG,
                      SYSTEM);
              foreign.setSourcePackInstallId(otherInstall.getId());
              foreign = ruleRepository.save(foreign);
              actionRepository.save(
                  new AutomationAction(
                      foreign.getId(), 0, ActionType.SEND_NOTIFICATION, v2Config(), null, null));
              assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, "4", "2"))
                  .isEqualTo(1);
              return foreign.getId();
            });
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var foreign = ruleRepository.findById(foreignId).orElseThrow();
            assertThat(foreign.getName()).isEqualTo(V2_NAME);
            assertThat(foreign.getDescription()).isEqualTo(V2_DESC);
            assertThat(foreign.isEnabled()).isTrue();
            assertThat(actionConfig(foreign))
                .containsEntry("title", V2_TITLE)
                .containsEntry("message", V2_MESSAGE);
            // this pack's own rule resolved unambiguously and was reconciled
            assertThat(seededRule().getName()).isEqualTo(V3_NAME);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            actionRepository.deleteByRuleId(foreignId);
            ruleRepository.deleteById(foreignId);
            return null;
          });
    }
  }

  @Test
  void equalVersion_isANoOp() {
    // v2 content, but the recorded version is already the classpath version → gate closed.
    simulateV2Tenant(rule -> {}, false, false);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          var rule = seededRule();
          assertThat(rule.getName()).isEqualTo(V2_NAME);
          assertThat(rule.getDescription()).isEqualTo(V2_DESC);
          assertThat(rule.isEnabled()).isTrue();
          assertThat(actionConfig(rule)).containsEntry("title", V2_TITLE);
          return null;
        });
  }

  @Test
  void secondRunAtEqualVersion_changesNothing() {
    simulateV2Tenant(rule -> {}, false, true);
    packInstallService.internalInstall(PACK_ID, schema);
    record Snapshot(
        String name, boolean enabled, String hash, Instant updatedAt, Map<String, Object> config) {}
    Supplier<Snapshot> snap =
        () ->
            inTenantTx(
                () -> {
                  var rule = seededRule();
                  return new Snapshot(
                      rule.getName(),
                      rule.isEnabled(),
                      rule.getContentHash(),
                      rule.getUpdatedAt(),
                      new LinkedHashMap<>(actionConfig(rule)));
                });
    Snapshot first = snap.get();
    assertThat(first.name()).isEqualTo(V3_NAME);

    packInstallService.internalInstall(PACK_ID, schema);

    assertThat(snap.get()).isEqualTo(first);
  }

  // --- 628C: v3 -> v4 entries (overdue-invoice-reminder, budget-alert-escalation) ---

  static final String OVERDUE_SLUG = "overdue-invoice-reminder";
  static final String BUDGET_SLUG = "budget-alert-escalation";
  static final String CUSTOM_SUBJECT = "Our own subject {{invoice.invoiceNumber}}";

  @Test
  void overdueReminderV3_isReconciledToV4_firmEditedSubjectSurvives() {
    simulateV3Rule(OVERDUE_SLUG, true);
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var rule = seededRule(OVERDUE_SLUG);
            assertThat(rule.getName()).isEqualTo("Overdue Fee Note Reminder");
            assertThat(rule.getDescription())
                .isEqualTo(
                    "Sends email to the client when a fee note becomes overdue, then notifies the"
                        + " matter lead after 7 days");
            assertThat(actionConfig(rule, 0))
                .containsEntry("subject", CUSTOM_SUBJECT)
                .containsEntry(
                    "body",
                    "Dear {{customer.name}},\n\nYour fee note {{invoice.invoiceNumber}} is now"
                        + " overdue. Please arrange payment at your earliest convenience.");
            assertThat(actionConfig(rule, 1))
                .containsEntry("title", "Fee note {{invoice.invoiceNumber}} still overdue")
                .containsEntry(
                    "message",
                    "Fee note {{invoice.invoiceNumber}} for {{customer.name}} has been overdue"
                        + " for 7 days.");
            assertThat(packInstallRepository.findByPackId(PACK_ID).orElseThrow().getPackVersion())
                .isEqualTo("4");
            return null;
          });
    } finally {
      restoreShipped(OVERDUE_SLUG);
    }
  }

  @Test
  void budgetAlertV3_isReconciledToV4_andHashRepinned() {
    simulateV3Rule(BUDGET_SLUG, false);
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var rule = seededRule(BUDGET_SLUG);
            assertThat(rule.getName()).isEqualTo("Fee Estimate Alert Escalation");
            assertThat(rule.getDescription())
                .isEqualTo("Notifies admins when a matter fee estimate threshold is reached");
            assertThat(actionConfig(rule, 0))
                .containsEntry("title", "Fee estimate alert: {{project.name}}")
                .containsEntry(
                    "message", "Matter {{project.name}} has reached its fee estimate threshold.");
            assertThat(rule.getContentHash())
                .as("pristine v3 rule is re-pinned to the reconciled content")
                .isEqualTo(automationPackInstaller.computeRuleHash(rule));
            return null;
          });
    } finally {
      restoreShipped(BUDGET_SLUG);
    }
  }

  /** The exact v3 ({@code fe579bdc9}) action configs of the two rules re-worded in v4. */
  private static Map<Integer, Map<String, Object>> v3Configs(String slug) {
    if (OVERDUE_SLUG.equals(slug)) {
      var email = new LinkedHashMap<String, Object>();
      email.put("recipientType", "CUSTOMER_CONTACT");
      email.put("subject", "Invoice {{invoice.invoiceNumber}} is overdue");
      email.put(
          "body",
          "Dear {{customer.name}},\n\nYour invoice {{invoice.invoiceNumber}} is now overdue."
              + " Please arrange payment at your earliest convenience.");
      var notify = new LinkedHashMap<String, Object>();
      notify.put("recipientType", "PROJECT_OWNER");
      notify.put("title", "Invoice {{invoice.invoiceNumber}} still overdue");
      notify.put(
          "message",
          "Invoice {{invoice.invoiceNumber}} for {{customer.name}} has been overdue for 7 days.");
      return Map.of(0, email, 1, notify);
    }
    var notify = new LinkedHashMap<String, Object>();
    notify.put("recipientType", "ORG_ADMINS");
    notify.put("title", "Budget alert: {{project.name}}");
    notify.put("message", "Project {{project.name}} has reached its budget threshold.");
    return Map.of(0, notify);
  }

  /**
   * Rewrites the provisioned rule to its exact v3 shipped state (hash pinned → pristine), then
   * optionally edits the SEND_EMAIL subject (firm edit after the pin), and rewinds the recorded
   * version 4 → 3.
   */
  private void simulateV3Rule(String slug, boolean firmEditsSubject) {
    inTenantTx(
        () -> {
          var rule = seededRule(slug);
          boolean overdue = OVERDUE_SLUG.equals(slug);
          rule.update(
              overdue ? "Overdue Invoice Reminder" : "Budget Alert Escalation",
              overdue
                  ? "Sends email to customer when invoice becomes overdue, then notifies project"
                      + " owner after 7 days"
                  : "Notifies admins when a project budget threshold is reached",
              rule.getTriggerType(),
              rule.getTriggerConfig(),
              rule.getConditions());
          var configs = v3Configs(slug);
          var actions = actionRepository.findByRuleIdOrderBySortOrder(rule.getId());
          assertThat(actions).hasSize(configs.size());
          for (AutomationAction action : actions) {
            action.update(
                action.getActionType(),
                new LinkedHashMap<>(configs.get(action.getSortOrder())),
                action.getSortOrder(),
                action.getDelayDuration(),
                action.getDelayUnit());
          }
          rule.setContentHash(automationPackInstaller.computeRuleHash(rule));
          if (firmEditsSubject) {
            var email = actions.getFirst();
            var edited = new LinkedHashMap<>(email.getActionConfig());
            edited.put("subject", CUSTOM_SUBJECT);
            email.update(
                email.getActionType(),
                edited,
                email.getSortOrder(),
                email.getDelayDuration(),
                email.getDelayUnit());
          }
          ruleRepository.save(rule);
          actionRepository.saveAll(actions);
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getPackStatus().recordAutomationPackApplication(PACK_ID, 3);
          orgSettingsRepository.save(settings);
          assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, "4", "3"))
              .as("rewind %s 4 -> 3", PACK_ID)
              .isEqualTo(1);
          return null;
        });
  }

  /**
   * Puts the rule back to the shipped v4 action configs with a pristine hash, so sibling tests
   * (which assert "no uninstall blocker") stay order-independent.
   */
  private void restoreShipped(String slug) {
    inTenantTx(
        () -> {
          var rule = seededRule(slug);
          for (AutomationAction action :
              actionRepository.findByRuleIdOrderBySortOrder(rule.getId())) {
            var config = new LinkedHashMap<>(action.getActionConfig());
            if (config.containsKey("subject")) {
              config.put("subject", "Fee note {{invoice.invoiceNumber}} is overdue");
            }
            action.update(
                action.getActionType(),
                config,
                action.getSortOrder(),
                action.getDelayDuration(),
                action.getDelayUnit());
            actionRepository.save(action);
          }
          rule.setContentHash(automationPackInstaller.computeRuleHash(rule));
          ruleRepository.save(rule);
          return null;
        });
  }

  private AutomationRule seededRule(String slug) {
    var rules =
        ruleRepository.findPackSeededByTemplateSlug(PACK_ID, slug, RuleSource.TEMPLATE).stream()
            .filter(r -> SYSTEM.equals(r.getCreatedBy()))
            .toList();
    assertThat(rules).hasSize(1);
    return rules.getFirst();
  }

  private Map<String, Object> actionConfig(AutomationRule rule, int sortOrder) {
    return actionRepository.findByRuleIdOrderBySortOrder(rule.getId()).stream()
        .filter(a -> a.getSortOrder() == sortOrder)
        .findFirst()
        .orElseThrow()
        .getActionConfig();
  }

  /**
   * Rewrites the provisioned pack-seeded rule to its exact v2 shipped state with a matching
   * (pristine) content hash, then applies the firm edit. When {@code firmEditsTitle}, the action
   * title is changed after the hash pin so the rule reads as edited. When {@code rewind}, the
   * recorded pack version (pack_install + OrgSettings automation status) goes back to 2.
   *
   * @return the pinned v2 content hash
   */
  private String simulateV2Tenant(
      Consumer<AutomationRule> firmEdit, boolean firmEditsTitle, boolean rewind) {
    return inTenantTx(
        () -> {
          var rule = seededRule();
          rule.update(
              V2_NAME,
              V2_DESC,
              rule.getTriggerType(),
              rule.getTriggerConfig(),
              rule.getConditions());
          if (!rule.isEnabled()) {
            rule.toggle();
          }
          var action = singleAction(rule);
          action.update(ActionType.SEND_NOTIFICATION, v2Config(), 0, null, null);
          String v2Hash = automationPackInstaller.computeRuleHash(rule);
          rule.setContentHash(v2Hash);

          firmEdit.accept(rule);
          if (firmEditsTitle) {
            var edited = new LinkedHashMap<>(action.getActionConfig());
            edited.put("title", CUSTOM_TITLE);
            action.update(ActionType.SEND_NOTIFICATION, edited, 0, null, null);
          }
          ruleRepository.save(rule);
          actionRepository.save(action);

          if (rewind) {
            var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
            settings.getPackStatus().recordAutomationPackApplication(PACK_ID, 2);
            orgSettingsRepository.save(settings);
            assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, "4", "2"))
                .as("rewind %s 4 -> 2", PACK_ID)
                .isEqualTo(1);
          }
          return v2Hash;
        });
  }

  private static void renameTo(AutomationRule rule, String name) {
    rule.update(
        name,
        rule.getDescription(),
        rule.getTriggerType(),
        rule.getTriggerConfig(),
        rule.getConditions());
  }

  private static Map<String, Object> v2Config() {
    var config = new LinkedHashMap<String, Object>();
    config.put("recipientType", "PROJECT_OWNER");
    config.put("title", V2_TITLE);
    config.put("message", V2_MESSAGE);
    return config;
  }

  /**
   * The single SYSTEM-created rule with the slug tagged with automation-common's install (must run
   * inside a tx).
   */
  private AutomationRule seededRule() {
    var rules =
        ruleRepository.findPackSeededByTemplateSlug(PACK_ID, SLUG, RuleSource.TEMPLATE).stream()
            .filter(r -> SYSTEM.equals(r.getCreatedBy()))
            .toList();
    assertThat(rules).hasSize(1);
    return rules.getFirst();
  }

  private AutomationAction singleAction(AutomationRule rule) {
    var actions = actionRepository.findByRuleIdOrderBySortOrder(rule.getId());
    assertThat(actions).hasSize(1);
    return actions.getFirst();
  }

  private Map<String, Object> actionConfig(AutomationRule rule) {
    return singleAction(rule).getActionConfig();
  }

  private int automationStatusVersion() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .orElseThrow()
        .getPackStatus()
        .getAutomationPackStatus()
        .stream()
        .filter(e -> PACK_ID.equals(e.get("packId")))
        .map(e -> ((Number) e.get("version")).intValue())
        .findFirst()
        .orElseThrow();
  }

  private String uninstallReason() {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> automationPackInstaller.checkUninstallable(PACK_ID, schema).blockingReason());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
