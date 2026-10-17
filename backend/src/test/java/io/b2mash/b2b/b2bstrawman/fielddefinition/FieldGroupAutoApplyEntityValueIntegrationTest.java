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
 * OBS-5004: covers the entity-value-aware overload of {@link
 * FieldGroupService#resolveAutoApplyGroupIds(EntityType, String, String)} that the customer create
 * path uses.
 *
 * <p>The bug being prevented: a tenant with both an unscoped and a {@code TRUST}-scoped auto-apply
 * customer field group (originally the since-removed accounting-za packs). Before the fix, every
 * customer — regardless of entity type — inherited both groups, causing trust-specific required
 * fields to inflate the completeness denominator for non-trust clients. The cases below use
 * hand-built groups; no remaining field pack declares {@code applicableEntityValues} (Phase 86 /
 * Epic 604).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldGroupAutoApplyEntityValueIntegrationTest {

  private static final String ORG_ID = "org_fg_aa_entityval_test";

  private final TenantProvisioningService provisioningService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupService fieldGroupService;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  FieldGroupAutoApplyEntityValueIntegrationTest(
      TenantProvisioningService provisioningService,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupService fieldGroupService,
      TransactionTemplate transactionTemplate) {
    this.provisioningService = provisioningService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupService = fieldGroupService;
    this.transactionTemplate = transactionTemplate;
  }

  private String tenantSchema;
  private UUID unscopedGroupId;
  private UUID trustScopedGroupId;
  private UUID ptyLtdScopedGroupId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "FG AA EntityValue Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () -> {
          // Unscoped CUSTOMER group — applicable_entity_values is null, applies to all customers
          var unscoped =
              new FieldGroup(EntityType.CUSTOMER, "Unscoped Customer Group", "fg_aa_ev_unscoped");
          unscoped.setAutoApply(true);
          unscoped.setSortOrder(1);
          unscopedGroupId = fieldGroupRepository.save(unscoped).getId();

          // TRUST-scoped CUSTOMER group — applies only to customers with entityType=TRUST
          var trustScoped =
              new FieldGroup(EntityType.CUSTOMER, "Trust Scoped Group", "fg_aa_ev_trust");
          trustScoped.setAutoApply(true);
          trustScoped.setSortOrder(2);
          trustScoped.setApplicableEntityValues(new ArrayList<>(List.of("TRUST")));
          trustScopedGroupId = fieldGroupRepository.save(trustScoped).getId();

          // PTY_LTD-scoped CUSTOMER group — applies only to customers with entityType=PTY_LTD
          var ptyLtdScoped =
              new FieldGroup(EntityType.CUSTOMER, "PTY_LTD Scoped Group", "fg_aa_ev_pty_ltd");
          ptyLtdScoped.setAutoApply(true);
          ptyLtdScoped.setSortOrder(3);
          ptyLtdScoped.setApplicableEntityValues(new ArrayList<>(List.of("PTY_LTD")));
          ptyLtdScopedGroupId = fieldGroupRepository.save(ptyLtdScoped).getId();
        });
  }

  @Test
  void trust_customer_includes_trust_scoped_excludes_pty_ltd_scoped() {
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, "TRUST");

          assertThat(ids)
              .as("TRUST customer must include unscoped + TRUST-scoped, exclude PTY_LTD-scoped")
              .contains(unscopedGroupId, trustScopedGroupId)
              .doesNotContain(ptyLtdScopedGroupId);
        });
  }

  @Test
  void pty_ltd_customer_includes_pty_ltd_scoped_excludes_trust_scoped() {
    runInTenant(
        () -> {
          var ids =
              fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, "PTY_LTD");

          assertThat(ids)
              .as("PTY_LTD customer must include unscoped + PTY_LTD-scoped, exclude TRUST-scoped")
              .contains(unscopedGroupId, ptyLtdScopedGroupId)
              .doesNotContain(trustScopedGroupId);
        });
  }

  @Test
  void null_entityValue_excludes_all_entity_value_scoped_groups() {
    runInTenant(
        () -> {
          var ids = fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, null);

          assertThat(ids)
              .as("null entityValue should auto-apply ONLY unscoped groups")
              .contains(unscopedGroupId)
              .doesNotContain(trustScopedGroupId, ptyLtdScopedGroupId);
        });
  }

  @Test
  void sole_prop_customer_gets_only_unscoped_groups() {
    runInTenant(
        () -> {
          var ids =
              fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, "SOLE_PROP");

          assertThat(ids)
              .as("SOLE_PROP has no scoped groups — only unscoped should apply")
              .contains(unscopedGroupId)
              .doesNotContain(trustScopedGroupId, ptyLtdScopedGroupId);
        });
  }

  @Test
  void legacy_one_arg_overload_delegates_with_null_entityValue() {
    // Regression guard: the single-arg signature (used by TASK/INVOICE create paths)
    // must still work and behave identically to passing null entityValue.
    runInTenant(
        () -> {
          var legacyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER);
          var explicitNullIds =
              fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, null);

          assertThat(legacyIds)
              .as(
                  "legacy overload must equal three-arg overload with null workType and entityValue")
              .containsExactlyInAnyOrderElementsOf(explicitNullIds);
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }
}
