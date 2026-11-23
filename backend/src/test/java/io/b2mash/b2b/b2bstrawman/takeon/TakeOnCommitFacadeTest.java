package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCommitRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnProblems.TakeOnProblemException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Phase 93 655A (§17.3.3 step 1, FD-35): the facade refuses before any claim when the queue is off,
 * claims before it enqueues, enqueues with a {@code null} payload, and still answers when the
 * enqueue fails (the claim is committed; the sweep recovers it).
 */
class TakeOnCommitFacadeTest {

  private static final UUID BATCH = UUID.randomUUID();
  private static final TakeOnCommitRequest REQUEST = new TakeOnCommitRequest("a".repeat(64));

  private final JobQueueProperties properties = new JobQueueProperties();
  private final TakeOnCommitService commitService = mock(TakeOnCommitService.class);
  private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
  private final TakeOnCommitFacade facade =
      new TakeOnCommitFacade(properties, commitService, enqueuer);

  @Test
  void queueOff_refusesBeforeAnyClaim() {
    properties.setEnabled(false);
    assertThatThrownBy(() -> facade.commit(BATCH, REQUEST))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e -> assertThat(e.getCode()).isEqualTo("TAKE_ON_QUEUE_DISABLED"));
    verifyNoInteractions(commitService, enqueuer);
  }

  @Test
  void queueOn_claimsThenEnqueuesTheTenantDrainWithNoPayload() {
    properties.setEnabled(true);
    var response = mock(TakeOnBatchResponse.class);
    when(commitService.requestCommit(BATCH, REQUEST)).thenReturn(response);
    var result =
        RequestScopes.callForTenant(
            "tenant_0123456789ab", "org_x", () -> facade.commit(BATCH, REQUEST));
    assertThat(result).isSameAs(response);
    var order = inOrder(commitService, enqueuer);
    order.verify(commitService).requestCommit(BATCH, REQUEST);
    order
        .verify(enqueuer)
        .enqueue(eq("take_on_drain"), eq("tenant_0123456789ab"), eq("org_x"), any(), isNull());
  }

  @Test
  void aFailedEnqueue_isLoggedAndTheClaimStillAnswers() {
    properties.setEnabled(true);
    var response = mock(TakeOnBatchResponse.class);
    when(commitService.requestCommit(BATCH, REQUEST)).thenReturn(response);
    doThrow(new IllegalStateException("queue down"))
        .when(enqueuer)
        .enqueue(any(), any(), any(), any(), any());
    var result =
        RequestScopes.callForTenant(
            "tenant_0123456789ab", "org_x", () -> facade.commit(BATCH, REQUEST));
    assertThat(result).isSameAs(response);
  }
}
