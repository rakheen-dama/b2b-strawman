package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.DealPipelinePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the {@link DealPipelinePackSeeder}: V130 runs clean, the right vertical pipeline is
 * seeded (one pipeline per tenant), entities round-trip, and re-provisioning is idempotent.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DealPipelinePackSeederTest {

  private static final String LEGAL_ORG_ID = "org_deal_pipeline_legal";
  private static final String DEFAULT_ORG_ID = "org_deal_pipeline_default";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DealPipelinePackSeeder dealPipelinePackSeeder;
  @Autowired private PipelineStageRepository pipelineStageRepository;
  @Autowired private DealRepository dealRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private String defaultSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Deal Pipeline Legal Org", "legal-za");
    provisioningService.provisionTenant(DEFAULT_ORG_ID, "Deal Pipeline Default Org", null);
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();
    defaultSchema =
        orgSchemaMappingRepository.findByClerkOrgId(DEFAULT_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void seedsDefaultProfileStagesForProfilelessTenant() {
    dealPipelinePackSeeder.seedPacksForTenant(defaultSchema, DEFAULT_ORG_ID);
    runInTenant(
        defaultSchema,
        DEFAULT_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  List<PipelineStage> stages =
                      pipelineStageRepository.findAllByOrderByPositionAsc();
                  assertThat(stages)
                      .extracting(PipelineStage::getName)
                      .containsExactly("Lead", "Qualified", "Proposal", "Won", "Lost");
                  assertThat(stages)
                      .extracting(PipelineStage::getStageType)
                      .containsExactly(
                          StageType.OPEN,
                          StageType.OPEN,
                          StageType.OPEN,
                          StageType.WON,
                          StageType.LOST);
                }));
  }

  @Test
  @Order(2)
  void seedsLegalZaStagesWithCorrectTypesAndOnlyOnePipeline() {
    dealPipelinePackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  List<PipelineStage> stages =
                      pipelineStageRepository.findAllByOrderByPositionAsc();
                  // legal-za pack only — the universal default pack must be skipped (one pipeline).
                  // Exactly the 5 legal stages, NOT default(5)+legal(5)=10.
                  assertThat(stages).hasSize(5);
                  assertThat(stages)
                      .extracting(PipelineStage::getName)
                      .containsExactly("Enquiry", "Conflict check", "Engagement", "Won", "Lost");
                  assertThat(stages).filteredOn(s -> s.getStageType() == StageType.WON).hasSize(1);
                  assertThat(stages).filteredOn(s -> s.getStageType() == StageType.LOST).hasSize(1);

                  // Idempotency ledger must record ONLY the legal pack — the default packId must
                  // never be falsely recorded for a profiled tenant (FINDING 1).
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  var packStatus = settings.getPackStatus().getDealPipelinePackStatus();
                  assertThat(packStatus)
                      .extracting(entry -> entry.get("packId"))
                      .containsExactly("deal-pipeline-legal-za")
                      .doesNotContain("deal-pipeline-default");
                }));
  }

  @Test
  @Order(3)
  void entitiesRoundTripViaFindOneById() {
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  PipelineStage openStage =
                      pipelineStageRepository
                          .findFirstByStageTypeAndArchivedFalseOrderByPositionAsc(StageType.OPEN)
                          .orElseThrow();
                  PipelineStage reloaded = pipelineStageRepository.findOneById(openStage.getId());
                  assertThat(reloaded.getName()).isEqualTo("Enquiry");

                  Deal deal =
                      Deal.create(
                          "DEAL-9001",
                          UUID.randomUUID(),
                          "Round-trip deal",
                          openStage,
                          new BigDecimal("1000.00"),
                          "ZAR",
                          UUID.randomUUID(),
                          "manual",
                          UUID.randomUUID());
                  Deal saved = dealRepository.save(deal);
                  Deal fetched = dealRepository.findOneById(saved.getId());
                  assertThat(fetched.getDealNumber()).isEqualTo("DEAL-9001");
                  assertThat(fetched.getStatus()).isEqualTo(DealStatus.OPEN);
                  assertThat(fetched.getStageId()).isEqualTo(openStage.getId());
                  assertThat(dealRepository.findByCustomerId(fetched.getCustomerId())).hasSize(1);
                }));
  }

  @Test
  @Order(4)
  void reProvisionIsIdempotent() {
    dealPipelinePackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    dealPipelinePackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long openCount =
                      pipelineStageRepository.findAllByOrderByPositionAsc().stream()
                          .filter(s -> "Enquiry".equals(s.getName()))
                          .count();
                  assertThat(openCount).isEqualTo(1); // not duplicated
                }));
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
