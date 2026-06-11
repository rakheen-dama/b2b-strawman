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
 * Integration tests for the {@code automation-legal-za} pack: asserts that provisioning a {@code
 * legal-za} tenant auto-installs the legal automation rules via the {@link AutomationPackInstaller}
 * pipeline.
 *
 * <p>This test is the reproduction harness for risk-register item B-05 / H-04, which claimed that
 * legal-za tenants do NOT auto-install {@code automation-legal-za} because {@code legal-za.json}
 * has no {@code packs.automation} key. The provisioning install path ( {@code
 * TenantProvisioningService.installPacksViaPipeline} → {@code
 * PackCatalogService.getPackIdsForProfile}) filters by the pack's own {@code verticalProfile}
 * field, not by the profile JSON's {@code packs.automation} key, so the pack DOES auto-install. If
 * this test passes on unchanged code, B-05's premise is disproven.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalZaAutomationPackTest {

  private static final String ORG_ID = "org_lz_auto_pack_test";

  private static final String PACK_ID = "automation-legal-za";

  private static final List<String> EXPECTED_SLUGS =
      List.of(
          "matter-onboarding-reminder",
          "engagement-letter-followup",
          "investment-maturity-reminder",
          "reconciliation-overdue-reminder",
          "pending-approval-aging");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Legal ZA Automation Pack Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void provisioningAutoInstallsThePack() {
    // No additional install call here — the @BeforeAll provisionTenant should have done it.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(packInstallRepository.findByPackId(PACK_ID))
                        .as("Pack %s should auto-install during provisionTenant(legal-za)", PACK_ID)
                        .isPresent()));
  }

  @Test
  void provisioningCreatesPackInstallWithExpectedRules() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  assertThat(install.getPackType()).isEqualTo(PackType.AUTOMATION_TEMPLATE);
                  assertThat(install.getItemCount()).isEqualTo(EXPECTED_SLUGS.size());
                }));
  }

  @Test
  void allRulesHaveSourcePackInstallIdAndContentHash() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var rules = ruleRepository.findBySourcePackInstallId(install.getId());
                  assertThat(rules).hasSize(EXPECTED_SLUGS.size());
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

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
