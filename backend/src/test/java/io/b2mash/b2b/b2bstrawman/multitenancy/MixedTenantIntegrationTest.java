package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.provisioning.Tier;
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
 * Integration tests verifying that Starter-tier (shared schema) and Pro-tier (dedicated schema)
 * orgs can coexist with complete cross-tier isolation. Validates that Pro entities have null
 * tenantId while Starter entities have their Clerk org ID as tenantId.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MixedTenantIntegrationTest {

  private static final String STARTER_ORG_ID = "org_mixed_starter";
  private static final String PRO_ORG_ID = "org_mixed_pro";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private ProjectRepository projectRepository;

  private String proSchemaName;

  @BeforeAll
  void provisionMixedTenants() throws Exception {
    // Starter org — default tier maps to tenant_shared
    provisioningService.provisionTenant(STARTER_ORG_ID, "Mixed Starter Org");

    // Pro org — set tier to PRO before provisioning to get dedicated schema
    var proOrg = new Organization(PRO_ORG_ID, "Mixed Pro Org");
    proOrg.updatePlan(Tier.PRO, "pro_plan");
    organizationRepository.save(proOrg);
    var proResult = provisioningService.provisionTenant(PRO_ORG_ID, "Mixed Pro Org");
    proSchemaName = proResult.schemaName();

    // Sync members for both orgs
    syncMember(
        STARTER_ORG_ID, "user_mixed_starter", "mixed_starter@test.com", "Starter User", "admin");
    syncMember(
        STARTER_ORG_ID,
        "user_mixed_starter_owner",
        "mixed_starter_owner@test.com",
        "Starter Owner",
        "owner");
    syncMember(PRO_ORG_ID, "user_mixed_pro", "mixed_pro@test.com", "Pro User", "admin");
    syncMember(
        PRO_ORG_ID, "user_mixed_pro_owner", "mixed_pro_owner@test.com", "Pro Owner", "owner");
  }

  @Test
  void starterAndProCanBothCreateProjects() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(starterAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Starter Mixed Project", "description": "From starter tier"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Starter Mixed Project"));

    mockMvc
        .perform(
            post("/api/projects")
                .with(proAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Pro Mixed Project", "description": "From pro tier"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Pro Mixed Project"));
  }

  @Test
  void starterCannotSeeProProject() throws Exception {
    // Create project in Pro org
    var proResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(proAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Pro Only Visible", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var proProjectId = extractIdFromLocation(proResult);

    // Starter cannot GET Pro's project
    mockMvc
        .perform(get("/api/projects/" + proProjectId).with(starterAdminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void proCannotSeeStarterProject() throws Exception {
    // Create project in Starter org
    var starterResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(starterAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter Only Visible", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var starterProjectId = extractIdFromLocation(starterResult);

    // Pro cannot GET Starter's project
    mockMvc
        .perform(get("/api/projects/" + starterProjectId).with(proAdminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void proEntityHasNullTenantId() throws Exception {
    // Create project in Pro org
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(proAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Pro TenantId Check", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(result));

    // Verify tenantId is null for Pro org (dedicated schema — no row-level tagging)
    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaName)
        .where(RequestScopes.ORG_ID, PRO_ORG_ID)
        .run(
            () -> {
              var project = projectRepository.findById(projectId);
              assertThat(project).isPresent();
              assertThat(project.get().getTenantId()).isNull();
            });
  }

  @Test
  void starterEntityHasOrgIdAsTenantId() throws Exception {
    // Create project in Starter org
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(starterAdminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter TenantId Check", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(result));

    // Verify tenantId equals Clerk org ID for Starter org (shared schema — row-level tagging)
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_ID)
        .run(
            () -> {
              var project = projectRepository.findById(projectId);
              assertThat(project).isPresent();
              assertThat(project.get().getTenantId()).isEqualTo(STARTER_ORG_ID);
            });
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

    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor starterAdminJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_mixed_starter")
                    .claim("o", Map.of("id", STARTER_ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor proAdminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_mixed_pro").claim("o", Map.of("id", PRO_ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }
}
