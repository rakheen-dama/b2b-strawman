package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code automation-consulting-za} pack: asserts the pack JSON is
 * discovered via the Phase 65 {@link AutomationPackInstaller} pipeline, that provisioning a {@code
 * consulting-za} tenant auto-installs all 6 agency rules, and that each rule references an existing
 * {@link TriggerType} (no novel trigger types invented).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaAutomationPackTest {

  private static final String ORG_ID = "org_cz_auto_pack_test";

  private static final String PACK_ID = "automation-consulting-za";

  private static final List<String> EXPECTED_SLUGS =
      List.of(
          "consulting-za-budget-80",
          "consulting-za-budget-exceeded",
          "consulting-za-retainer-closing",
          "consulting-za-task-blocked-7d",
          "consulting-za-unbilled-time-30d",
          "consulting-za-proposal-followup-5d");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Automation Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void provisioningCreatesPackInstallWithSixRules() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  assertThat(install.getPackType()).isEqualTo(PackType.AUTOMATION_TEMPLATE);
                  assertThat(install.getItemCount()).isEqualTo(6);
                }));
  }

  @Test
  void allSixRulesHaveSourcePackInstallIdAndContentHash() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var rules = ruleRepository.findBySourcePackInstallId(install.getId());
                  assertThat(rules).hasSize(6);
                  assertThat(rules)
                      .allSatisfy(
                          r -> {
                            assertThat(r.getSourcePackInstallId()).isEqualTo(install.getId());
                            assertThat(r.getContentHash())
                                .isNotNull()
                                .hasSize(64)
                                .matches("[0-9a-f]{64}");
                            assertThat(r.getSource()).isEqualTo(RuleSource.TEMPLATE);
                          });
                  var slugs = rules.stream().map(AutomationRule::getTemplateSlug).toList();
                  assertThat(slugs).containsExactlyInAnyOrderElementsOf(EXPECTED_SLUGS);
                }));
  }

  @Test
  void everyRuleUsesAnExistingTriggerType() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var rules = ruleRepository.findBySourcePackInstallId(install.getId());
                  var allowed = List.of(TriggerType.values());
                  assertThat(rules)
                      .allSatisfy(
                          r ->
                              assertThat(allowed)
                                  .as("Rule '%s' triggerType must be in the enum", r.getName())
                                  .contains(r.getTriggerType()));
                }));
  }

  @Test
  void provisioningAutoInstallsThePack() {
    // No additional install call here — the @BeforeAll provisionTenant should have done it.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(packInstallRepository.findByPackId(PACK_ID))
                        .as(
                            "Pack %s should auto-install during provisionTenant(consulting-za)",
                            PACK_ID)
                        .isPresent()));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
