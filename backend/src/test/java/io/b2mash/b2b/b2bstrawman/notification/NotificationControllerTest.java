package io.b2mash.b2b.b2bstrawman.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationControllerTest extends AbstractIntegrationTest {
  private static final String ORG_ID = "org_notif_ctrl_test";

  @Autowired private NotificationService notificationService;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;

  /** Notification IDs created during setup, used by mutation tests. */
  private UUID ownerNotif1Id;

  private UUID ownerNotif2Id;
  private UUID ownerNotif3Id;
  private UUID memberNotifId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Notif Controller Test Org", null).schemaName();

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_nc_owner", "nc_owner@test.com", "NC Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_nc_member", "nc_member@test.com", "NC Member", "member");

    UUID ownerUuid = UUID.fromString(memberIdOwner);
    UUID memberUuid = UUID.fromString(memberIdMember);
    UUID projectId = UUID.randomUUID();

    // Seed notifications within tenant context (required for schema-per-tenant isolation)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              ownerNotif1Id =
                  notificationService
                      .createNotification(
                          ownerUuid,
                          "TASK_ASSIGNED",
                          "Alice assigned you to task \"Fix login bug\"",
                          null,
                          "TASK",
                          UUID.randomUUID(),
                          projectId)
                      .getId();

              // Small sleep to ensure distinct createdAt ordering
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }

              ownerNotif2Id =
                  notificationService
                      .createNotification(
                          ownerUuid,
                          "COMMENT_ADDED",
                          "Bob commented on task \"Fix login bug\"",
                          null,
                          "TASK",
                          UUID.randomUUID(),
                          projectId)
                      .getId();

              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }

              ownerNotif3Id =
                  notificationService
                      .createNotification(
                          ownerUuid,
                          "DOCUMENT_SHARED",
                          "Charlie uploaded \"requirements.pdf\"",
                          null,
                          "DOCUMENT",
                          UUID.randomUUID(),
                          projectId)
                      .getId();

              memberNotifId =
                  notificationService
                      .createNotification(
                          memberUuid,
                          "MEMBER_INVITED",
                          "You were added to project \"Acme\"",
                          null,
                          "PROJECT",
                          projectId,
                          projectId)
                      .getId();
            });
  }

  // --- List tests ---

  @Test
  @Order(1)
  void listNotificationsReturnsPaginatedList() throws Exception {
    mockMvc
        .perform(get("/api/notifications").with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].type").exists())
        .andExpect(jsonPath("$.content[0].title").exists())
        .andExpect(jsonPath("$.content[0].createdAt").exists());
  }

  @Test
  @Order(2)
  void listNotificationsWithUnreadOnlyFilter() throws Exception {
    // All 3 owner notifications are unread at this point
    mockMvc
        .perform(
            get("/api/notifications")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner"))
                .param("unreadOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)));
  }

  @Test
  @Order(3)
  void notificationsReturnedInDescendingCreatedAtOrder() throws Exception {
    // ownerNotif3 was created last, so it should be first
    mockMvc
        .perform(get("/api/notifications").with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(ownerNotif3Id.toString()))
        .andExpect(jsonPath("$.content[1].id").value(ownerNotif2Id.toString()))
        .andExpect(jsonPath("$.content[2].id").value(ownerNotif1Id.toString()));
  }

  // --- Unread count ---

  @Test
  @Order(4)
  void getUnreadCountReturnsCorrectCount() throws Exception {
    mockMvc
        .perform(
            get("/api/notifications/unread-count")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(3));
  }

  // --- Mark as read ---

  @Test
  @Order(5)
  void markAsReadSetsNotificationToRead() throws Exception {
    // Mark notification 1 as read
    mockMvc
        .perform(
            put("/api/notifications/" + ownerNotif1Id + "/read")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isNoContent());

    // Verify unread count decreased
    mockMvc
        .perform(
            get("/api/notifications/unread-count")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(2));

    // Verify the notification shows as read in list
    mockMvc
        .perform(
            get("/api/notifications")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner"))
                .param("unreadOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)));
  }

  @Test
  @Order(6)
  void markAsReadForOtherUserNotificationReturns404() throws Exception {
    // Owner tries to mark member's notification as read
    mockMvc
        .perform(
            put("/api/notifications/" + memberNotifId + "/read")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isNotFound());
  }

  // --- Mark all as read ---

  @Test
  @Order(7)
  void markAllAsReadSetsAllNotificationsToRead() throws Exception {
    mockMvc
        .perform(
            put("/api/notifications/read-all")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isNoContent());

    // Verify unread count is now 0
    mockMvc
        .perform(
            get("/api/notifications/unread-count")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0));
  }

  // --- Dismiss ---

  @Test
  @Order(8)
  void dismissNotificationRemovesIt() throws Exception {
    mockMvc
        .perform(
            delete("/api/notifications/" + ownerNotif2Id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isNoContent());

    // Verify total count decreased (was 3, now 2)
    mockMvc
        .perform(get("/api/notifications").with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  @Order(9)
  void dismissOtherUserNotificationReturns404() throws Exception {
    // Owner tries to dismiss member's notification
    mockMvc
        .perform(
            delete("/api/notifications/" + memberNotifId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nc_owner")))
        .andExpect(status().isNotFound());
  }

  // --- Self-scoping isolation ---

  @Test
  @Order(10)
  void memberCannotSeeOwnerNotifications() throws Exception {
    // Member should only see their own notification (1)
    mockMvc
        .perform(get("/api/notifications").with(TestJwtFactory.memberJwt(ORG_ID, "user_nc_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(memberNotifId.toString()));
  }

  @Test
  @Order(11)
  void memberCannotMarkOwnerNotificationAsRead() throws Exception {
    // Member tries to mark owner's notification as read
    mockMvc
        .perform(
            put("/api/notifications/" + ownerNotif3Id + "/read")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nc_member")))
        .andExpect(status().isNotFound());
  }
}
