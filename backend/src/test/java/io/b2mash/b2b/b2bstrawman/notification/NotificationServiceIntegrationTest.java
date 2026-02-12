package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.*;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationServiceIntegrationTest {

  private static final String ORG_ID = "org_notif_svc_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private NotificationService notificationService;

  private String tenantSchema;
  private UUID memberIdA;
  private UUID memberIdB;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Notification Svc Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdA =
        memberSyncService
            .syncMember(ORG_ID, "user_ns_a", "ns_a@test.com", "NS Member A", null, "owner")
            .memberId();
    memberIdB =
        memberSyncService
            .syncMember(ORG_ID, "user_ns_b", "ns_b@test.com", "NS Member B", null, "member")
            .memberId();
  }

  @Test
  void createNotification_persistsAndIsQueryable() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  notificationService.createNotification(
                      memberIdA,
                      "TASK_ASSIGNED",
                      "You were assigned a task",
                      "Details here",
                      "task",
                      UUID.randomUUID(),
                      UUID.randomUUID());

              assertThat(notification.getId()).isNotNull();
              assertThat(notification.getRecipientMemberId()).isEqualTo(memberIdA);
              assertThat(notification.getType()).isEqualTo("TASK_ASSIGNED");
              assertThat(notification.getTitle()).isEqualTo("You were assigned a task");
              assertThat(notification.getBody()).isEqualTo("Details here");
              assertThat(notification.isRead()).isFalse();
              assertThat(notification.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void listNotifications_returnsNotificationsForRecipient() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.createNotification(
                  memberIdA, "TASK_ASSIGNED", "List 1", null, "task", UUID.randomUUID(), null);
              notificationService.createNotification(
                  memberIdA, "COMMENT_ADDED", "List 2", null, "task", UUID.randomUUID(), null);

              var page =
                  notificationService.listNotifications(memberIdA, false, PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
            });
  }

  @Test
  void getUnreadCount_returnsCorrectCount() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.createNotification(
                  memberIdB, "TASK_ASSIGNED", "Unread 1", null, "task", UUID.randomUUID(), null);
              notificationService.createNotification(
                  memberIdB, "TASK_ASSIGNED", "Unread 2", null, "task", UUID.randomUUID(), null);

              long count = notificationService.getUnreadCount(memberIdB);
              assertThat(count).isGreaterThanOrEqualTo(2);
            });
  }

  @Test
  void markAsRead_setsIsReadTrue() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  notificationService.createNotification(
                      memberIdA,
                      "TASK_ASSIGNED",
                      "To be read",
                      null,
                      "task",
                      UUID.randomUUID(),
                      null);

              notificationService.markAsRead(notification.getId(), memberIdA);

              var unreadPage =
                  notificationService.listNotifications(memberIdA, true, PageRequest.of(0, 10));
              assertThat(unreadPage.getContent())
                  .noneMatch(n -> n.getId().equals(notification.getId()));
            });
  }

  @Test
  void markAsRead_wrongRecipient_throwsNotFound() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  notificationService.createNotification(
                      memberIdA,
                      "TASK_ASSIGNED",
                      "Wrong recipient test",
                      null,
                      "task",
                      UUID.randomUUID(),
                      null);

              assertThatThrownBy(
                      () -> notificationService.markAsRead(notification.getId(), memberIdB))
                  .isInstanceOf(ResourceNotFoundException.class);
            });
  }

  @Test
  void markAllAsRead_marksAllUnreadForMember() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Use memberIdB to avoid interference from other tests using memberIdA
              notificationService.createNotification(
                  memberIdB, "TASK_ASSIGNED", "Bulk read 1", null, "task", UUID.randomUUID(), null);
              notificationService.createNotification(
                  memberIdB, "COMMENT_ADDED", "Bulk read 2", null, "task", UUID.randomUUID(), null);

              notificationService.markAllAsRead(memberIdB);

              long unreadCount = notificationService.getUnreadCount(memberIdB);
              assertThat(unreadCount).isZero();
            });
  }

  @Test
  void dismissNotification_deletesNotification() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  notificationService.createNotification(
                      memberIdA,
                      "TASK_ASSIGNED",
                      "To dismiss",
                      null,
                      "task",
                      UUID.randomUUID(),
                      null);

              notificationService.dismissNotification(notification.getId(), memberIdA);

              // Verify notification no longer appears in full list
              var page =
                  notificationService.listNotifications(memberIdA, false, PageRequest.of(0, 100));
              assertThat(page.getContent()).noneMatch(n -> n.getId().equals(notification.getId()));
            });
  }

  @Test
  void dismissNotification_wrongRecipient_throwsNotFound() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  notificationService.createNotification(
                      memberIdA,
                      "TASK_ASSIGNED",
                      "Wrong dismiss",
                      null,
                      "task",
                      UUID.randomUUID(),
                      null);

              assertThatThrownBy(
                      () ->
                          notificationService.dismissNotification(notification.getId(), memberIdB))
                  .isInstanceOf(ResourceNotFoundException.class);
            });
  }
}
