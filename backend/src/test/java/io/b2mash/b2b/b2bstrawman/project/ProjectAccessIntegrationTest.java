package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectAccessIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_access_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String ownerMemberId;
  private String adminMemberId;
  private String leadMemberId;
  private String regularMemberId;
  private String nonMemberMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Access Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ownerMemberId =
        syncMember(ORG_ID, "user_access_owner", "access_owner@test.com", "Owner", "owner");
    adminMemberId =
        syncMember(ORG_ID, "user_access_admin", "access_admin@test.com", "Admin", "admin");
    leadMemberId = syncMember(ORG_ID, "user_access_lead", "access_lead@test.com", "Lead", "member");
    regularMemberId =
        syncMember(ORG_ID, "user_access_regular", "access_regular@test.com", "Regular", "member");
    nonMemberMemberId =
        syncMember(
            ORG_ID, "user_access_nonmember", "access_nonmember@test.com", "NonMember", "member");
  }

  // --- Filtered listing ---

  @Test
  void ownerSeesAllProjects() throws Exception {
    // Lead creates a project (only lead is on it)
    createProjectAs(leadJwt(), "Owner Sees All");

    mockMvc
        .perform(get("/api/projects").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Owner Sees All')]").exists());
  }

  @Test
  void adminSeesAllProjects() throws Exception {
    createProjectAs(leadJwt(), "Admin Sees All");

    mockMvc
        .perform(get("/api/projects").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Admin Sees All')]").exists());
  }

  @Test
  void memberSeesOnlyTheirProjects() throws Exception {
    // Lead creates a project — nonMember is NOT added
    createProjectAs(leadJwt(), "Not For NonMember");

    mockMvc
        .perform(get("/api/projects").with(nonMemberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Not For NonMember')]").doesNotExist());
  }

  @Test
  void memberSeesProjectsTheyAreOn() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Member Is On This");

    // Add regular member to this project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(leadJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects").with(regularJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Member Is On This')]").exists());
  }

  @Test
  void listingIncludesProjectRole() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Role In Listing");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(leadJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    // Lead sees their role
    mockMvc
        .perform(get("/api/projects").with(leadJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Role In Listing')].projectRole").value("lead"));

    // Regular member sees their role
    mockMvc
        .perform(get("/api/projects").with(regularJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Role In Listing')].projectRole").value("member"));
  }

  // --- Single project access ---

  @Test
  void nonMemberGets404OnGetProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "No Access For NonMember");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(nonMemberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerCanGetAnyProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Owner Can Get");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Can Get"));
  }

  @Test
  void adminCanGetAnyProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Admin Can Get");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Can Get"));
  }

  @Test
  void projectMemberCanGetProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Regular Can Get");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(leadJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects/" + projectId).with(regularJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Regular Can Get"))
        .andExpect(jsonPath("$.projectRole").value("member"));
  }

  @Test
  void getProjectIncludesProjectRole() throws Exception {
    var projectId = createProjectAs(ownerJwt(), "Get With Role");

    // Owner is not on the project member list (unless they created it — they did as owner)
    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  @Test
  void adminNotOnProjectSeesNullRole() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Admin Not Member");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Not Member"))
        .andExpect(jsonPath("$.projectRole").isEmpty());
  }

  @Test
  void ownerNotOnProjectSeesNullRole() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Owner Not Member");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Not Member"))
        .andExpect(jsonPath("$.projectRole").isEmpty());
  }

  // --- Create project (all members can) ---

  @Test
  void memberCanCreateProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(regularJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Member Created", "description": null}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Member Created"))
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  @Test
  void creatorBecomesLeadAndCanGetTheirProject() throws Exception {
    var projectId = createProjectAs(regularJwt(), "Creator Is Lead");

    mockMvc
        .perform(get("/api/projects/" + projectId).with(regularJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  // --- Update project ---

  @Test
  void leadCanUpdateProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Lead Can Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(leadJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Lead Updated", "description": "updated by lead"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lead Updated"));
  }

  @Test
  void adminCanUpdateAnyProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Admin Can Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Admin Updated", "description": "updated by admin"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Updated"));
  }

  @Test
  void regularMemberCannotUpdateProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "Regular Cannot Update");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(leadJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(regularJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Should Fail", "description": null}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void nonMemberGets404OnUpdateProject() throws Exception {
    var projectId = createProjectAs(leadJwt(), "NonMember Cannot Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(nonMemberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Should Not See", "description": null}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private String createProjectAs(JwtRequestPostProcessor jwt, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"%s\", \"description\": null}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();

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
        .jwt(j -> j.subject("user_access_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_access_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor leadJwt() {
    return jwt()
        .jwt(j -> j.subject("user_access_lead").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor regularJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_access_regular").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor nonMemberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_access_nonmember")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
