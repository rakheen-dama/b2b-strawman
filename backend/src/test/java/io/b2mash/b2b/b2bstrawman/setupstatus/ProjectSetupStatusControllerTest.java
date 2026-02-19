package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectSetupStatusControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_setupstatus_test";
  private static final String ORG_ID_B = "org_setupstatus_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID projectId;

  private String tenantSchemaB;
  private UUID memberIdOwnerB;
  private UUID projectIdB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "Setup Status Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_setupstatus_owner",
                "setupstatus_owner@test.com",
                "Setup Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Setup Status Test Project", "Test", memberIdOwner);
              projectId = project.getId();
            });

    // --- Tenant B (isolation) ---
    provisioningService.provisionTenant(ORG_ID_B, "Setup Status Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(
                ORG_ID_B,
                "user_setupstatus_owner_b",
                "setupstatus_owner_b@test.com",
                "Setup Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .where(RequestScopes.MEMBER_ID, memberIdOwnerB)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject(
                      "Setup Status Test Project B", "Test B", memberIdOwnerB);
              projectIdB = project.getId();
            });
  }

  @Test
  @Order(1)
  void getSetupStatus_returns200_withExpectedShape() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/setup-status").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.customerAssigned").isBoolean())
        .andExpect(jsonPath("$.rateCardConfigured").isBoolean())
        .andExpect(jsonPath("$.budgetConfigured").isBoolean())
        .andExpect(jsonPath("$.teamAssigned").isBoolean())
        .andExpect(jsonPath("$.completionPercentage").isNumber())
        .andExpect(jsonPath("$.overallComplete").isBoolean())
        .andExpect(jsonPath("$.requiredFields.filled").isNumber())
        .andExpect(jsonPath("$.requiredFields.total").isNumber())
        .andExpect(jsonPath("$.requiredFields.fields").isArray());
  }

  @Test
  @Order(2)
  void getSetupStatus_freshProject_hasFalseChecks() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/setup-status").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerAssigned").value(false))
        .andExpect(jsonPath("$.budgetConfigured").value(false))
        .andExpect(jsonPath("$.teamAssigned").value(false))
        .andExpect(jsonPath("$.overallComplete").value(false));
  }

  @Test
  @Order(3)
  void getSetupStatus_nonExistentProject_returns404() throws Exception {
    var nonExistent = UUID.randomUUID();
    mockMvc
        .perform(get("/api/projects/" + nonExistent + "/setup-status").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(4)
  void getSetupStatus_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/setup-status"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  void getSetupStatus_crossTenant_returns404() throws Exception {
    // Tenant B tries to access Tenant A's project — should get 404 (schema isolation)
    mockMvc
        .perform(get("/api/projects/" + projectId + "/setup-status").with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_setupstatus_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_setupstatus_owner_b")
                    .claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
}
