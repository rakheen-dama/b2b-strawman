package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldPackSeederIntegrationTest {

  private static final String ORG_ID = "org_fps_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    // provisionTenant now calls fieldPackSeeder.seedPacksForTenant internally
    provisioningService.provisionTenant(ORG_ID, "FPS Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedPacksForTenantCreatesFieldDefinitionsFromPacks() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // common-customer has 8 fields + compliance packs add 5 more (3
                  // sa-fica-individual
                  // + 2 sa-fica-company) = 13 total customer fields
                  // common-project has 3 fields
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);

                  assertThat(customerFields).hasSize(13);
                  assertThat(projectFields).hasSize(3);
                }));
  }

  @Test
  void seedPacksForTenantCreatesFieldGroupsAndMembers() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  // 1 from field packs (contact_address) + 3 from compliance packs (identity,
                  // compliance, registration)
                  assertThat(customerGroups).hasSizeGreaterThanOrEqualTo(1);

                  var contactAddressGroup =
                      customerGroups.stream()
                          .filter(g -> "contact_address".equals(g.getSlug()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(contactAddressGroup.getName()).isEqualTo("Contact & Address");

                  var members =
                      fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(
                          contactAddressGroup.getId());
                  assertThat(members).hasSize(8);

                  var projectGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  assertThat(projectGroups).hasSize(1);
                  assertThat(projectGroups.getFirst().getSlug()).isEqualTo("project_info");

                  var projectMembers =
                      fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(
                          projectGroups.getFirst().getId());
                  assertThat(projectMembers).hasSize(3);
                }));
  }

  @Test
  void orgSettingsFieldPackStatusUpdatedWithPackInfo() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getFieldPackStatus()).isNotNull();
                  assertThat(settings.getFieldPackStatus()).hasSize(2);

                  List<String> packIds =
                      settings.getFieldPackStatus().stream()
                          .map(entry -> (String) entry.get("packId"))
                          .toList();
                  assertThat(packIds)
                      .containsExactlyInAnyOrder("common-customer", "common-project");

                  // Verify each entry has version and appliedAt
                  for (Map<String, Object> entry : settings.getFieldPackStatus()) {
                    assertThat(entry).containsKey("version");
                    assertThat(entry).containsKey("appliedAt");
                  }
                }));
  }

  @Test
  void reSeedingSameTenantIsIdempotentNoDuplicates() {
    // Call seeder again — should be idempotent
    fieldPackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Counts should remain the same as initial seeding (field packs only)
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);

                  // 8 from field packs + 5 from compliance packs = 13
                  assertThat(customerFields).hasSize(13);
                  assertThat(projectFields).hasSize(3);

                  var projectGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);

                  assertThat(projectGroups).hasSize(1);
                }));
  }

  @Test
  void twoPacksSeedIndependently() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Verify both packs were applied
                  var customerGroup =
                      fieldGroupRepository
                          .findByEntityTypeAndSlug(EntityType.CUSTOMER, "contact_address")
                          .orElseThrow();
                  assertThat(customerGroup.getPackId()).isEqualTo("common-customer");

                  var projectGroup =
                      fieldGroupRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "project_info")
                          .orElseThrow();
                  assertThat(projectGroup.getPackId()).isEqualTo("common-project");

                  // Verify fields are correctly associated to their entity types
                  var addressLine1 =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.CUSTOMER, "address_line1")
                          .orElseThrow();
                  assertThat(addressLine1.getEntityType()).isEqualTo(EntityType.CUSTOMER);

                  var refNumber =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "reference_number")
                          .orElseThrow();
                  assertThat(refNumber.getEntityType()).isEqualTo(EntityType.PROJECT);
                }));
  }

  @Test
  void packFieldDefinitionsHavePackIdAndPackFieldKeySet() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);

                  // Filter to only field pack fields (not compliance pack fields)
                  var fieldPackCustomerFields =
                      customerFields.stream()
                          .filter(fd -> "common-customer".equals(fd.getPackId()))
                          .toList();
                  assertThat(fieldPackCustomerFields).hasSize(8);

                  for (FieldDefinition fd : fieldPackCustomerFields) {
                    assertThat(fd.getPackId())
                        .as("packId for " + fd.getSlug())
                        .isEqualTo("common-customer");
                    assertThat(fd.getPackFieldKey())
                        .as("packFieldKey for " + fd.getSlug())
                        .isNotNull();
                    assertThat(fd.getPackFieldKey())
                        .as("packFieldKey matches slug for " + fd.getSlug())
                        .isEqualTo(fd.getSlug());
                  }

                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);

                  for (FieldDefinition fd : projectFields) {
                    assertThat(fd.getPackId())
                        .as("packId for " + fd.getSlug())
                        .isEqualTo("common-project");
                    assertThat(fd.getPackFieldKey())
                        .as("packFieldKey for " + fd.getSlug())
                        .isNotNull();
                  }
                }));
  }

  private void runInTenant(String schema, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
