package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies {@code AbstractPackSeeder}'s vertical-profile filter: a {@code legal-za} tenant receives
 * the legal-za field packs, a tenant without a profile receives none of them, and re-seeding is
 * idempotent. Since Phase 86 (Epic 604, ADR-332) legal-za is the only registered profile, so the
 * control tenant has no profile.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerticalProfileFilteringTest {

  private static final String LEGAL_ORG_ID = "org_vpf_legal";
  private static final String NO_PROFILE_ORG_ID = "org_vpf_noprofile";
  private static final String LEGAL_CUSTOMER_PACK = "legal-za-customer";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private String noProfileSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Law Firm", "legal-za");
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(NO_PROFILE_ORG_ID, "No-Profile Org", null);
    noProfileSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(NO_PROFILE_ORG_ID)
            .orElseThrow()
            .getSchemaName();
  }

  @Test
  void legalTenantGetsLegalCustomerPack() {
    assertThat(legalCustomerFieldCount(legalSchema, LEGAL_ORG_ID)).isPositive();
  }

  @Test
  void noProfileTenantGetsNoLegalPacks() {
    runInTenant(
        noProfileSchema,
        NO_PROFILE_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (EntityType type : List.of(EntityType.CUSTOMER, EntityType.PROJECT)) {
                    var legalFields =
                        fieldDefinitionRepository
                            .findByEntityTypeAndActiveTrueOrderBySortOrder(type)
                            .stream()
                            .filter(
                                f -> f.getPackId() != null && f.getPackId().startsWith("legal-za"))
                            .toList();
                    assertThat(legalFields).as("legal-za fields on %s", type).isEmpty();
                  }
                }));
  }

  @Test
  void reSeedingDoesNotDuplicatePacks() {
    int before = legalCustomerFieldCount(legalSchema, LEGAL_ORG_ID);

    fieldPackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);

    assertThat(legalCustomerFieldCount(legalSchema, LEGAL_ORG_ID)).isEqualTo(before);
  }

  private int legalCustomerFieldCount(String schema, String orgId) {
    var count = new AtomicInteger();
    runInTenant(
        schema,
        orgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    count.set(
                        (int)
                            fieldDefinitionRepository
                                .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
                                .stream()
                                .filter(f -> LEGAL_CUSTOMER_PACK.equals(f.getPackId()))
                                .count())));
    return count.get();
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
