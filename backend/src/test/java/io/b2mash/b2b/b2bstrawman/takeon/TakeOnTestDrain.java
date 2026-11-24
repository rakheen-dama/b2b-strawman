package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCommitRequest;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Commits a take-on batch in integration tests without the job queue (Phase 93 655A, C-4; used by
 * 656A, 657B, 657C, 658A). The test profile switches the queue off, so {@code POST …/commit} is
 * refused 409 {@code TAKE_ON_QUEUE_DISABLED} by the facade (one MockMvc test asserts that); every
 * real commit in a test is the claim, {@link TakeOnCommitService#requestCommit}, in a member scope
 * — bypassing only the facade's queue check — then the drain, {@link TakeOnDrainJobHandler}, run
 * exactly as the job worker runs it (tenant and org bound, no member, no capabilities).
 *
 * <p>Never flip {@code JobQueueProperties.setEnabled(true)} at runtime (it writes {@code job_queue}
 * rows nothing drains, which dedup-block later enqueues) and never add {@code @TestPropertySource}
 * (a new context key). Binding {@code ScopedValue}s directly is fine in tests ({@code
 * TenantScopeBindingTest} imports main classes only).
 */
public final class TakeOnTestDrain {

  /**
   * What a member who may commit a clients or matters batch holds (owner/admin hold all of these;
   * 656A adds {@code PROJECT_MANAGEMENT}, the matters dataset capability).
   */
  public static final Set<String> COMMIT_CAPABILITIES =
      Set.of("MANAGE_TAKE_ON", "CUSTOMER_MANAGEMENT", "MANAGE_COMPLIANCE", "PROJECT_MANAGEMENT");

  private TakeOnTestDrain() {}

  /** The claim as {@code memberId} (batch {@code VALIDATED → COMMITTING}). */
  public static TakeOnBatchResponse commit(
      TakeOnCommitService commitService,
      String schema,
      String orgId,
      UUID memberId,
      UUID batchId,
      String validationHash) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, COMMIT_CAPABILITIES)
        .call(() -> commitService.requestCommit(batchId, new TakeOnCommitRequest(validationHash)));
  }

  /** The drain as the job worker runs it, with the default 10-minute budget. */
  public static void run(TakeOnDrainJobHandler handler, String schema, String orgId) {
    RequestScopes.runForTenant(schema, orgId, () -> handler.execute(null));
  }

  /** The drain with an injected budget ({@code Duration.ZERO} commits exactly one row). */
  public static void run(
      TakeOnDrainJobHandler handler, String schema, String orgId, Duration budget) {
    RequestScopes.runForTenant(schema, orgId, () -> handler.drainFor(budget));
  }

  /** Claim then drain: the commit a wizard user's click leads to. */
  public static void commitAndDrain(
      TakeOnCommitService commitService,
      TakeOnDrainJobHandler handler,
      String schema,
      String orgId,
      UUID memberId,
      UUID batchId,
      String validationHash) {
    commit(commitService, schema, orgId, memberId, batchId, validationHash);
    run(handler, schema, orgId);
  }
}
