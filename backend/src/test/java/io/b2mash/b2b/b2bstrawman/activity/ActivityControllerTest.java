package io.b2mash.b2b.b2bstrawman.activity;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for {@link ActivityController}. Tests the activity feed endpoint with MockMvc
 * against a real database via Testcontainers. Seeds audit events with project_id in details JSONB
 * to verify the findByProjectId native query.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_activity_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private AuditService auditService;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;
  private UUID ownerUuid;
  private UUID memberUuid;
  private String projectId;
  private UUID projectUuid;
  private Instant seedTime;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Activity Ctrl Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_ac_owner", "ac_owner@test.com", "AC Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_ac_member", "ac_member@test.com", "AC Member", "member");

    ownerUuid = UUID.fromString(memberIdOwner);
    memberUuid = UUID.fromString(memberIdMember);

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Activity Ctrl Test Project", "description": "For activity ctrl tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);
    projectUuid = UUID.fromString(projectId);

    // Add member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberIdMember)))
        .andExpect(status().isCreated());

    // Record the seed time before creating audit events
    seedTime = Instant.now();

    // Seed audit events with project_id in details
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerUuid)
        .run(
            () -> {
              UUID taskId = UUID.randomUUID();

              // Task created event
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      taskId,
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Test Task", "project_id", projectUuid.toString())));

              sleep(50);

              // Task claimed event
              auditService.log(
                  new AuditEventRecord(
                      "task.claimed",
                      "task",
                      taskId,
                      memberUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Test Task", "project_id", projectUuid.toString())));

              sleep(50);

              // Document uploaded event
              auditService.log(
                  new AuditEventRecord(
                      "document.uploaded",
                      "document",
                      UUID.randomUUID(),
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of(
                          "file_name", "requirements.pdf", "project_id", projectUuid.toString())));

              sleep(50);

              // Comment created event
              auditService.log(
                  new AuditEventRecord(
                      "comment.created",
                      "comment",
                      UUID.randomUUID(),
                      memberUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("entity_type", "task", "project_id", projectUuid.toString())));
            });
  }

  @Test
  void getActivityReturnsPaginatedItemsWithCorrectShape() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/activity").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].message").exists())
        .andExpect(jsonPath("$.content[0].actorName").exists())
        .andExpect(jsonPath("$.content[0].entityType").exists())
        .andExpect(jsonPath("$.content[0].entityId").exists())
        .andExpect(jsonPath("$.content[0].eventType").exists())
        .andExpect(jsonPath("$.content[0].occurredAt").exists());
  }

  @Test
  void filterByEntityTypeReturnsOnlyMatchingEvents() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("entityType", "TASK")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].entityType").value("task"))
        .andExpect(jsonPath("$.content[1].entityType").value("task"));
  }

  @Test
  void filterBySinceReturnsOnlyEventsAfterTimestamp() throws Exception {
    // Use a future timestamp to ensure no events match
    String futureTimestamp = Instant.now().plus(1, ChronoUnit.HOURS).toString();

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("since", futureTimestamp)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void emptyProjectReturnsEmptyList() throws Exception {
    // Create a second project with no audit events
    var emptyProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Empty Activity Project", "description": "No audit events"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String emptyProjectId = extractIdFromLocation(emptyProjectResult);

    mockMvc
        .perform(get("/api/projects/" + emptyProjectId + "/activity").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void nonMemberReturns404() throws Exception {
    // Create a different org with a different member who is NOT on this project
    String otherOrgId = "org_activity_other";
    provisioningService.provisionTenant(otherOrgId, "Other Org");
    planSyncService.syncPlan(otherOrgId, "pro-plan");

    String otherMemberId =
        syncMember(otherOrgId, "user_ac_other", "ac_other@test.com", "AC Other", "member");

    // This member is in a different org, so they should get a 404
    var otherJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_ac_other")
                        .claim("o", Map.of("id", otherOrgId, "rol", "member")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));

    mockMvc
        .perform(get("/api/projects/" + projectId + "/activity").with(otherJwt))
        .andExpect(status().isNotFound());
  }

  @Test
  void responseFormatContainsActorNameAndMessage() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/activity")
                .param("entityType", "DOCUMENT")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].actorName").value("AC Owner"))
        .andExpect(
            jsonPath("$.content[0].message")
                .value("AC Owner uploaded document \"requirements.pdf\""))
        .andExpect(jsonPath("$.content[0].entityType").value("document"))
        .andExpect(jsonPath("$.content[0].eventType").value("document.uploaded"));
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
        .jwt(j -> j.subject("user_ac_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ac_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
