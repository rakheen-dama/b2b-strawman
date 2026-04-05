package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test verifying that domain events carry the orgId field and that the
 * NotificationEventHandler processes events, creating actual Notification rows via fan-out logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationEventHandlerIntegrationTest {
  private static final String ORG_ID = "org_notif_handler_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ApplicationEvents events;
  @Autowired private NotificationRepository notificationRepository;

  private String tenantSchema;
  private String projectId;
  private UUID memberIdOwner;
  private UUID memberIdMember;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Notif Handler Test Org", null).schemaName();

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_nh_owner", "nh_owner@test.com", "NH Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_nh_member", "nh_member@test.com", "NH Member", "member"));

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Notif Handler Test Project", "description": "For handler tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdMember)))
        .andExpect(status().isCreated());
  }

  @Test
  void taskAssignedEvent_carriesOrgId() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId assign test", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "OrgId assign test", "priority": "HIGH", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    var assignedEvents = events.stream(TaskAssignedEvent.class).toList();
    assertThat(assignedEvents).hasSize(1);

    var event = assignedEvents.getFirst();
    assertThat(event.orgId()).isEqualTo(ORG_ID);
    assertThat(event.tenantId()).isNotNull();
  }

  @Test
  void taskAssigned_createsNotificationForAssignee() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Notif assign test", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    // Assign task to member (actor = owner)
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Notif assign test", "priority": "HIGH", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    // Verify notification created for assignee (member), not for actor (owner)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var memberNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdMember, PageRequest.of(0, 100));
              assertThat(memberNotifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getTitle().contains("assigned you to task")
                              && n.getTitle().contains("Notif assign test")
                              && n.getReferenceEntityId().equals(UUID.fromString(taskId)));

              // Actor (owner) should NOT receive a notification for their own action
              var ownerNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdOwner, PageRequest.of(0, 100));
              assertThat(ownerNotifs.getContent())
                  .noneMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getReferenceEntityId().equals(UUID.fromString(taskId)));
            });
  }

  @Test
  void taskAssigned_actorAssignsToSelf_noNotification() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Self assign test", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    // Owner assigns to self
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Self assign test", "priority": "MEDIUM", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isOk());

    // No notification for self-assignment
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var ownerNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdOwner, PageRequest.of(0, 100));
              assertThat(ownerNotifs.getContent())
                  .noneMatch(
                      n ->
                          "TASK_ASSIGNED".equals(n.getType())
                              && n.getReferenceEntityId().equals(UUID.fromString(taskId)));
            });
  }

  @Test
  void taskClaimedEvent_carriesOrgId() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId claim test", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nh_member")))
        .andExpect(status().isOk());

    var claimedEvents = events.stream(TaskClaimedEvent.class).toList();
    assertThat(claimedEvents).hasSize(1);
    assertThat(claimedEvents.getFirst().orgId()).isEqualTo(ORG_ID);
  }

  @Test
  void taskStatusChangedEvent_carriesOrgId() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId status test", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "OrgId status test", "priority": "LOW", "status": "IN_PROGRESS"}
                    """))
        .andExpect(status().isOk());

    var statusEvents = events.stream(TaskStatusChangedEvent.class).toList();
    assertThat(statusEvents).hasSize(1);
    assertThat(statusEvents.getFirst().orgId()).isEqualTo(ORG_ID);
  }

  @Test
  void taskStatusChanged_createsNotificationForAssignee() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Status notif test", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = TestEntityHelper.extractIdFromLocation(taskResult);

    // Assign to member first
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Status notif test", "priority": "HIGH", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    // Owner changes status — assignee (member) should get notification
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Status notif test", "priority": "HIGH", "status": "IN_PROGRESS", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var memberNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdMember, PageRequest.of(0, 100));
              assertThat(memberNotifs.getContent())
                  .anyMatch(
                      n ->
                          "TASK_UPDATED".equals(n.getType())
                              && n.getTitle().contains("changed task")
                              && n.getTitle().contains("IN_PROGRESS"));
            });
  }

  @Test
  void memberAddedEvent_carriesOrgId() throws Exception {
    var newMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_nh_new", "nh_new@test.com", "NH New Member", "member");

    events.clear();

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(newMemberId)))
        .andExpect(status().isCreated());

    var addedEvents = events.stream(MemberAddedToProjectEvent.class).toList();
    assertThat(addedEvents).hasSize(1);
    assertThat(addedEvents.getFirst().orgId()).isEqualTo(ORG_ID);
  }

  @Test
  void memberAdded_createsNotificationForAddedMember() throws Exception {
    var newMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_nh_added", "nh_added@test.com", "NH Added Member", "member");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_nh_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(newMemberId)))
        .andExpect(status().isCreated());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notifs =
                  notificationRepository.findByRecipientMemberId(
                      UUID.fromString(newMemberId), PageRequest.of(0, 100));
              assertThat(notifs.getContent())
                  .anyMatch(
                      n ->
                          "MEMBER_INVITED".equals(n.getType())
                              && n.getTitle().contains("You were added to project"));
            });
  }
}
