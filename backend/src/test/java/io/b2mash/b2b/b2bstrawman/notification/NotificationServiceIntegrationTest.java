package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.*;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.Map;
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
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private NotificationPreferenceRepository notificationPreferenceRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private ProjectService projectService;

  private String tenantSchema;
  private UUID memberIdA;
  private UUID memberIdB;
  private UUID memberIdC;

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
    memberIdC =
        memberSyncService
            .syncMember(ORG_ID, "user_ns_c", "ns_c@test.com", "NS Member C", null, "member")
            .memberId();
  }

  // --- CRUD Tests ---

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

  // --- Fan-Out Handler Tests ---

  @Test
  void handleTaskAssigned_createsNotificationForAssignee() {
    var taskId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event =
        new TaskAssignedEvent(
            "task.assigned",
            "task",
            taskId,
            projectId,
            memberIdA,
            "NS Member A",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            memberIdB,
            "Test Task");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleTaskAssigned(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdB, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getTitle().contains("NS Member A assigned you to task")
                              && n.getTitle().contains("Test Task")
                              && n.getReferenceEntityId().equals(taskId));
            });
  }

  @Test
  void handleTaskAssigned_actorIsSameAsAssignee_noNotification() {
    var taskId = UUID.randomUUID();
    var event =
        new TaskAssignedEvent(
            "task.assigned",
            "task",
            taskId,
            UUID.randomUUID(),
            memberIdA,
            "NS Member A",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            memberIdA, // same as actor
            "Self Assign Task");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleTaskAssigned(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdA, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .noneMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getReferenceEntityId().equals(taskId));
            });
  }

  @Test
  void handleTaskStatusChanged_notifiesAssigneeWhenActorIsDifferent() {
    var taskId = UUID.randomUUID();
    var event =
        new TaskStatusChangedEvent(
            "task.status_changed",
            "task",
            taskId,
            UUID.randomUUID(),
            memberIdA,
            "NS Member A",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "OPEN",
            "IN_PROGRESS",
            memberIdB,
            "Status Changed Task");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleTaskStatusChanged(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdB, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_UPDATED".equals(n.getType())
                              && n.getTitle().contains("changed task")
                              && n.getTitle().contains("IN_PROGRESS"));
            });
  }

  @Test
  void handleTaskStatusChanged_actorIsAssignee_noNotification() {
    var taskId = UUID.randomUUID();
    var event =
        new TaskStatusChangedEvent(
            "task.status_changed",
            "task",
            taskId,
            UUID.randomUUID(),
            memberIdA,
            "NS Member A",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            "OPEN",
            "DONE",
            memberIdA, // same as actor
            "Self Status Task");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleTaskStatusChanged(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdA, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .noneMatch(
                      n ->
                          "TASK_UPDATED".equals(n.getType())
                              && n.getReferenceEntityId().equals(taskId));
            });
  }

  @Test
  void handleDocumentUploaded_notifiesAllProjectMembersExceptUploader() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a project with members A, B, C
              var project =
                  projectService.createProject("Doc Upload Notif Project", "desc", memberIdA);
              projectMemberRepository.findByProjectIdAndMemberId(project.getId(), memberIdA);
              // Add B and C to project
              var pmB =
                  new io.b2mash.b2b.b2bstrawman.member.ProjectMember(
                      project.getId(), memberIdB, "MEMBER", memberIdA);
              var pmC =
                  new io.b2mash.b2b.b2bstrawman.member.ProjectMember(
                      project.getId(), memberIdC, "MEMBER", memberIdA);
              projectMemberRepository.save(pmB);
              projectMemberRepository.save(pmC);

              var docId = UUID.randomUUID();
              var event =
                  new DocumentUploadedEvent(
                      "document.uploaded",
                      "document",
                      docId,
                      project.getId(),
                      memberIdA,
                      "NS Member A",
                      tenantSchema,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "test-file.pdf");

              notificationService.handleDocumentUploaded(event);

              // B should get notification
              var bNotifs =
                  notificationRepository.findByRecipientMemberId(memberIdB, PageRequest.of(0, 100));
              assertThat(bNotifs.getContent())
                  .anyMatch(
                      n ->
                          "DOCUMENT_SHARED".equals(n.getType())
                              && n.getTitle().contains("uploaded")
                              && n.getTitle().contains("test-file.pdf"));

              // C should get notification
              var cNotifs =
                  notificationRepository.findByRecipientMemberId(memberIdC, PageRequest.of(0, 100));
              assertThat(cNotifs.getContent())
                  .anyMatch(
                      n ->
                          "DOCUMENT_SHARED".equals(n.getType())
                              && n.getReferenceEntityId().equals(docId));

              // A (uploader) should NOT get notification for this doc
              var aNotifs =
                  notificationRepository.findByRecipientMemberId(memberIdA, PageRequest.of(0, 100));
              assertThat(aNotifs.getContent())
                  .noneMatch(
                      n ->
                          "DOCUMENT_SHARED".equals(n.getType())
                              && n.getReferenceEntityId().equals(docId));
            });
  }

  @Test
  void handleMemberAddedToProject_notifiesAddedMember() {
    var projectId = UUID.randomUUID();
    var event =
        new MemberAddedToProjectEvent(
            "project_member.added",
            "project_member",
            UUID.randomUUID(),
            projectId,
            memberIdA,
            "NS Member A",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            memberIdB,
            "Test Project");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleMemberAddedToProject(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdB, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "MEMBER_INVITED".equals(n.getType())
                              && n.getTitle().contains("You were added to project")
                              && n.getTitle().contains("Test Project"));
            });
  }

  @Test
  void handleTaskClaimed_notifiesPreviousAssignee() {
    var taskId = UUID.randomUUID();
    var event =
        new TaskClaimedEvent(
            "task.claimed",
            "task",
            taskId,
            UUID.randomUUID(),
            memberIdB,
            "NS Member B",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            memberIdA, // previous assignee
            "Claimed Task");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationService.handleTaskClaimed(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdA, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_CLAIMED".equals(n.getType())
                              && n.getTitle().contains("NS Member B claimed task")
                              && n.getTitle().contains("Claimed Task"));
            });
  }

  // --- Preference Opt-Out Tests ---

  @Test
  void handleTaskAssigned_prefDisabled_noNotification() {
    var taskId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Disable TASK_ASSIGNED notifications for memberIdC
              var pref = new NotificationPreference(memberIdC, "TASK_ASSIGNED", false, true);
              notificationPreferenceRepository.save(pref);

              var event =
                  new TaskAssignedEvent(
                      "task.assigned",
                      "task",
                      taskId,
                      UUID.randomUUID(),
                      memberIdA,
                      "NS Member A",
                      tenantSchema,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      memberIdC,
                      "Pref Test Task");

              notificationService.handleTaskAssigned(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdC, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .noneMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getReferenceEntityId().equals(taskId));
            });
  }

  @Test
  void handleTaskAssigned_noPreferenceRow_defaultEnabled() {
    var taskId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // No preference row for memberIdB + TASK_ASSIGNED => default enabled
              var event =
                  new TaskAssignedEvent(
                      "task.assigned",
                      "task",
                      taskId,
                      UUID.randomUUID(),
                      memberIdA,
                      "NS Member A",
                      tenantSchema,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      memberIdB,
                      "Default Pref Task");

              notificationService.handleTaskAssigned(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdB, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getReferenceEntityId().equals(taskId));
            });
  }

  @Test
  void handleMemberAdded_prefDisabled_noNotification() {
    var projectId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Disable MEMBER_INVITED notifications for memberIdC
              var existingPref =
                  notificationPreferenceRepository.findByMemberIdAndNotificationType(
                      memberIdC, "MEMBER_INVITED");
              if (existingPref.isEmpty()) {
                var pref = new NotificationPreference(memberIdC, "MEMBER_INVITED", false, true);
                notificationPreferenceRepository.save(pref);
              }

              var event =
                  new MemberAddedToProjectEvent(
                      "project_member.added",
                      "project_member",
                      UUID.randomUUID(),
                      projectId,
                      memberIdA,
                      "NS Member A",
                      tenantSchema,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      memberIdC,
                      "Pref Test Project");

              notificationService.handleMemberAddedToProject(event);

              var notifs =
                  notificationRepository.findByRecipientMemberId(memberIdC, PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .noneMatch(
                      n ->
                          "MEMBER_INVITED".equals(n.getType())
                              && n.getReferenceProjectId().equals(projectId));
            });
  }
}
