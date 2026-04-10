package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.PromotedFieldSlugs;
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
    provisioningService.provisionTenant(ORG_ID, "FPS Test Org", null);
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
                  // Post-Epic-462: common-customer pack is deleted (all fields promoted to
                  // structural columns). Only compliance pack customer fields remain for the
                  // default (null vertical) tenant.
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var fieldPackCustomerFields =
                      customerFields.stream()
                          .filter(f -> "common-customer".equals(f.getPackId()))
                          .toList();
                  assertThat(fieldPackCustomerFields).isEmpty();

                  // common-project now has exactly 1 field (`category`) after promoting
                  // reference_number and priority to structural columns.
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  assertThat(projectFields).hasSize(1);
                  assertThat(projectFields.getFirst().getSlug()).isEqualTo("category");
                  assertThat(projectFields.getFirst().getPackId()).isEqualTo("common-project");
                }));
  }

  @Test
  void seedPacksForTenantCreatesFieldGroupsAndMembers() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // common-customer group no longer exists.
                  var customerGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var fieldPackGroups =
                      customerGroups.stream()
                          .filter(g -> "common-customer".equals(g.getPackId()))
                          .toList();
                  assertThat(fieldPackGroups).isEmpty();

                  // Project group still exists but now has 1 member (`category` only).
                  var projectGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  assertThat(projectGroups).hasSize(1);
                  assertThat(projectGroups.getFirst().getSlug()).isEqualTo("project_info");

                  var projectMembers =
                      fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(
                          projectGroups.getFirst().getId());
                  assertThat(projectMembers).hasSize(1);
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
                  // Post-Epic-462: common-customer and common-invoice packs deleted.
                  // Only common-project and common-task remain.
                  assertThat(settings.getFieldPackStatus()).hasSize(2);

                  List<String> packIds =
                      settings.getFieldPackStatus().stream()
                          .map(entry -> (String) entry.get("packId"))
                          .toList();
                  assertThat(packIds).containsExactlyInAnyOrder("common-project", "common-task");

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
                  // Counts should remain the same as initial seeding.
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);

                  var fieldPackCustomerFields =
                      customerFields.stream()
                          .filter(f -> "common-customer".equals(f.getPackId()))
                          .toList();
                  assertThat(fieldPackCustomerFields).isEmpty();
                  assertThat(projectFields).hasSize(1);

                  // Project group still singleton — idempotent re-seed.
                  var projectGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  assertThat(projectGroups).hasSize(1);
                }));
  }

  @Test
  void commonProjectPackSeedsCategoryFieldOnly() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // common-project is the only common pack with CUSTOMER/PROJECT scope that
                  // still applies. Verify its group + `category` field are present.
                  var projectGroup =
                      fieldGroupRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "project_info")
                          .orElseThrow();
                  assertThat(projectGroup.getPackId()).isEqualTo("common-project");

                  var category =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "category")
                          .orElseThrow();
                  assertThat(category.getEntityType()).isEqualTo(EntityType.PROJECT);
                  assertThat(category.getPackId()).isEqualTo("common-project");
                }));
  }

  @Test
  void packFieldDefinitionsHavePackIdAndPackFieldKeySet() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
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
                    assertThat(fd.getPackFieldKey())
                        .as("packFieldKey matches slug for " + fd.getSlug())
                        .isEqualTo(fd.getSlug());
                  }
                }));
  }

  @Test
  void promotedSlugsAreNotSeededAsFieldDefinitions() {
    // Epic 462 guarantee: promoted slugs must not be re-created as FieldDefinitions for newly
    // provisioned tenants (they've moved to structural columns and backward-compat aliases).
    // Slug lists are sourced from PromotedFieldSlugs to stay in sync with the single source of
    // truth used by VariableMetadataRegistry and the template context builders. Task has no
    // promoted custom-field slugs yet (PromotedFieldSlugs.TASK is empty), so no TASK loop.

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (String slug : PromotedFieldSlugs.CUSTOMER) {
                    assertThat(
                            fieldDefinitionRepository.findByEntityTypeAndSlug(
                                EntityType.CUSTOMER, slug))
                        .as("Customer slug %s should NOT be seeded post-Epic-462", slug)
                        .isEmpty();
                  }
                  for (String slug : PromotedFieldSlugs.PROJECT) {
                    assertThat(
                            fieldDefinitionRepository.findByEntityTypeAndSlug(
                                EntityType.PROJECT, slug))
                        .as("Project slug %s should NOT be seeded post-Epic-462", slug)
                        .isEmpty();
                  }
                  for (String slug : PromotedFieldSlugs.INVOICE) {
                    assertThat(
                            fieldDefinitionRepository.findByEntityTypeAndSlug(
                                EntityType.INVOICE, slug))
                        .as("Invoice slug %s should NOT be seeded post-Epic-462", slug)
                        .isEmpty();
                  }
                  for (String slug : PromotedFieldSlugs.TASK) {
                    assertThat(
                            fieldDefinitionRepository.findByEntityTypeAndSlug(
                                EntityType.TASK, slug))
                        .as("Task slug %s should NOT be seeded post-Epic-462", slug)
                        .isEmpty();
                  }
                }));
  }

  @Test
  void commonInvoicePackIsAbsentFromFieldPackStatus() {
    // Epic 462: common-invoice.json was deleted — newly provisioned tenants must not record
    // a FieldPackStatus entry for it.
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  List<String> packIds =
                      settings.getFieldPackStatus().stream()
                          .map(entry -> (String) entry.get("packId"))
                          .toList();
                  assertThat(packIds).doesNotContain("common-invoice");
                  assertThat(packIds).doesNotContain("common-customer");
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
