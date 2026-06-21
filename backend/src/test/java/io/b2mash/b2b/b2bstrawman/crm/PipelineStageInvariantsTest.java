package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.DealPipelinePackSeeder;
import java.math.BigDecimal;
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
 * Verifies the {@link PipelineStageService} invariants: a pipeline must always retain at least one
 * non-archived OPEN, WON, and LOST stage, and a stage with deals cannot be deleted (archive
 * instead).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineStageInvariantsTest {

  private static final String ORG_ID = "org_pipeline_invariants";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DealPipelinePackSeeder dealPipelinePackSeeder;
  @Autowired private PipelineStageService pipelineStageService;
  @Autowired private PipelineStageRepository pipelineStageRepository;
  @Autowired private DealRepository dealRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Pipeline Invariants Org", "accounting-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    dealPipelinePackSeeder.seedPacksForTenant(schema, ORG_ID);
  }

  @Test
  @Order(4) // Mutation-heavy: permanently archives OPEN stages — must run last.
  void cannotArchiveLastOpenStage() {
    runInTenant(
        () -> {
          // Accounting pack has 3 OPEN stages; archive two so only one OPEN remains.
          archiveExtraStagesOfType(StageType.OPEN);
          UUID lastOpenId = firstActiveStageId(StageType.OPEN);
          assertThatThrownBy(() -> pipelineStageService.archiveStage(lastOpenId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  @Order(1)
  void cannotArchiveLastWonStage() {
    runInTenant(
        () -> {
          UUID wonId = firstActiveStageId(StageType.WON);
          assertThatThrownBy(() -> pipelineStageService.archiveStage(wonId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  @Order(2)
  void cannotArchiveLastLostStage() {
    runInTenant(
        () -> {
          UUID lostId = firstActiveStageId(StageType.LOST);
          assertThatThrownBy(() -> pipelineStageService.archiveStage(lostId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  @Order(3)
  void cannotDeleteStageWithDeals() {
    runInTenant(
        () -> {
          UUID openId = firstActiveStageId(StageType.OPEN);
          // Insert a deal referencing this stage.
          transactionTemplate.executeWithoutResult(
              tx -> {
                PipelineStage stage = pipelineStageRepository.findOneById(openId);
                Deal deal =
                    Deal.create(
                        "DEAL-8001",
                        UUID.randomUUID(),
                        "Stage-attached deal",
                        stage,
                        new BigDecimal("500.00"),
                        "ZAR",
                        UUID.randomUUID(),
                        "manual",
                        UUID.randomUUID());
                dealRepository.save(deal);
              });
          assertThatThrownBy(() -> pipelineStageService.deleteStage(openId))
              .isInstanceOf(ResourceConflictException.class);
        });
  }

  // --- Helpers (run inside tenant scope) ---

  private UUID firstActiveStageId(StageType type) {
    return pipelineStageRepository
        .findFirstByStageTypeAndArchivedFalseOrderByPositionAsc(type)
        .orElseThrow()
        .getId();
  }

  /** Archives all but one active stage of the given type so the type is at its minimum (1). */
  private void archiveExtraStagesOfType(StageType type) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          var stages =
              pipelineStageRepository.findAllByOrderByPositionAsc().stream()
                  .filter(s -> !s.isArchived())
                  .filter(s -> s.getStageType() == type)
                  .toList();
          // Leave the first; archive the rest directly via the entity (bypassing the invariant).
          for (int i = 1; i < stages.size(); i++) {
            stages.get(i).archive();
            pipelineStageRepository.save(stages.get(i));
          }
          assertThat(
                  pipelineStageRepository.findAllByOrderByPositionAsc().stream()
                      .filter(s -> !s.isArchived())
                      .filter(s -> s.getStageType() == type)
                      .count())
              .isEqualTo(1);
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
