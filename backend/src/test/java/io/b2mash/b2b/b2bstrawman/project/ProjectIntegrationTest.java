package io.b2mash.b2b.b2bstrawman.project;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_project_test";
  private static final String ORG_B_ID = "org_project_test_b";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Test Org");
    provisioningService.provisionTenant(ORG_B_ID, "Project Test Org B");

    syncMember(ORG_ID, "user_owner", "proj_owner@test.com", "Owner", "owner");
    syncMember(ORG_ID, "user_admin", "proj_admin@test.com", "Admin", "admin");
    syncMember(ORG_ID, "user_member", "proj_member@test.com", "Member", "member");
    syncMember(ORG_B_ID, "user_tenant_b", "proj_tenantb@test.com", "Tenant B User", "member");
  }

  // --- CRUD happy path ---

  @Test
  void shouldCreateAndGetProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Test Project", "description": "A test project"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Project"))
            .andExpect(jsonPath("$.description").value("A test project"))
            .andExpect(jsonPath("$.createdBy").value(matchesPattern(UUID_PATTERN)))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andExpect(jsonPath("$.projectRole").value("lead"))
            .andReturn();

    var id = extractIdFromLocation(createResult);

    // Owner can always get any project
    mockMvc
        .perform(get("/api/projects/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Project"))
        .andExpect(jsonPath("$.description").value("A test project"));
  }

  @Test
  void shouldListProjects() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "List Test 1", "description": null}
                    """))
        .andExpect(status().isCreated());

    // Admin sees all projects
    mockMvc
        .perform(get("/api/projects").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void shouldUpdateProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Before Update", "description": "Original desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "After Update", "description": "Updated desc"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("After Update"))
        .andExpect(jsonPath("$.description").value("Updated desc"));
  }

  @Test
  void shouldDeleteProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "To Delete", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/projects/" + id).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/projects/" + id).with(ownerJwt())).andExpect(status().isNotFound());
  }

  // --- Validation errors ---

  @Test
  void shouldReject400WhenNameIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "", "description": "desc"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenNameIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "desc"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenNameTooLong() throws Exception {
    String longName = "a".repeat(256);
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "%s", "description": "desc"}
                    """
                        .formatted(longName)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenDescriptionTooLong() throws Exception {
    String longDesc = "a".repeat(2001);
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Valid\", \"description\": \"" + longDesc + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // --- Not found ---

  @Test
  void shouldReturn404ForNonexistentProject() throws Exception {
    mockMvc
        .perform(get("/api/projects/00000000-0000-0000-0000-000000000000").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenUpdatingNonexistentProject() throws Exception {
    mockMvc
        .perform(
            put("/api/projects/00000000-0000-0000-0000-000000000000")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Ghost", "description": "nope"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenDeletingNonexistentProject() throws Exception {
    mockMvc
        .perform(delete("/api/projects/00000000-0000-0000-0000-000000000000").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- RBAC ---

  @Test
  void memberCanCreateProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Member Created", "description": null}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  @Test
  void memberCannotDeleteProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "RBAC Delete Test", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/projects/" + id).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateButCannotDelete() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Admin Test", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/projects/" + id).with(adminJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanUpdateProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Admin Update", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Admin Updated", "description": "updated"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void unauthenticatedUserCannotAccessProjects() throws Exception {
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  // --- Tenant isolation ---

  @Test
  void projectsAreIsolatedBetweenTenants() throws Exception {
    // Create project in tenant A
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant A Isolation", "description": "Belongs to A"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(createResult);

    // Verify visible from tenant A (owner can see all)
    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant A Isolation"));

    // Verify NOT visible from tenant B
    mockMvc
        .perform(get("/api/projects/" + projectId).with(tenantBMemberJwt()))
        .andExpect(status().isNotFound());

    // Verify tenant B list doesn't include tenant A's project
    mockMvc
        .perform(get("/api/projects").with(tenantBMemberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Tenant A Isolation"))));
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

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor tenantBMemberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
