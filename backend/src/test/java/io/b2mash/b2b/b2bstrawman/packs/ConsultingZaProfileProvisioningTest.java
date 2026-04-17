package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
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
 * Integration tests for the {@code consulting-za} vertical profile: asserts the profile manifest
 * loads, that tenant provisioning with {@code consulting-za} seeds the customer + project field
 * packs, enforces {@code campaign_type} as required, installs the conditional visibility rule on
 * {@code retainer_tier}, and writes {@code verticalProfile} to {@link
 * io.b2mash.b2b.b2bstrawman.settings.OrgSettings}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaProfileProvisioningTest {

  private static final String ORG_ID = "org_czpp_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private VerticalProfileRegistry verticalProfileRegistry;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Consulting ZA Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void registryLoadsConsultingZaManifest() {
    var profile = verticalProfileRegistry.getProfile("consulting-za");
    assertThat(profile).isPresent();
    var p = profile.get();
    assertThat(p.profileId()).isEqualTo("consulting-za");
    assertThat(p.name()).isEqualTo("South African Agency & Consulting Firm");
    assertThat(p.currency()).isEqualTo("ZAR");
    // GAP-C-04 + GAP-C-07: consulting-za ships with resource_planning (TeamUtilizationWidget)
    // and automation_builder (Automations UI) enabled so the dashboard and Day-45 automations
    // checkpoint work without manual module toggling.
    assertThat(p.enabledModules())
        .containsExactlyInAnyOrder("resource_planning", "automation_builder");
    assertThat(p.terminologyNamespace()).isEqualTo("en-ZA-consulting");
    assertThat(p.packs()).containsKey("field");
  }

  @Test
  void orgSettingsEnablesResourcePlanningAndAutomationBuilder() {
    // GAP-C-04 + GAP-C-07: verify the freshly provisioned consulting-za tenant has both
    // modules written to org_settings.enabled_modules so moduleGuard.requireModule(...) does
    // not 403 on UtilizationService / AutomationRuleController.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getEnabledModules())
                      .containsExactlyInAnyOrder("resource_planning", "automation_builder");
                }));
  }

  @Test
  void provisioningInstallsCustomerAndProjectFieldPacks() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository
                          .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
                          .stream()
                          .filter(f -> "consulting-za-customer".equals(f.getPackId()))
                          .toList();
                  assertThat(customerFields).hasSize(5);
                  var customerSlugs =
                      customerFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(customerSlugs)
                      .containsExactlyInAnyOrder(
                          "industry",
                          "company_size",
                          "primary_stakeholder",
                          "msa_signed",
                          "msa_start_date");

                  var projectFields =
                      fieldDefinitionRepository
                          .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.PROJECT)
                          .stream()
                          .filter(f -> "consulting-za-project".equals(f.getPackId()))
                          .toList();
                  assertThat(projectFields).hasSize(5);
                  var projectSlugs = projectFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(projectSlugs)
                      .containsExactlyInAnyOrder(
                          "campaign_type",
                          "channel",
                          "deliverable_type",
                          "retainer_tier",
                          "creative_brief_url");
                }));
  }

  @Test
  void campaignTypeIsRequired() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var campaignType =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "campaign_type")
                          .orElseThrow();
                  assertThat(campaignType.isRequired()).isTrue();
                }));
  }

  @Test
  void retainerTierHasConditionalVisibility() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var retainerTier =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "retainer_tier")
                          .orElseThrow();
                  var vc = retainerTier.getVisibilityCondition();
                  assertThat(vc).isNotNull();
                  assertThat(vc.get("dependsOnSlug")).isEqualTo("campaign_type");
                  assertThat(vc.get("operator")).isEqualTo("in");
                  assertThat(vc.get("value"))
                      .asList()
                      .containsExactlyInAnyOrder("SOCIAL_MEDIA_RETAINER", "CONTENT_MARKETING");
                }));
  }

  @Test
  void orgSettingsVerticalProfileIsConsultingZa() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getVerticalProfile()).isEqualTo("consulting-za");
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
