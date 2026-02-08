package io.b2mash.b2b.b2bstrawman.member;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
class ProjectMemberIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_pm_test";
  private static final String ORG_B_ID = "org_pm_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;
  private String extraMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "PM Test Org");
    provisioningService.provisionTenant(ORG_B_ID, "PM Test Org B");

    ownerMemberId = syncMember(ORG_ID, "user_pm_owner", "owner@test.com", "Owner", "owner");
    adminMemberId = syncMember(ORG_ID, "user_pm_admin", "admin@test.com", "Admin", "admin");
    memberMemberId = syncMember(ORG_ID, "user_pm_member", "member@test.com", "Member", "member");
    extraMemberId = syncMember(ORG_ID, "user_pm_extra", "extra@test.com", "Extra", "member");

    syncMember(ORG_B_ID, "user_pm_tenant_b", "tenantb@test.com", "Tenant B User", "member");
  }

  // --- Creator becomes lead ---

  @Test
  void creatorBecomesLeadOnProjectCreation() throws Exception {
    var projectId = createProject(ownerJwt(), "Lead Test Project");

    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].memberId").value(ownerMemberId))
        .andExpect(jsonPath("$[0].projectRole").value("lead"))
        .andExpect(jsonPath("$[0].name").value("Owner"))
        .andExpect(jsonPath("$[0].email").value("owner@test.com"));
  }

  // --- Add member ---

  @Test
  void shouldAddMemberToProject() throws Exception {
    var projectId = createProject(adminJwt(), "Add Member Test");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void shouldReturn409WhenAddingDuplicateMember() throws Exception {
    var projectId = createProject(adminJwt(), "Duplicate Test");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturn404WhenAddingNonexistentMember() throws Exception {
    var projectId = createProject(adminJwt(), "Nonexistent Member Test");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"00000000-0000-0000-0000-000000000000\"}"))
        .andExpect(status().isNotFound());
  }

  // --- Remove member ---

  @Test
  void shouldRemoveMemberFromProject() throws Exception {
    var projectId = createProject(adminJwt(), "Remove Member Test");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/members/" + memberMemberId).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void shouldReturn400WhenRemovingLead() throws Exception {
    var projectId = createProject(adminJwt(), "Remove Lead Test");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/members/" + adminMemberId).with(adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404WhenRemovingNonMember() throws Exception {
    var projectId = createProject(adminJwt(), "Remove Non-Member Test");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/members/" + extraMemberId).with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Lead transfer ---

  @Test
  void shouldTransferLead() throws Exception {
    var projectId = createProject(adminJwt(), "Transfer Lead Test");

    // Add another member
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    // Transfer lead from admin to member
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/members/" + memberMemberId + "/role")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    // Verify roles swapped
    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].projectRole".formatted(adminMemberId))
                .value("member"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].projectRole".formatted(memberMemberId))
                .value("lead"));
  }

  @Test
  void shouldReturn404WhenTransferTargetNotOnProject() throws Exception {
    var projectId = createProject(adminJwt(), "Transfer Not On Project Test");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/members/" + memberMemberId + "/role")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNotFound());
  }

  // --- GET /api/members (org members) ---

  @Test
  void shouldListOrgMembers() throws Exception {
    mockMvc
        .perform(get("/api/members").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].email").exists())
        .andExpect(jsonPath("$[0].orgRole").exists());
  }

  @Test
  void shouldReturn401ForUnauthenticatedMembersRequest() throws Exception {
    mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
  }

  // --- RBAC ---

  @Test
  void memberCanListProjectMembers() throws Exception {
    var projectId = createProject(ownerJwt(), "RBAC List Test");

    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(memberJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void memberCanAddMemberToProject() throws Exception {
    var projectId = createProject(ownerJwt(), "RBAC Add Test");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(extraMemberId)))
        .andExpect(status().isCreated());
  }

  @Test
  void unauthenticatedCannotAccessProjectMembers() throws Exception {
    mockMvc
        .perform(get("/api/projects/00000000-0000-0000-0000-000000000000/members"))
        .andExpect(status().isUnauthorized());
  }

  // --- Tenant isolation ---

  @Test
  void projectMembersAreIsolatedBetweenTenants() throws Exception {
    var projectId = createProject(ownerJwt(), "Tenant Isolation PM");

    // Tenant B cannot see tenant A's project members
    mockMvc
        .perform(get("/api/projects/" + projectId + "/members").with(tenantBMemberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void orgMembersAreIsolatedBetweenTenants() throws Exception {
    // Tenant B should see only tenant B's members
    mockMvc
        .perform(get("/api/members").with(tenantBMemberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].email").value("tenantb@test.com"));
  }

  // --- Helpers ---

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

  private String createProject(JwtRequestPostProcessor jwt, String name) throws Exception {
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pm_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pm_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pm_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor tenantBMemberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pm_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
