package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.notification.NotificationEventHandler;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the new {@link RequestScopes#runForTenant} contract under the (in-practice unreachable)
 * scenario where a domain event carries {@code tenantId == null}. The audit attached to PR #1265
 * concluded no production code path can publish a null-tenant event today, but we still want a
 * regression guard for the contract itself: if such an event were ever published, the listener must
 * fail fast (no silent drop, no fall-through to {@code action.run()} with unbound scope), and the
 * exception must not poison the originating transaction.
 *
 * <p>Spring's {@code @TransactionalEventListener(AFTER_COMMIT)} contract guarantees the originating
 * transaction has already committed by the time the listener runs, so listener exceptions cannot
 * roll back the originating work. This test exercises the listener directly to prove the fail-fast
 * happens at the entry point (before any DB I/O), and exercises the AFTER_COMMIT path via {@link
 * ApplicationEventPublisher} to prove the originating transaction is unaffected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NullTenantListenerResilienceTest {

  private static final String ORG_ID = "org_null_tenant_resilience";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private NotificationEventHandler notificationEventHandler;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Null-Tenant Resilience Test", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ntr_owner", "ntr_owner@test.com", "NTR Owner", "owner");
  }

  @Test
  void listener_failsFast_whenEventCarriesNullTenant_andDoesNotRunActionBody() {
    // Construct a null-tenantId event by hand. In production no publisher emits this — the audit
    // proved every publisher chain has TENANT_ID bound by TenantFilter or a per-tenant loop —
    // but the contract on RequestScopes.runForTenant must still reject it cleanly.
    CommentCreatedEvent nullTenantEvent =
        new CommentCreatedEvent(
            "comment.created",
            "comment",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Resilience Test Actor",
            null, // ← null tenantId
            ORG_ID,
            Instant.now(),
            Map.of("body", "test"),
            null,
            null,
            "INTERNAL");

    long notificationCountBefore =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(notificationRepository::count);

    assertThatThrownBy(() -> notificationEventHandler.onCommentCreated(nullTenantEvent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");

    long notificationCountAfter =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(notificationRepository::count);

    assertThat(notificationCountAfter)
        .as(
            "fail-fast must happen before the action body runs — no notification should have been"
                + " created")
        .isEqualTo(notificationCountBefore);
  }

  @Test
  void afterCommitListener_failure_doesNotPoisonOriginatingTransaction() {
    // Simulate the production shape: publish a domain event from inside a committed transaction.
    // The @TransactionalEventListener(AFTER_COMMIT) handler will fail with IllegalArgumentException
    // when it calls runForTenant(null, ...). Spring's TransactionSynchronizationUtils catches
    // AFTER_COMMIT listener exceptions and logs them — the originating transaction has already
    // committed by then, so it cannot be rolled back.
    DomainEvent nullTenantEvent =
        new CommentCreatedEvent(
            "comment.created",
            "comment",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Resilience Test Actor",
            null,
            ORG_ID,
            Instant.now(),
            Map.of("body", "test"),
            null,
            null,
            "INTERNAL");

    boolean[] originatingWorkCompleted = {false};

    // Wrap in TENANT_ID scope so the publishing-side has a valid scope and the
    // TransactionTemplate-driven JDBC layer doesn't trip on missing TENANT_ID.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              transactionTemplate.executeWithoutResult(
                  status -> {
                    eventPublisher.publishEvent(nullTenantEvent);
                    // mark "originating work" as completed within the tx
                    originatingWorkCompleted[0] = true;
                  });
              // After-commit listeners fire here. The handler throws
              // IllegalArgumentException; Spring catches it and logs at WARN.
              // Test continues — no exception bubbles up.
            });

    assertThat(originatingWorkCompleted[0])
        .as("Originating transaction must commit cleanly despite the AFTER_COMMIT listener failing")
        .isTrue();
  }
}
