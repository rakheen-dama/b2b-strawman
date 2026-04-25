package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.ArrayList;
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
 * GAP-L-37-regression-2026-04-25: covers the work-type-aware overload of {@link
 * FieldGroupService#resolveAutoApplyGroupIds(EntityType, String)} that the project create paths
 * use.
 *
 * <p>The bug being prevented: a legal-za tenant has both {@code legal-za-project} (unscoped) and
 * {@code conveyancing-za-project} (scoped to {@code CONVEYANCING}) auto-apply field groups
 * installed by the FieldPackSeeder. Before the fix, every project — regardless of work_type —
 * inherited both groups, surfacing conveyancing-only fields like Erf Number and Deeds Office on
 * litigation matters. After the fix, the conveyancing group is filtered out unless the project's
 * work_type is {@code CONVEYANCING}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldGroupAutoApplyWorkTypeIntegrationTest {

  private static final String ORG_ID = "org_fg_aa_worktype_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupService fieldGroupService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID unscopedGroupId;
  private UUID conveyancingGroupId;
  private UUID litigationGroupId;
  private UUID customerUnscopedGroupId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "FG AA WorkType Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () -> {
          // Unscoped PROJECT group — applicable_work_types is null, applies to all projects
          var unscoped =
              new FieldGroup(EntityType.PROJECT, "Unscoped Project Group", "fg_aa_wt_unscoped");
          unscoped.setAutoApply(true);
          unscoped.setSortOrder(1);
          unscopedGroupId = fieldGroupRepository.save(unscoped).getId();

          // CONVEYANCING-scoped PROJECT group — applies only to projects with workType=CONVEYANCING
          var conveyancing =
              new FieldGroup(
                  EntityType.PROJECT, "Conveyancing Scoped Group", "fg_aa_wt_conveyancing");
          conveyancing.setAutoApply(true);
          conveyancing.setSortOrder(2);
          conveyancing.setApplicableWorkTypes(new ArrayList<>(List.of("CONVEYANCING")));
          conveyancingGroupId = fieldGroupRepository.save(conveyancing).getId();

          // LITIGATION-scoped PROJECT group — applies only to projects with workType=LITIGATION
          var litigation =
              new FieldGroup(EntityType.PROJECT, "Litigation Scoped Group", "fg_aa_wt_litigation");
          litigation.setAutoApply(true);
          litigation.setSortOrder(3);
          litigation.setApplicableWorkTypes(new ArrayList<>(List.of("LITIGATION")));
          litigationGroupId = fieldGroupRepository.save(litigation).getId();

          // Unscoped CUSTOMER group — for the regression-guard test that the legacy overload
          // still works for non-PROJECT entity types and ignores work_type filtering.
          var customer =
              new FieldGroup(EntityType.CUSTOMER, "Unscoped Customer Group", "fg_aa_wt_customer");
          customer.setAutoApply(true);
          customer.setSortOrder(1);
          customerUnscopedGroupId = fieldGroupRepository.save(customer).getId();
        });
  }

  @Test
  void litigation_project_excludes_conveyancing_scoped_group() {
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, "LITIGATION");

          assertThat(ids)
              .as(
                  "litigation project must include unscoped + LITIGATION-scoped, exclude"
                      + " CONVEYANCING-scoped")
              .contains(unscopedGroupId, litigationGroupId)
              .doesNotContain(conveyancingGroupId);
        });
  }

  @Test
  void conveyancing_project_includes_conveyancing_scoped_group() {
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, "CONVEYANCING");

          assertThat(ids)
              .as(
                  "conveyancing project must include unscoped + CONVEYANCING-scoped, exclude"
                      + " LITIGATION-scoped")
              .contains(unscopedGroupId, conveyancingGroupId)
              .doesNotContain(litigationGroupId);
        });
  }

  @Test
  void null_workType_excludes_any_workType_scoped_group() {
    // Templates without an explicit workType (e.g. the recurring-schedule path) get the
    // unscoped groups only — work-type-scoped groups are correctly skipped because the caller
    // hasn't declared a work_type and therefore can't satisfy any predicate.
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, null);

          assertThat(ids)
              .as(
                  "null workType should auto-apply ONLY unscoped groups, never any work-type-"
                      + "scoped group (correct: the caller has not asserted a work_type to match)")
              .contains(unscopedGroupId)
              .doesNotContain(conveyancingGroupId, litigationGroupId);
        });
  }

  @Test
  void empty_workType_string_excludes_any_workType_scoped_group() {
    // Defensive: empty-string workType is treated the same as null (cannot satisfy a non-empty
    // applicable_work_types predicate). This guards against frontend regressions sending "".
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, "");

          assertThat(ids)
              .as("empty workType behaves like null — only unscoped groups apply")
              .contains(unscopedGroupId)
              .doesNotContain(conveyancingGroupId, litigationGroupId);
        });
  }

  @Test
  void legacy_overload_delegates_with_null_workType() {
    // Regression guard: the existing single-arg signature (used by CUSTOMER/TASK/INVOICE create
    // paths) must still work and behave identically to passing null workType. CUSTOMER groups
    // never declare applicable_work_types, so they all apply via either overload.
    runInTenant(
        () -> {
          var customerIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER);
          assertThat(customerIds)
              .as("legacy overload still resolves CUSTOMER auto-apply groups untouched")
              .contains(customerUnscopedGroupId);

          // PROJECT via legacy overload should match PROJECT via two-arg overload with null.
          var legacyProjectIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT);
          var explicitNullIds =
              fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, null);
          assertThat(legacyProjectIds)
              .as("legacy overload must equal two-arg overload with null workType")
              .containsExactlyInAnyOrderElementsOf(explicitNullIds);
        });
  }

  /**
   * Verifies that the {@code conveyancing-za-project} pack JSON ships the work-type predicate so
   * that legal-za tenants get the GAP-L-37 fix end-to-end without any tenant-specific overrides.
   * This catches a regression where a future edit to {@link FieldPackGroup} or the seeder drops the
   * field on the wire.
   */
  @Test
  void seeder_persists_applicableWorkTypes_for_conveyancing_pack() {
    // The default tenant doesn't get conveyancing-za-project (it's gated on legal-za vertical).
    // Provision a fresh legal-za tenant and verify the persisted row has the predicate.
    String legalOrgId = "org_fg_aa_wt_legal_test";
    provisioningService.provisionTenant(legalOrgId, "Legal Pack WT Test Org", "legal-za");
    String legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(legalOrgId).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, legalOrgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var groups =
                          fieldGroupRepository.findByEntityTypeAndAutoApplyTrueAndActiveTrue(
                              EntityType.PROJECT);
                      var conveyancing =
                          groups.stream()
                              .filter(g -> "conveyancing-za-project".equals(g.getPackId()))
                              .findFirst()
                              .orElseThrow(
                                  () ->
                                      new AssertionError(
                                          "conveyancing-za-project group not seeded in legal-za"
                                              + " tenant"));
                      assertThat(conveyancing.getApplicableWorkTypes())
                          .as(
                              "conveyancing-za-project pack JSON must persist"
                                  + " applicableWorkTypes=[CONVEYANCING] via seeder")
                          .containsExactly("CONVEYANCING");

                      // legal-za-project stays unscoped — must NOT have applicable_work_types set.
                      var legal =
                          groups.stream()
                              .filter(g -> "legal-za-project".equals(g.getPackId()))
                              .findFirst()
                              .orElseThrow(
                                  () ->
                                      new AssertionError(
                                          "legal-za-project group not seeded in legal-za tenant"));
                      assertThat(legal.getApplicableWorkTypes())
                          .as(
                              "legal-za-project pack must remain unscoped — applies to all"
                                  + " legal-za matters regardless of work_type")
                          .isNullOrEmpty();
                    }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }
}
