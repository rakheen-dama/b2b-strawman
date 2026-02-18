package io.b2mash.b2b.b2bstrawman.customer;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for customer-project linking endpoints. Covers Tasks 37.11 (linking tests) and
 * 37.12 (shared schema isolation tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerProjectIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_custproj_test";
  private static final String ORG_B_ID = "org_custproj_test_b";

  // Additional orgs for cross-tenant isolation tests (37.12)
  private static final String STARTER_A_ID = "org_custproj_starter_a";
  private static final String STARTER_B_ID = "org_custproj_starter_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String projectLeadMemberId;

  @BeforeAll
  void provisionTenants() throws Exception {
    // Pro-tier orgs for main linking tests
    provisioningService.provisionTenant(ORG_ID, "CustProj Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    provisioningService.provisionTenant(ORG_B_ID, "CustProj Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    syncMember(ORG_ID, "user_cp_owner", "cp_owner@test.com", "Owner", "owner");
    syncMember(ORG_ID, "user_cp_admin", "cp_admin@test.com", "Admin", "admin");
    projectLeadMemberId =
        syncMember(ORG_ID, "user_cp_member", "cp_member@test.com", "Member", "member");
    syncMember(ORG_B_ID, "user_cp_tenant_b", "cp_tenantb@test.com", "Tenant B User", "owner");

    // Create a project for linking tests — owner creates it (auto-lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Test Project", "description": "For linking tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add the member to the project as a lead so they can link
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"" + projectLeadMemberId + "\"}"))
        .andExpect(status().isCreated());

    // Promote member to lead by transferring lead role
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/projects/" + projectId + "/members/" + projectLeadMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    // Additional orgs for cross-tenant isolation tests (37.12)
    provisioningService.provisionTenant(STARTER_A_ID, "Starter A CustProj");
    provisioningService.provisionTenant(STARTER_B_ID, "Starter B CustProj");

    syncMember(
        STARTER_A_ID, "user_cp_starter_a", "cp_starter_a@test.com", "Starter A Owner", "owner");
    syncMember(
        STARTER_B_ID, "user_cp_starter_b", "cp_starter_b@test.com", "Starter B Owner", "owner");
  }

  // --- Linking happy path ---

  @Test
  void shouldLinkCustomerToProject() throws Exception {
    var customerId = createCustomer("Link Corp", "link@test.com");

    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.linkedBy").exists())
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void shouldRejectDuplicateLink() throws Exception {
    var customerId = createCustomer("Dup Link Corp", "duplink@test.com");

    // First link succeeds
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // Second link should be 409
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldUnlinkCustomerFromProject() throws Exception {
    var customerId = createCustomer("Unlink Corp", "unlink@test.com");

    // Link first
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // Unlink
    mockMvc
        .perform(delete("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldListProjectsForCustomer() throws Exception {
    var customerId = createCustomer("List Projects Corp", "listprojects@test.com");

    // Link customer to project
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // List projects for customer
    mockMvc
        .perform(get("/api/customers/" + customerId + "/projects").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[*].name", hasItem("Link Test Project")));
  }

  @Test
  void shouldListCustomersForProject() throws Exception {
    var customerId = createCustomer("Project Customers Corp", "projcust@test.com");

    // Link customer to project
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // List customers for project
    mockMvc
        .perform(get("/api/projects/" + projectId + "/customers").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[*].name", hasItem("Project Customers Corp")));
  }

  // --- RBAC ---

  @Test
  void memberCannotLinkWithoutProjectLeadRole() throws Exception {
    var customerId = createCustomer("RBAC Link Corp", "rbaclink@test.com");

    // Create a second project where the member is NOT a lead
    var project2Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "No Lead Project", "description": "Member not lead"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var project2Id = extractIdFromLocation(project2Result);

    // Member (not a project member at all) cannot link
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + project2Id).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void projectLeadCanLink() throws Exception {
    var customerId = createCustomer("Lead Link Corp", "leadlink@test.com");

    // The member is a project lead on projectId (set up in @BeforeAll)
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(memberJwt()))
        .andExpect(status().isCreated());
  }

  // --- Error cases ---

  @Test
  void shouldReturn404WhenUnlinkingNonExistentLink() throws Exception {
    var customerId = createCustomer("No Link Corp", "nolink@test.com");

    // Never linked — unlink should return 404
    mockMvc
        .perform(delete("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenLinkingNonExistentCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/00000000-0000-0000-0000-000000000000/projects/" + projectId)
                .with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenLinkingNonExistentProject() throws Exception {
    var customerId = createCustomer("Bad Project Corp", "badproject@test.com");

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/00000000-0000-0000-0000-000000000000")
                .with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Tenant isolation (Pro tier, Task 37.11) ---

  @Test
  void cannotLinkCrossTenant() throws Exception {
    // Create customer in tenant A
    var customerId = createCustomer("Cross Tenant Corp", "crosstenant@test.com");

    // Create project in tenant B
    var tenantBProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(tenantBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant B Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var tenantBProjectId = extractIdFromLocation(tenantBProjectResult);

    // Tenant B cannot see tenant A's customer to link it
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + tenantBProjectId)
                .with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Shared schema isolation (Starter tier, Task 37.12) ---

  @Test
  void starterOrgsCustomersAreIsolated() throws Exception {
    // Create customer in Starter A
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(starterAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Customer", "email": "starter_a_cust@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var customerIdA = extractIdFromLocation(createResult);

    // Starter A can see it
    mockMvc
        .perform(get("/api/customers/" + customerIdA).with(starterAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Starter A Customer"));

    // Starter B cannot see it
    mockMvc
        .perform(get("/api/customers/" + customerIdA).with(starterBOwnerJwt()))
        .andExpect(status().isNotFound());

    // Starter B's customer list does not include Starter A's customer
    mockMvc
        .perform(get("/api/customers").with(starterBOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Starter A Customer"))));
  }

  @Test
  void starterOrgsCanLinkIndependently() throws Exception {
    // Create project + customer in Starter A
    var projectResultA =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(starterAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Link Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var projectIdA = extractIdFromLocation(projectResultA);

    var customerResultA =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(starterAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Linker", "email": "starter_a_linker@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var customerIdA = extractIdFromLocation(customerResultA);

    // Link in Starter A
    mockMvc
        .perform(
            post("/api/customers/" + customerIdA + "/projects/" + projectIdA)
                .with(starterAOwnerJwt()))
        .andExpect(status().isCreated());

    // List projects for customer in Starter A
    mockMvc
        .perform(get("/api/customers/" + customerIdA + "/projects").with(starterAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Starter A Link Project"));

    // Starter B cannot see Starter A's customer or project
    mockMvc
        .perform(get("/api/customers/" + customerIdA + "/projects").with(starterBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void starterOrgsCannotCrossLink() throws Exception {
    // Create project in Starter A
    var projectResultA =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(starterAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Cross Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var projectIdA = extractIdFromLocation(projectResultA);

    // Create customer in Starter B
    var customerResultB =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(starterBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter B Cross Customer", "email": "starter_b_cross@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var customerIdB = extractIdFromLocation(customerResultB);

    // Starter B cannot link their customer to Starter A's project (project not visible)
    mockMvc
        .perform(
            post("/api/customers/" + customerIdB + "/projects/" + projectIdA)
                .with(starterBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

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
        .jwt(j -> j.subject("user_cp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cp_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cp_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cp_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor starterAOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_cp_starter_a")
                    .claim("o", Map.of("id", STARTER_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor starterBOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_cp_starter_b")
                    .claim("o", Map.of("id", STARTER_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
