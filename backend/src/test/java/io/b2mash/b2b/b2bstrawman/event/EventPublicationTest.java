package io.b2mash.b2b.b2bstrawman.event;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventPublicationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_event_pub_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ApplicationEvents events;

  private String projectId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Event Pub Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_ep_owner", "ep_owner@test.com", "EP Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_ep_member", "ep_member@test.com", "EP Member", "member");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Event Pub Test Project", "description": "For event publication tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdMember)))
        .andExpect(status().isCreated());
  }

  @Test
  void updateTask_assigneeChange_publishesTaskAssignedEvent() throws Exception {
    // Create a task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Task for assign event", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    // Clear events accumulated from setup/create
    events.clear();

    // Update task with assignee change
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Task for assign event", "priority": "HIGH", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    var assignedEvents = events.stream(TaskAssignedEvent.class).toList();
    assertThat(assignedEvents).hasSize(1);

    var event = assignedEvents.getFirst();
    assertThat(event.eventType()).isEqualTo("task.assigned");
    assertThat(event.entityType()).isEqualTo("task");
    assertThat(event.entityId()).isEqualTo(UUID.fromString(taskId));
    assertThat(event.projectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.actorMemberId()).isEqualTo(UUID.fromString(memberIdOwner));
    assertThat(event.actorName()).isEqualTo("EP Owner");
    assertThat(event.tenantId()).isNotNull();
    assertThat(event.occurredAt()).isNotNull();
    assertThat(event.assigneeMemberId()).isEqualTo(UUID.fromString(memberIdMember));
    assertThat(event.taskTitle()).isEqualTo("Task for assign event");
  }

  @Test
  void claimTask_publishesTaskClaimedEvent() throws Exception {
    // Create an unassigned, OPEN task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Task for claim event", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    // Clear events
    events.clear();

    // Claim the task as member
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    var claimedEvents = events.stream(TaskClaimedEvent.class).toList();
    assertThat(claimedEvents).hasSize(1);

    var event = claimedEvents.getFirst();
    assertThat(event.eventType()).isEqualTo("task.claimed");
    assertThat(event.entityType()).isEqualTo("task");
    assertThat(event.entityId()).isEqualTo(UUID.fromString(taskId));
    assertThat(event.projectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.actorMemberId()).isEqualTo(UUID.fromString(memberIdMember));
    assertThat(event.actorName()).isEqualTo("EP Member");
    assertThat(event.tenantId()).isNotNull();
    assertThat(event.previousAssigneeId()).isNull();
    assertThat(event.taskTitle()).isEqualTo("Task for claim event");
  }

  @Test
  void updateTask_statusChange_publishesTaskStatusChangedEvent() throws Exception {
    // Create a task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Task for status event", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    // Assign the member first (so we can verify assigneeMemberId in the status event)
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Task for status event", "priority": "LOW", "status": "OPEN", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    // Clear events from create + assign
    events.clear();

    // Update task status from OPEN to IN_PROGRESS
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Task for status event", "priority": "LOW", "status": "IN_PROGRESS", "assigneeId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isOk());

    var statusEvents = events.stream(TaskStatusChangedEvent.class).toList();
    assertThat(statusEvents).hasSize(1);

    var event = statusEvents.getFirst();
    assertThat(event.eventType()).isEqualTo("task.status_changed");
    assertThat(event.entityType()).isEqualTo("task");
    assertThat(event.entityId()).isEqualTo(UUID.fromString(taskId));
    assertThat(event.projectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.actorMemberId()).isEqualTo(UUID.fromString(memberIdOwner));
    assertThat(event.oldStatus()).isEqualTo("OPEN");
    assertThat(event.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(event.assigneeMemberId()).isEqualTo(UUID.fromString(memberIdMember));
    assertThat(event.taskTitle()).isEqualTo("Task for status event");
  }

  @Test
  void confirmUpload_publishesDocumentUploadedEvent() throws Exception {
    // Initiate a PROJECT-scoped document upload
    var docInitResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "event-test.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var documentId = extractJsonField(docInitResult, "documentId");

    // Clear events
    events.clear();

    // Confirm the upload
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    var uploadedEvents = events.stream(DocumentUploadedEvent.class).toList();
    assertThat(uploadedEvents).hasSize(1);

    var event = uploadedEvents.getFirst();
    assertThat(event.eventType()).isEqualTo("document.uploaded");
    assertThat(event.entityType()).isEqualTo("document");
    assertThat(event.entityId()).isEqualTo(UUID.fromString(documentId));
    assertThat(event.projectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.actorMemberId()).isEqualTo(UUID.fromString(memberIdOwner));
    assertThat(event.actorName()).isEqualTo("EP Owner");
    assertThat(event.tenantId()).isNotNull();
    assertThat(event.documentName()).isEqualTo("event-test.pdf");
  }

  @Test
  void addMember_publishesMemberAddedToProjectEvent() throws Exception {
    // Sync a new member to add
    var newMemberId =
        syncMember(ORG_ID, "user_ep_new", "ep_new@test.com", "EP New Member", "member");

    // Clear events
    events.clear();

    // Add the new member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(newMemberId)))
        .andExpect(status().isCreated());

    var addedEvents = events.stream(MemberAddedToProjectEvent.class).toList();
    assertThat(addedEvents).hasSize(1);

    var event = addedEvents.getFirst();
    assertThat(event.eventType()).isEqualTo("project_member.added");
    assertThat(event.entityType()).isEqualTo("project_member");
    assertThat(event.entityId()).isNotNull();
    assertThat(event.projectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.actorMemberId()).isEqualTo(UUID.fromString(memberIdOwner));
    assertThat(event.actorName()).isEqualTo("EP Owner");
    assertThat(event.tenantId()).isNotNull();
    assertThat(event.addedMemberId()).isEqualTo(UUID.fromString(newMemberId));
    assertThat(event.projectName()).isEqualTo("Event Pub Test Project");
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String extractJsonField(MvcResult result, String field) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$." + field).toString();
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ep_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
