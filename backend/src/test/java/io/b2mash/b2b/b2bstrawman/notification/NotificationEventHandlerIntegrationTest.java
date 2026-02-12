package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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

/**
 * Integration test verifying that domain events carry the orgId field and that the
 * NotificationEventHandler processes events without error (stubs log only in 61B; real fan-out in
 * 61C).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationEventHandlerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_notif_handler_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ApplicationEvents events;

  private String projectId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Notif Handler Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_nh_owner", "nh_owner@test.com", "NH Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_nh_member", "nh_member@test.com", "NH Member", "member");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Notif Handler Test Project", "description": "For handler tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId assign test", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
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
  void taskClaimedEvent_carriesOrgId() throws Exception {
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId claim test", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "OrgId status test", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskId = extractIdFromLocation(taskResult);

    events.clear();

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
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
  void memberAddedEvent_carriesOrgId() throws Exception {
    var newMemberId =
        syncMember(ORG_ID, "user_nh_new", "nh_new@test.com", "NH New Member", "member");

    events.clear();

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(newMemberId)))
        .andExpect(status().isCreated());

    var addedEvents = events.stream(MemberAddedToProjectEvent.class).toList();
    assertThat(addedEvents).hasSize(1);
    assertThat(addedEvents.getFirst().orgId()).isEqualTo(ORG_ID);
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
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
        .jwt(j -> j.subject("user_nh_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_nh_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
