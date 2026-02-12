package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditEventControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_audit_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String projectId;
  private String taskId;
  private String project2Id;
  private String project3Id;
  private Instant beforeCreation;
  private Instant afterCreation;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Audit Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_ac_owner", "ac_owner@test.com", "AC Owner", "owner");
    memberIdAdmin = syncMember(ORG_ID, "user_ac_admin", "ac_admin@test.com", "AC Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_ac_member", "ac_member@test.com", "AC Member", "member");

    beforeCreation = Instant.now();

    // Create a project (produces project.created audit event)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project", "description": "For audit controller tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add member to project so we can create tasks
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    // Create a task (produces task.created audit event)
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Audit Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);

    // Create additional projects for pagination tests
    var project2Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project 2", "description": "Second project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    project2Id = extractIdFromLocation(project2Result);

    var project3Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project 3", "description": "Third project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    project3Id = extractIdFromLocation(project3Result);

    afterCreation = Instant.now();
  }

  @Test
  void ownerCanListAuditEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
  }

  @Test
  void adminCanListAuditEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
  }

  @Test
  void regularMemberDenied() throws Exception {
    mockMvc.perform(get("/api/audit-events").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  void filterByEntityTypeReturnsOnlyMatchingEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()).param("entityType", "project"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(
            jsonPath("$.content[*].entityType", everyItem(org.hamcrest.Matchers.is("project"))));
  }

  @Test
  void filterByEntityIdReturnsEventsForSpecificEntity() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(ownerJwt())
                .param("entityType", "task")
                .param("entityId", taskId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[0].entityId").value(taskId));
  }

  @Test
  void filterByEventTypePrefixMatchesMultipleTypes() throws Exception {
    // "project." should match project.created (and any other project.* events)
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()).param("eventType", "project."))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[*].eventType", everyItem(startsWith("project."))));
  }

  @Test
  void filterByTimeRange() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(ownerJwt())
                .param("from", beforeCreation.toString())
                .param("to", afterCreation.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

    // Events before our test setup should return empty (use a time far in the past)
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(ownerJwt())
                .param("from", "2020-01-01T00:00:00Z")
                .param("to", "2020-01-02T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void paginationWorks() throws Exception {
    // We created at least 3 projects + 1 task = 4+ audit events
    // Request page 0 with size 2
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()).param("size", "2").param("page", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)));

    // Request page 1
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()).param("size", "2").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.number").value(1));
  }

  @Test
  void entitySpecificEndpointReturnsCorrectEvents() throws Exception {
    mockMvc
        .perform(get("/api/audit-events/task/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[0].entityType").value("task"))
        .andExpect(jsonPath("$.content[0].entityId").value(taskId));
  }

  @Test
  void responseExcludesPiiFields() throws Exception {
    mockMvc
        .perform(get("/api/audit-events").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0]").exists())
        .andExpect(jsonPath("$.content[0].ipAddress").doesNotExist())
        .andExpect(jsonPath("$.content[0].userAgent").doesNotExist())
        .andExpect(jsonPath("$.content[0].tenantId").doesNotExist())
        // Verify expected fields ARE present
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].eventType").exists())
        .andExpect(jsonPath("$.content[0].entityType").exists())
        .andExpect(jsonPath("$.content[0].entityId").exists())
        .andExpect(jsonPath("$.content[0].occurredAt").exists());
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

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ac_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ac_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
