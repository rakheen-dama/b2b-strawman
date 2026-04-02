package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingAutomationTemplatePackTest {

  private static final String ORG_ID = "org_aatp_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Accounting Automation Pack Test Org", "accounting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void accountingTenantGets3RulesFromAccountingAutomationPack() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var allRules = ruleRepository.findAllByOrderByCreatedAtDesc();
                  var accountingRules =
                      allRules.stream()
                          .filter(r -> r.getSource() == RuleSource.TEMPLATE)
                          .filter(
                              r ->
                                  "fica-reminder".equals(r.getTemplateSlug())
                                      || "accounting-za-budget-alert".equals(r.getTemplateSlug())
                                      || "accounting-za-invoice-overdue"
                                          .equals(r.getTemplateSlug()))
                          .toList();
                  assertThat(accountingRules).hasSize(3);
                  var slugs =
                      accountingRules.stream().map(AutomationRule::getTemplateSlug).toList();
                  assertThat(slugs)
                      .containsExactlyInAnyOrder(
                          "fica-reminder",
                          "accounting-za-budget-alert",
                          "accounting-za-invoice-overdue");
                }));
  }

  @Test
  void ficaReminderHas7DayDelay() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var allRules = ruleRepository.findAllByOrderByCreatedAtDesc();
                  var ficaRule =
                      allRules.stream()
                          .filter(r -> "fica-reminder".equals(r.getTemplateSlug()))
                          .findFirst()
                          .orElseThrow();
                  var actions = actionRepository.findByRuleIdOrderBySortOrder(ficaRule.getId());
                  assertThat(actions).hasSize(1);
                  var action = actions.getFirst();
                  assertThat(action.getDelayDuration()).isEqualTo(7);
                  assertThat(action.getDelayUnit()).isEqualTo(DelayUnit.DAYS);
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
