package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the correctness premise behind {@code DomainEvent.shardId()} (PR #1389): an {@code
 * AFTER_COMMIT} listener reads {@link RequestScopes#SHARD_ID} <em>lazily</em> at handling time, so
 * this only routes to the right shard if the binding from the publishing scope is still visible
 * when {@code afterCommit} fires. Spring implements
 * {@code @TransactionalEventListener(AFTER_COMMIT)} via {@link
 * TransactionSynchronization#afterCommit()} on the publishing thread, so this test reproduces that
 * exact callback timing.
 *
 * <p>Runs with sharding disabled (default): {@code SHARD_ID} is bound as a ScopedValue regardless
 * of {@code kazi.sharding.enabled} (only {@code TenantIdentifierResolver} gates on the flag), so
 * the binding is observable here.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventListenerShardScopeTest {

  private static final String TENANT = "tenant_aaa0000000ab";
  private static final String ORG = "org_evt_shard";

  private final PlatformTransactionManager transactionManager;

  @Autowired
  EventListenerShardScopeTest(PlatformTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  private String shardSeenInAfterCommit(Runnable scopeWrappedTx) {
    scopeWrappedTx.run();
    return capturedShard.get();
  }

  private final AtomicReference<String> capturedShard = new AtomicReference<>();

  private void txRegisteringAfterCommitShardCapture() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx ->
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        capturedShard.set(RequestScopes.getShardIdOrDefault());
                      }
                    }));
  }

  @Test
  void afterCommit_seesShardIdFromEnclosingRunForTenantOnShard() {
    capturedShard.set(null);
    String seen =
        shardSeenInAfterCommit(
            () ->
                RequestScopes.runForTenantOnShard(
                    TENANT, ORG, "kazi_legal_1", this::txRegisteringAfterCommitShardCapture));
    assertThat(seen)
        .as("AFTER_COMMIT listener must see the shard bound by the publishing scope")
        .isEqualTo("kazi_legal_1");
  }

  @Test
  void afterCommit_inheritsShardThroughNestedRunForTenant() {
    capturedShard.set(null);
    // A nested 2-arg runForTenant (no shard rebind) must let the outer shard fall through —
    // this is the "protected by ScopedValue inheritance" path for the still-grandfathered callers.
    String seen =
        shardSeenInAfterCommit(
            () ->
                RequestScopes.runForTenantOnShard(
                    TENANT,
                    ORG,
                    "kazi_legal_1",
                    () ->
                        RequestScopes.runForTenant(
                            TENANT, ORG, this::txRegisteringAfterCommitShardCapture)));
    assertThat(seen)
        .as("nested runForTenant must inherit the outer shard, not reset to primary")
        .isEqualTo("kazi_legal_1");
  }

  @Test
  void afterCommit_defaultsToPrimaryWithoutAShardScope() {
    capturedShard.set(null);
    // No enclosing shard scope (e.g. a primary-only / non-tenant publish) → primary, as documented.
    String seen =
        shardSeenInAfterCommit(
            () ->
                RequestScopes.runForTenant(
                    TENANT, ORG, this::txRegisteringAfterCommitShardCapture));
    assertThat(seen).isEqualTo("primary");
  }
}
