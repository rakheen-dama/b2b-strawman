package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealUpdateRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PipelineSummaryResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PipelineSummaryResponse.StageBreakdown;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aggregation-math tests for {@link PipelineSummaryService} (Epic 578A). The tenant accumulates
 * rows across methods ({@code PER_CLASS}), so each test scopes its assertions to a distinct owner
 * UUID and passes it as the {@code ownerId} filter — the summary then aggregates only that method's
 * deals, sidestepping count-bleed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineSummaryServiceTest {

  private static final String ORG_ID = "org_pipeline_summary_test";
  private static final String OWNER_SUBJECT = "user_pipeline_summary_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PipelineSummaryService pipelineSummaryService;
  @Autowired private DealService dealService;
  @Autowired private DealRepository dealRepository;
  @Autowired private DealTransitionService dealTransitionService;
  @Autowired private PipelineStageService pipelineStageService;
  @PersistenceContext private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private List<PipelineStage> openStages;
  private UUID wonStageId;
  private UUID lostStageId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pipeline Summary Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUBJECT, "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    inTenant(
        () -> {
          openStages =
              pipelineStageService.listStages().stream()
                  .filter(s -> s.getStageType() == StageType.OPEN && !s.isArchived())
                  .toList();
          wonStageId = pipelineStageService.firstWonStage().getId();
          lostStageId = pipelineStageService.firstLostStage().getId();
          return null;
        });
  }

  private <T> T inTenant(Callable<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () -> {
              try {
                return body.call();
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private UUID createCustomer(String name, String email) throws Exception {
    return UUID.fromString(
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT), name, email));
  }

  /** Creates an OPEN deal owned by {@code ownerId} in the given stage with the given value. */
  private UUID createDeal(UUID customerId, UUID ownerId, UUID stageId, String value) {
    return inTenant(
        () -> {
          DealResponse r =
              dealService.createDeal(
                  customerId,
                  "Deal " + UUID.randomUUID(),
                  stageId,
                  new BigDecimal(value),
                  ownerId,
                  "WEBSITE",
                  null,
                  memberId);
          return r.id();
        });
  }

  private void setProbability(UUID dealId, int pct) {
    inTenant(
        () -> {
          dealService.updateDeal(
              dealId, new DealUpdateRequest(null, null, null, null, null, pct, null, null));
          return null;
        });
  }

  private void win(UUID dealId) {
    inTenant(
        () ->
            dealTransitionService.transition(
                dealId, new TransitionRequest(wonStageId, null, null)));
  }

  private void lose(UUID dealId) {
    inTenant(
        () ->
            dealTransitionService.transition(
                dealId, new TransitionRequest(lostStageId, null, "no budget")));
  }

  /**
   * Force {@code created_at}/{@code won_at} via a native UPDATE for exact days-to-close assertions.
   * Reflection on the entity is not enough — {@code created_at} is mapped {@code updatable =
   * false}, so Hibernate omits it from UPDATE statements. A native UPDATE bypasses that mapping.
   */
  private void setTimestamps(UUID dealId, Instant createdAt, Instant wonAt) {
    inTenant(
        () ->
            transactionTemplate.execute(
                status -> {
                  entityManager
                      .createNativeQuery(
                          "UPDATE deals SET created_at = :createdAt, won_at = :wonAt WHERE id = :id")
                      .setParameter("createdAt", createdAt)
                      .setParameter("wonAt", wonAt)
                      .setParameter("id", dealId)
                      .executeUpdate();
                  // Drop the L1 cache so a subsequent read cannot surface the pre-UPDATE entity
                  // (the native UPDATE bypasses the persistence context).
                  entityManager.flush();
                  entityManager.clear();
                  return null;
                }));
  }

  private PipelineSummaryResponse summaryFor(UUID ownerId) {
    return inTenant(() -> pipelineSummaryService.getSummary(null, null, ownerId));
  }

  private PipelineSummaryResponse summaryFor(LocalDate from, LocalDate to, UUID ownerId) {
    return inTenant(() -> pipelineSummaryService.getSummary(from, to, ownerId));
  }

  // --- Tests ---

  @Test
  void weightedValue_overOpenOnly_excludesWonAndLost() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("WV Co", "wv@test.com");
    PipelineStage stage = openStages.get(0);
    int def = stage.getDefaultProbabilityPct();

    // OPEN deal contributes; WON and LOST must not inflate openWeightedValue.
    createDeal(customer, owner, stage.getId(), "1000.00");
    win(createDeal(customer, owner, stage.getId(), "5000.00"));
    lose(createDeal(customer, owner, stage.getId(), "7000.00"));

    PipelineSummaryResponse summary = summaryFor(owner);

    BigDecimal expected =
        new BigDecimal("1000.00").multiply(BigDecimal.valueOf(def)).divide(BigDecimal.valueOf(100));
    assertThat(summary.openWeightedValue()).isEqualByComparingTo(expected);
  }

  @Test
  void perStage_totalsAndCount_correct() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("PS Co", "ps@test.com");
    PipelineStage stage = openStages.get(0);

    createDeal(customer, owner, stage.getId(), "1000.00");
    createDeal(customer, owner, stage.getId(), "2500.00");

    PipelineSummaryResponse summary = summaryFor(owner);
    StageBreakdown row = stageRow(summary, stage.getId());

    assertThat(row.dealCount()).isEqualTo(2);
    assertThat(row.totalValue()).isEqualByComparingTo("3500.00");
  }

  @Test
  void effectiveProbability_overrideVsStageDefault() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("EP Co", "ep@test.com");
    PipelineStage stage = openStages.get(0);
    int def = stage.getDefaultProbabilityPct();

    createDeal(customer, owner, stage.getId(), "1000.00"); // uses stage default
    UUID overridden = createDeal(customer, owner, stage.getId(), "1000.00");
    setProbability(overridden, 90); // explicit override

    PipelineSummaryResponse summary = summaryFor(owner);
    StageBreakdown row = stageRow(summary, stage.getId());

    BigDecimal expected =
        new BigDecimal("1000.00")
            .multiply(BigDecimal.valueOf(def))
            .divide(BigDecimal.valueOf(100))
            .add(
                new BigDecimal("1000.00")
                    .multiply(BigDecimal.valueOf(90))
                    .divide(BigDecimal.valueOf(100)));
    assertThat(row.weightedValue()).isEqualByComparingTo(expected);
  }

  @Test
  void wonAndLost_excludedFromWeighted() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("WL Co", "wl@test.com");
    PipelineStage stage = openStages.get(0);

    // Only WON + LOST deals for this owner → no OPEN contribution at all.
    win(createDeal(customer, owner, stage.getId(), "9999.00"));
    lose(createDeal(customer, owner, stage.getId(), "8888.00"));

    PipelineSummaryResponse summary = summaryFor(owner);

    assertThat(summary.openWeightedValue()).isEqualByComparingTo(BigDecimal.ZERO);
    StageBreakdown row = stageRow(summary, stage.getId());
    assertThat(row.dealCount()).isZero();
    assertThat(row.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void winRate_overWindow_only() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("WR Co", "wr@test.com");
    PipelineStage stage = openStages.get(0);

    // 2 WON + 1 LOST, all closed "now" → inside the default trailing-90-day window.
    win(createDeal(customer, owner, stage.getId(), "1000.00"));
    win(createDeal(customer, owner, stage.getId(), "2000.00"));
    lose(createDeal(customer, owner, stage.getId(), "3000.00"));

    PipelineSummaryResponse summary = summaryFor(owner);

    // 2 / (2 + 1) = 0.6667
    assertThat(summary.winRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
  }

  @Test
  void winRate_excludesDealsClosedBeforeWindowStart() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("WB Co", "wb@test.com");
    PipelineStage stage = openStages.get(0);

    // 1 WON inside the default trailing-90-day window.
    win(createDeal(customer, owner, stage.getId(), "1000.00"));
    // 1 WON pushed OUTSIDE (older than) the 90-day window — must not raise the win count.
    UUID old = createDeal(customer, owner, stage.getId(), "2000.00");
    win(old);
    Instant longAgo = Instant.now().minus(200, ChronoUnit.DAYS);
    setTimestamps(old, longAgo.minus(10, ChronoUnit.DAYS), longAgo);

    PipelineSummaryResponse summary = summaryFor(owner);

    // Only the in-window WON counts → 1 / (1 + 0) = 1.0000, NOT inflated by the old deal.
    assertThat(summary.winRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
    // Days-to-close reflects only the in-window WON (not the 200-day-old span).
    assertThat(summary.averageDaysToClose()).isNotNull();
    assertThat(summary.averageDaysToClose()).isLessThan(190);
  }

  @Test
  void winRate_explicitTo_excludesDealsClosedAfterIt() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("WT Co", "wt@test.com");
    PipelineStage stage = openStages.get(0);

    // Both WON now. An explicit `to` of 5 days ago must exclude both (closed after `to`).
    win(createDeal(customer, owner, stage.getId(), "1000.00"));
    win(createDeal(customer, owner, stage.getId(), "2000.00"));

    LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(30);
    LocalDate to = LocalDate.now(ZoneOffset.UTC).minusDays(5);

    PipelineSummaryResponse summary = summaryFor(from, to, owner);

    // No deals fall inside [from, to] → zero closed → winRate 0, days-to-close null.
    assertThat(summary.winRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.averageDaysToClose()).isNull();
    // The DTO still reports the inclusive caller-supplied window bounds.
    assertThat(summary.windowFrom()).isEqualTo(from);
    assertThat(summary.windowTo()).isEqualTo(to);
  }

  @Test
  void averageDealSize_correct() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("AD Co", "ad@test.com");
    PipelineStage stage = openStages.get(0);

    createDeal(customer, owner, stage.getId(), "1000.00");
    createDeal(customer, owner, stage.getId(), "3000.00");

    PipelineSummaryResponse summary = summaryFor(owner);

    // (1000 + 3000) / 2 = 2000.00
    assertThat(summary.averageDealSize()).isEqualByComparingTo("2000.00");
  }

  @Test
  void averageDaysToClose_meanOverWonDeals() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("DC Co", "dc@test.com");
    PipelineStage stage = openStages.get(0);

    UUID d1 = createDeal(customer, owner, stage.getId(), "1000.00");
    UUID d2 = createDeal(customer, owner, stage.getId(), "2000.00");
    win(d1);
    win(d2);

    Instant now = Instant.now();
    // d1: 10 days to close, d2: 20 days → mean 15 days. won_at kept inside the window.
    setTimestamps(d1, now.minus(10, ChronoUnit.DAYS), now);
    setTimestamps(d2, now.minus(20, ChronoUnit.DAYS), now);

    PipelineSummaryResponse summary = summaryFor(owner);

    assertThat(summary.averageDaysToClose()).isEqualTo(15);
  }

  @Test
  void averageDaysToClose_nullWhenNoWonDeals() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID customer = createCustomer("NC Co", "nc@test.com");
    PipelineStage stage = openStages.get(0);

    createDeal(customer, owner, stage.getId(), "1000.00"); // OPEN only

    PipelineSummaryResponse summary = summaryFor(owner);

    assertThat(summary.averageDaysToClose()).isNull();
    assertThat(summary.winRate()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private static StageBreakdown stageRow(PipelineSummaryResponse summary, UUID stageId) {
    return summary.stages().stream()
        .filter(s -> s.stageId().equals(stageId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("stage not in summary: " + stageId));
  }
}
