package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.crm.event.DealLostEvent;
import io.b2mash.b2b.b2bstrawman.crm.event.DealStageChangedEvent;
import io.b2mash.b2b.b2bstrawman.crm.event.DealWonEvent;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Service-level test (Phase 80, slice 575A) for {@link DealTransitionService} — the guarded deal
 * lifecycle path. Each test body runs the domain work inside tenant scope (binding TENANT_ID /
 * ORG_ID / MEMBER_ID via {@link ScopedValue}); the {@code @Transactional} transition commits when
 * the service call returns, so published events (recorded by {@link RecordApplicationEvents}),
 * persisted entity state, and audit rows are all observable afterward.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class DealTransitionServiceTest {

  private static final String ORG_ID = "org_deal_transition_test";
  private static final String OWNER_SUBJECT = "user_deal_transition_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DealTransitionService dealTransitionService;
  @Autowired private DealService dealService;
  @Autowired private DealRepository dealRepository;
  @Autowired private PipelineStageService pipelineStageService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditService auditService;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deal Transition Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUBJECT, "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // --- helpers ---------------------------------------------------------------

  private <T> T inTenant(java.util.concurrent.Callable<T> body) {
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

  /** Creates a PROSPECT customer via the API and returns its id. */
  private UUID createCustomer(String name, String email) throws Exception {
    return UUID.fromString(TestEntityHelper.createCustomer(mockMvc, owner(), name, email));
  }

  /** Creates an OPEN deal against an existing customer and returns its id. */
  private UUID createDeal(UUID customerId, String title) {
    return inTenant(
        () -> {
          DealResponse r =
              dealService.createDeal(
                  customerId,
                  title,
                  null,
                  new BigDecimal("1000.00"),
                  memberId,
                  "WEBSITE",
                  null,
                  memberId);
          return r.id();
        });
  }

  /**
   * Resolves an OPEN target stage for an OPEN→OPEN move — prefers a second OPEN stage so the move
   * is observable, falling back to the deal's current (OPEN) stage when the seeded pipeline only
   * has one (a move-to-same-OPEN-stage is still a valid OPEN→OPEN transition).
   */
  private UUID openTargetStageFor(UUID dealId) {
    return inTenant(
        () ->
            pipelineStageService.listStages().stream()
                .filter(s -> s.getStageType() == StageType.OPEN && !s.isArchived())
                .map(s -> s.getId())
                .reduce((first, second) -> second)
                .orElseGet(() -> dealRepository.findOneById(dealId).getStageId()));
  }

  // --- tests -----------------------------------------------------------------

  @Test
  void openToOpenMove_recomputesEffectiveProbability_andEmitsStageChangedEvent() throws Exception {
    var customerId = createCustomer("Move Co", "move@test.com");
    var dealId = createDeal(customerId, "Move Deal");
    UUID targetStage = openTargetStageFor(dealId);

    DealResponse resp =
        inTenant(
            () ->
                dealTransitionService.transition(
                    dealId, new TransitionRequest(targetStage, 65, null)));

    assertThat(resp.status()).isEqualTo(DealStatus.OPEN);
    assertThat(resp.probabilityPct()).isEqualTo(65);
    assertThat(resp.effectiveProbabilityPct()).isEqualTo(65);
    assertThat(
            events.stream(DealStageChangedEvent.class)
                .filter(e -> e.dealId().equals(dealId))
                .count())
        .isEqualTo(1);
  }

  @Test
  void win_setsWonStatus_wonAtNonNull_andEffectiveProbability100() throws Exception {
    var customerId = createCustomer("Win Co", "win@test.com");
    var dealId = createDeal(customerId, "Win Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

    DealResponse resp =
        inTenant(
            () ->
                dealTransitionService.transition(
                    dealId, new TransitionRequest(wonStage, null, null)));

    assertThat(resp.status()).isEqualTo(DealStatus.WON);
    assertThat(resp.wonAt()).isNotNull();
    assertThat(resp.effectiveProbabilityPct()).isEqualTo(100);
    assertThat(events.stream(DealWonEvent.class).filter(e -> e.dealId().equals(dealId)).count())
        .isEqualTo(1);
  }

  @Test
  void win_nudgesProspectCustomerToOnboarding() throws Exception {
    var customerId = createCustomer("Prospect Co", "prospect@test.com");
    var dealId = createDeal(customerId, "Prospect Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

    // sanity: customer starts as PROSPECT
    assertThat(
            inTenant(
                () -> customerRepository.findById(customerId).orElseThrow().getLifecycleStatus()))
        .isEqualTo(LifecycleStatus.PROSPECT);

    inTenant(
        () ->
            dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

    assertThat(
            inTenant(
                () -> customerRepository.findById(customerId).orElseThrow().getLifecycleStatus()))
        .isEqualTo(LifecycleStatus.ONBOARDING);
  }

  @Test
  void win_doesNotDowngradeActiveCustomer() throws Exception {
    var customerId = createCustomer("Active Co", "active@test.com");
    // advance the customer PROSPECT -> ONBOARDING -> ACTIVE inside tenant scope
    inTenant(
        () -> {
          Customer c = customerRepository.findById(customerId).orElseThrow();
          c.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, memberId);
          c.transitionLifecycleStatus(LifecycleStatus.ACTIVE, memberId);
          customerRepository.save(c);
          return null;
        });
    var dealId = createDeal(customerId, "Active Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

    inTenant(
        () ->
            dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

    assertThat(
            inTenant(
                () -> customerRepository.findById(customerId).orElseThrow().getLifecycleStatus()))
        .isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void doubleWin_isRejected() throws Exception {
    var customerId = createCustomer("Double Co", "double@test.com");
    var dealId = createDeal(customerId, "Double Win Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

    inTenant(
        () ->
            dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

    assertThatThrownBy(
            () ->
                inTenant(
                    () ->
                        dealTransitionService.transition(
                            dealId, new TransitionRequest(wonStage, null, null))))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void loseWithoutReason_isRejected() throws Exception {
    var customerId = createCustomer("NoReason Co", "noreason@test.com");
    var dealId = createDeal(customerId, "No Reason Deal");
    UUID lostStage = inTenant(() -> pipelineStageService.firstLostStage().getId());

    assertThatThrownBy(
            () ->
                inTenant(
                    () ->
                        dealTransitionService.transition(
                            dealId, new TransitionRequest(lostStage, null, "  "))))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void lose_setsLostStatus_lostAtNonNull_andReasonPersisted() throws Exception {
    var customerId = createCustomer("Lose Co", "lose@test.com");
    var dealId = createDeal(customerId, "Lose Deal");
    UUID lostStage = inTenant(() -> pipelineStageService.firstLostStage().getId());

    DealResponse resp =
        inTenant(
            () ->
                dealTransitionService.transition(
                    dealId, new TransitionRequest(lostStage, null, "Budget cut")));

    assertThat(resp.status()).isEqualTo(DealStatus.LOST);
    assertThat(resp.lostAt()).isNotNull();
    assertThat(resp.lostReason()).isEqualTo("Budget cut");
    assertThat(resp.effectiveProbabilityPct()).isZero();
    assertThat(events.stream(DealLostEvent.class).filter(e -> e.dealId().equals(dealId)).count())
        .isEqualTo(1);
  }

  @Test
  void reopenFromWon_clearsTerminalFields_andStatusBackToOpen() throws Exception {
    var customerId = createCustomer("Reopen Co", "reopen@test.com");
    var dealId = createDeal(customerId, "Reopen Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());
    UUID openStage = inTenant(() -> pipelineStageService.firstOpenStage().getId());

    inTenant(
        () ->
            dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

    DealResponse resp =
        inTenant(
            () ->
                dealTransitionService.transition(
                    dealId, new TransitionRequest(openStage, null, null)));

    assertThat(resp.status()).isEqualTo(DealStatus.OPEN);
    assertThat(resp.wonAt()).isNull();
    assertThat(resp.lostAt()).isNull();
    assertThat(resp.lostReason()).isNull();
  }

  @Test
  void auditRowsWritten_withUppercaseDealEntityType() throws Exception {
    var customerId = createCustomer("Audit Co", "audit@test.com");
    var dealId = createDeal(customerId, "Audit Deal");
    UUID wonStage = inTenant(() -> pipelineStageService.firstWonStage().getId());

    inTenant(
        () ->
            dealTransitionService.transition(dealId, new TransitionRequest(wonStage, null, null)));

    long dealAuditRows =
        inTenant(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter("DEAL", dealId, null, null, null, null, null),
                        Pageable.ofSize(50))
                    .getTotalElements());

    assertThat(dealAuditRows).isGreaterThanOrEqualTo(1);
  }
}
