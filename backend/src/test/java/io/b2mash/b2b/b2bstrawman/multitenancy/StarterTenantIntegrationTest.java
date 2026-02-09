package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests verifying row-level isolation for Starter-tier orgs sharing the {@code
 * tenant_shared} schema. Two Starter orgs are provisioned, and every CRUD operation is validated
 * for correct isolation via Hibernate @Filter and tenant_id population.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StarterTenantIntegrationTest {

  private static final String ORG_A_ID = "org_starter_a";
  private static final String ORG_B_ID = "org_starter_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ProjectRepository projectRepository;

  @BeforeAll
  void provisionStarterOrgs() throws Exception {
    provisioningService.provisionTenant(ORG_A_ID, "Starter Org A");
    provisioningService.provisionTenant(ORG_B_ID, "Starter Org B");

    syncMember(ORG_A_ID, "user_starter_a_admin", "starter_a_admin@test.com", "Admin A", "admin");
    syncMember(ORG_A_ID, "user_starter_a_owner", "starter_a_owner@test.com", "Owner A", "owner");
    syncMember(ORG_B_ID, "user_starter_b_admin", "starter_b_admin@test.com", "Admin B", "admin");
    syncMember(ORG_B_ID, "user_starter_b_owner", "starter_b_owner@test.com", "Owner B", "owner");
  }

  @Test
  void starterOrgCanCreateAndGetProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Project", "description": "Created by Starter org A"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Starter A Project"))
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(get("/api/projects/" + id).with(orgAAdminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Starter A Project"));
  }

  @Test
  void starterOrgCanListProjects() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(orgAAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Starter List Test", "description": null}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects").with(orgAAdminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void starterOrgProjectsAreIsolated() throws Exception {
    // Create project in Org A
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Org A Isolated", "description": "Should not be visible to B"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(createResult);

    // Org A can see it
    mockMvc
        .perform(get("/api/projects/" + projectId).with(orgAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Org A Isolated"));

    // Org B cannot GET it (404 — schema isolation + row filter)
    mockMvc
        .perform(get("/api/projects/" + projectId).with(orgBAdminJwt()))
        .andExpect(status().isNotFound());

    // Org B's project list does not include Org A's project
    mockMvc
        .perform(get("/api/projects").with(orgBAdminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Org A Isolated"))));
  }

  @Test
  void starterOrgCanCreateAndConfirmDocument() throws Exception {
    // Create a project first
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Doc Test Project A", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    // Initiate upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "starter-doc.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentId").exists())
            .andExpect(jsonPath("$.presignedUrl").exists())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm upload
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(orgAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));
  }

  @Test
  void starterOrgDocumentsAreIsolated() throws Exception {
    // Create project + document in Org A
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Doc Isolation Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "isolated-doc.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm from Org A
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(orgAOwnerJwt()))
        .andExpect(status().isOk());

    // Org B cannot see Org A's project (and thus can't list its documents)
    mockMvc
        .perform(get("/api/projects/" + projectId + "/documents").with(orgBAdminJwt()))
        .andExpect(status().isNotFound());

    // Org B cannot confirm Org A's document
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(orgBAdminJwt()))
        .andExpect(status().isNotFound());

    // Org B cannot download Org A's document
    mockMvc
        .perform(get("/api/documents/" + documentId + "/presign-download").with(orgBAdminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void tenantIdIsPopulatedForStarterOrg() throws Exception {
    // Create project via API
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TenantId Check", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(createResult));

    // Verify tenant_id is populated with the Clerk org ID via direct repository access
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, ORG_A_ID)
        .run(
            () -> {
              var project = projectRepository.findById(projectId);
              assertThat(project).isPresent();
              assertThat(project.get().getTenantId()).isEqualTo(ORG_A_ID);
            });
  }

  @Test
  void bothStarterOrgsCanCrudIndependently() throws Exception {
    // Create project in Org A
    var createA =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Independent A", "description": "Org A project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var idA = extractIdFromLocation(createA);

    // Create project in Org B
    var createB =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgBAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Independent B", "description": "Org B project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var idB = extractIdFromLocation(createB);

    // Update Org A's project
    mockMvc
        .perform(
            put("/api/projects/" + idA)
                .with(orgAAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Updated A", "description": "Updated by A"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated A"));

    // Org B's project is unaffected
    mockMvc
        .perform(get("/api/projects/" + idB).with(orgBAdminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Independent B"));

    // Delete Org A's project
    mockMvc
        .perform(delete("/api/projects/" + idA).with(orgAOwnerJwt()))
        .andExpect(status().isNoContent());

    // Org A can no longer see it
    mockMvc
        .perform(get("/api/projects/" + idA).with(orgAAdminJwt()))
        .andExpect(status().isNotFound());

    // Org B's project still exists
    mockMvc
        .perform(get("/api/projects/" + idB).with(orgBAdminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Independent B"));
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String extractJsonField(MvcResult result, String field) throws Exception {
    String body = result.getResponse().getContentAsString();
    String search = "\"" + field + "\":\"";
    int start = body.indexOf(search) + search.length();
    int end = body.indexOf("\"", start);
    return body.substring(start, end);
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

    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor orgAAdminJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_starter_a_admin")
                    .claim("o", Map.of("id", ORG_A_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor orgAOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_starter_a_owner")
                    .claim("o", Map.of("id", ORG_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor orgBAdminJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_starter_b_admin")
                    .claim("o", Map.of("id", ORG_B_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor orgBOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_starter_b_owner")
                    .claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
