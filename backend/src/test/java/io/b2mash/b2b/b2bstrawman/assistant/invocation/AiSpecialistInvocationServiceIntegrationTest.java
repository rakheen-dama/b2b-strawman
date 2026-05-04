package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplier;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for {@link AiSpecialistInvocationService}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  AiSpecialistInvocationServiceIntegrationTest.FakeApplierConfig.class
})
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiSpecialistInvocationServiceIntegrationTest {

  private static final String ORG_ID = "org_inv_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationService service;
  @MockitoSpyBean private AiSpecialistInvocationRepository repository;
  @Autowired private FakeBillingPolishApplier fakeApplier;
  @Autowired private ApplicationEvents events;
  @Autowired private JdbcTemplate jdbc;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Inv Svc Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inv_owner", "inv_owner@test.com", "Inv Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inv_member", "inv_member@test.com", "Inv Member", "member");
    memberMemberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // ----- helpers -----

  private void runAsOwner(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_ASSISTANT_USE", "TEAM_OVERSIGHT"))
        .run(body);
  }

  private void runAsMember(Set<String> caps, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberMemberId)
        .where(RequestScopes.ORG_ROLE, "member")
        .where(RequestScopes.CAPABILITIES, caps)
        .run(body);
  }

  private UUID seedPendingInvocation(UUID actorId) {
    UUID[] holder = new UUID[1];
    runAsOwner(
        () -> {
          var inv =
              service.recordRunning(
                  "billing-za",
                  InvocationSource.MEMBER,
                  actorId,
                  null,
                  "invoice",
                  UUID.randomUUID(),
                  "v1");
          service.recordProposal(inv.getId(), new BillingPolishPayload(null, java.util.List.of()));
          service.markPendingApproval(inv.getId());
          holder[0] = inv.getId();
        });
    return holder[0];
  }

  // ----- tests -----

  @Test
  void approveHappyPath_runsApplier_publishesEvent_emitsAudit() {
    fakeApplier.reset();
    UUID id = seedPendingInvocation(ownerMemberId);

    runAsOwner(
        () -> {
          var result = service.approve(id, null);
          assertThat(result.status()).isEqualTo(InvocationStatus.APPROVED);
          assertThat(result.appliedAt()).isNotNull();
          var stored = repository.findById(id).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(InvocationStatus.APPROVED);
          assertThat(stored.getAppliedOutput()).isInstanceOf(BillingPolishPayload.class);
          assertThat(stored.getReviewedById()).isEqualTo(ownerMemberId);
          assertThat(stored.getVersion()).isGreaterThan(0);
        });

    assertThat(fakeApplier.applyCount()).isEqualTo(1);
    assertThat(events.stream(AiInvocationApprovedEvent.class).count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void rejectRecordsReason_publishesRejectedEvent() {
    UUID id = seedPendingInvocation(ownerMemberId);
    runAsOwner(
        () -> {
          service.reject(id, "Output looks wrong");
          var stored = repository.findById(id).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(InvocationStatus.REJECTED);
          assertThat(stored.getRejectReason()).isEqualTo("Output looks wrong");
        });
    assertThat(events.stream(AiInvocationRejectedEvent.class).count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void approveAfterExternalRejection_failsRequireStatusGuard() {
    // Renamed: this test only proves that the requireStatus(...) guard fires when the row was
    // externally transitioned to REJECTED. It does NOT exercise the optimistic-lock path; that
    // is covered by serviceTranslatesOptimisticLockToResourceConflict() below.
    fakeApplier.reset();
    UUID id = seedPendingInvocation(ownerMemberId);

    runAsOwner(
        () -> {
          var current = repository.findById(id).orElseThrow();
          current.markRejected(ownerMemberId, "external rejection");
          repository.save(current);
          assertThatThrownBy(() -> service.approve(id, null))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void crossActorApproveWithoutOversight_throws403() {
    UUID id = seedPendingInvocation(ownerMemberId);
    // Member is not the actor (owner is) → needs TEAM_OVERSIGHT, member doesn't have it.
    runAsMember(
        Set.of("AI_ASSISTANT_USE"),
        () ->
            assertThatThrownBy(() -> service.approve(id, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
  }

  @Test
  void bulkApproveCapExceeded_throws400() {
    runAsOwner(
        () -> {
          List<UUID> tooMany =
              java.util.stream.IntStream.range(0, 26).mapToObj(i -> UUID.randomUUID()).toList();
          assertThatThrownBy(() -> service.bulkApprove(tooMany))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Bulk-approve cap exceeded");
        });
  }

  @Test
  void bulkApproveMixedSpecialist_throws400() {
    UUID[] ids = new UUID[2];
    runAsOwner(
        () -> {
          var a =
              service.recordRunning(
                  "billing-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "invoice",
                  UUID.randomUUID(),
                  "v1");
          service.recordProposal(a.getId(), new BillingPolishPayload(null, java.util.List.of()));
          service.markPendingApproval(a.getId());

          var b =
              service.recordRunning(
                  "intake-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "customer",
                  UUID.randomUUID(),
                  "v1");
          service.recordProposal(b.getId(), new BillingPolishPayload(null, java.util.List.of()));
          service.markPendingApproval(b.getId());

          ids[0] = a.getId();
          ids[1] = b.getId();
        });

    runAsOwner(
        () ->
            assertThatThrownBy(() -> service.bulkApprove(List.of(ids[0], ids[1])))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Mixed specialist"));
  }

  @Test
  void bulkApproveMixedStatus_perIdErrorInResult() {
    fakeApplier.reset();
    UUID[] ids = new UUID[2];
    runAsOwner(
        () -> {
          // Pending one
          var a =
              service.recordRunning(
                  "billing-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "invoice",
                  UUID.randomUUID(),
                  "v1");
          service.recordProposal(a.getId(), new BillingPolishPayload(null, java.util.List.of()));
          service.markPendingApproval(a.getId());

          // RUNNING (not pending) — bulkApprove should record per-id error for it.
          var b =
              service.recordRunning(
                  "billing-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "invoice",
                  UUID.randomUUID(),
                  "v1");
          ids[0] = a.getId();
          ids[1] = b.getId();
        });

    runAsOwner(
        () -> {
          var result = service.bulkApprove(List.of(ids[0], ids[1]));
          assertThat(result.outcomes()).hasSize(2);
          // First outcome: the pending one approved; second: status mismatch → error.
          var aOutcome =
              result.outcomes().stream()
                  .filter(o -> o.id().equals(ids[0]))
                  .findFirst()
                  .orElseThrow();
          var bOutcome =
              result.outcomes().stream()
                  .filter(o -> o.id().equals(ids[1]))
                  .findFirst()
                  .orElseThrow();
          assertThat(aOutcome.status()).isEqualTo(InvocationStatus.APPROVED);
          assertThat(aOutcome.error()).isNull();
          assertThat(bOutcome.error()).isNotNull();

          // Brief contract: a failure on one id MUST NOT roll back successful siblings. Verify
          // the valid approval was actually committed to the database (not just reported in
          // outcomes).
          var aStored = repository.findById(ids[0]).orElseThrow();
          assertThat(aStored.getStatus()).isEqualTo(InvocationStatus.APPROVED);
          var bStored = repository.findById(ids[1]).orElseThrow();
          assertThat(bStored.getStatus()).isEqualTo(InvocationStatus.RUNNING);
        });
  }

  @Test
  void approveWithoutApplierForUnregisteredPayload_throws400() {
    UUID[] holder = new UUID[1];
    runAsOwner(
        () -> {
          var inv =
              service.recordRunning(
                  "intake-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "customer",
                  UUID.randomUUID(),
                  "v1");
          // IntakeExtractionPayload has no applier registered in this test — service must fail.
          service.recordProposal(
              inv.getId(),
              new io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.IntakeExtractionPayload());
          service.markPendingApproval(inv.getId());
          holder[0] = inv.getId();
        });

    runAsOwner(
        () ->
            assertThatThrownBy(() -> service.approve(holder[0], null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("No applier registered"));
  }

  @Test
  void findByIdCrossActorWithoutOversight_throws403() {
    // Brief §515A.6: cross-actor read without TEAM_OVERSIGHT must yield 403, not 404.
    UUID id = seedPendingInvocation(ownerMemberId);
    runAsMember(
        Set.of("AI_ASSISTANT_USE"),
        () ->
            assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ForbiddenException.class));
  }

  @Test
  void findByFilterWithCrossActorIdWithoutOversight_throws403() {
    runAsMember(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var filter =
              new AiSpecialistInvocationService.InvocationFilter(
                  null, null, null, null, null, null, ownerMemberId);
          assertThatThrownBy(
                  () ->
                      service.findByFilter(
                          filter, org.springframework.data.domain.PageRequest.of(0, 10)))
              .isInstanceOf(ForbiddenException.class);
        });
  }

  @Test
  void rejectWithBlankReason_throws400() {
    UUID id = seedPendingInvocation(ownerMemberId);
    runAsOwner(
        () ->
            assertThatThrownBy(() -> service.reject(id, "  "))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("rejectReason"));
  }

  @Test
  void retryFromFailed_resetsToRunning() {
    UUID[] holder = new UUID[1];
    runAsOwner(
        () -> {
          var inv =
              service.recordRunning(
                  "billing-za",
                  InvocationSource.MEMBER,
                  ownerMemberId,
                  null,
                  "invoice",
                  UUID.randomUUID(),
                  "v1");
          inv.markFailed("Boom");
          repository.save(inv);
          holder[0] = inv.getId();
        });

    runAsOwner(
        () -> {
          service.retry(holder[0]);
          var stored = repository.findById(holder[0]).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(InvocationStatus.RUNNING);
          assertThat(stored.getErrorMessage()).isNull();
        });
  }

  @Test
  void approveOptimisticLock_concurrentWritesProduceConflict() {
    fakeApplier.reset();
    UUID id = seedPendingInvocation(ownerMemberId);

    runAsOwner(
        () -> {
          // Load entity twice as separate detached copies — the second save() must lose.
          var first = repository.findById(id).orElseThrow();
          var second = repository.findById(id).orElseThrow();

          first.markApproved(ownerMemberId, new BillingPolishPayload(null, java.util.List.of()));
          repository.saveAndFlush(first);

          second.markApproved(ownerMemberId, new BillingPolishPayload(null, java.util.List.of()));
          assertThatThrownBy(() -> repository.saveAndFlush(second))
              .satisfiesAnyOf(
                  ex ->
                      assertThat(ex)
                          .isInstanceOf(
                              org.springframework.orm.ObjectOptimisticLockingFailureException
                                  .class),
                  ex ->
                      assertThat(ex)
                          .isInstanceOf(jakarta.persistence.OptimisticLockException.class));
        });
  }

  @Test
  void serviceTranslatesOptimisticLockToResourceConflict() {
    // True service-level optimistic-lock test: stub the repository spy to throw
    // ObjectOptimisticLockingFailureException on saveAndFlush. The service must translate it
    // to ResourceConflictException (→ 409 wire mapping).
    fakeApplier.reset();
    UUID id = seedPendingInvocation(ownerMemberId);

    runAsOwner(
        () -> {
          org.mockito.Mockito.doThrow(
                  new org.springframework.orm.ObjectOptimisticLockingFailureException(
                      AiSpecialistInvocation.class, id))
              .when(repository)
              .saveAndFlush(org.mockito.ArgumentMatchers.any(AiSpecialistInvocation.class));
          try {
            assertThatThrownBy(() -> service.approve(id, null))
                .isInstanceOf(ResourceConflictException.class);
          } finally {
            org.mockito.Mockito.reset(repository);
          }
        });
  }

  // ----- fake applier wiring -----

  @TestConfiguration
  static class FakeApplierConfig {
    /**
     * Overrides the production {@code BillingPolishApplier} bean (registered by 512A's
     * {@code @Component} scan) so these tests can count apply() calls without driving the full
     * TimeEntryService pipeline. Bean override is enabled in {@code application-test.yml}.
     */
    @Bean(name = "billingPolishApplier")
    FakeBillingPolishApplier fakeBillingPolishApplier() {
      return new FakeBillingPolishApplier();
    }
  }

  static class FakeBillingPolishApplier implements OutputApplier<BillingPolishPayload> {
    private final AtomicInteger applyCount = new AtomicInteger();

    void reset() {
      applyCount.set(0);
    }

    int applyCount() {
      return applyCount.get();
    }

    @Override
    public Class<BillingPolishPayload> payloadType() {
      return BillingPolishPayload.class;
    }

    @Override
    public void apply(BillingPolishPayload payload, UUID actorId) {
      applyCount.incrementAndGet();
    }
  }
}
