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
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
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
class ProjectCustomerLinkIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proj_cust_link";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String activeCustomerId;
  private String prospectCustomerId;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Customer Link Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_pcl_owner", "pcl_owner@test.com", "PCL Owner", "owner");

    // Create an ACTIVE customer
    activeCustomerId = createCustomerAndActivate("Active Customer", "pcl_active@test.com");

    // Create a PROSPECT customer (default lifecycle status)
    prospectCustomerId = createCustomer("Prospect Customer", "pcl_prospect@test.com");
  }

  // --- 205.1: Create project with customerId and dueDate ---

  @Test
  void shouldCreateProjectWithCustomerLink() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Linked Project", "description": "With customer",
                     "customerId": "%s", "dueDate": "2026-06-30"}
                    """
                        .formatted(activeCustomerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(activeCustomerId))
        .andExpect(jsonPath("$.dueDate").value("2026-06-30"));
  }

  @Test
  void shouldCreateProjectWithoutCustomerLink() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Standalone Project", "description": "No customer"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").isEmpty())
        .andExpect(jsonPath("$.dueDate").isEmpty());
  }

  @Test
  void shouldRejectCreateProjectWithNonExistentCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Bad Link", "description": "Nonexistent customer",
                     "customerId": "00000000-0000-0000-0000-000000000999"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectCreateProjectWithProspectCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Prospect Link", "description": "Prospect customer",
                     "customerId": "%s"}
                    """
                        .formatted(prospectCustomerId)))
        .andExpect(status().isBadRequest());
  }

  // --- 205.2: Update project with customerId and dueDate ---

  @Test
  void shouldUpdateProjectWithCustomerLink() throws Exception {
    String projectId = createProject("Update Link Test");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Update Link Test", "description": "Now linked",
                     "customerId": "%s", "dueDate": "2026-12-31"}
                    """
                        .formatted(activeCustomerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(activeCustomerId))
        .andExpect(jsonPath("$.dueDate").value("2026-12-31"));
  }

  @Test
  void shouldUnlinkCustomerFromProject() throws Exception {
    // Create project with customer
    String projectId = createProjectWithCustomer("Unlink Test", activeCustomerId);

    // Update without customerId (unlink)
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Unlink Test", "description": "Unlinked"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").isEmpty());
  }

  @Test
  void shouldRejectUpdateProjectWithNonExistentCustomer() throws Exception {
    String projectId = createProject("Update Bad Link");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Update Bad Link", "description": "Nonexistent customer",
                     "customerId": "00000000-0000-0000-0000-000000000999"}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- 205.5: Filter projects by customerId ---

  @Test
  void shouldFilterProjectsByCustomerId() throws Exception {
    // Create two projects: one linked, one not
    createProjectWithCustomer("Filtered Linked", activeCustomerId);
    createProject("Filtered Standalone");

    mockMvc
        .perform(
            get("/api/projects")
                .with(ownerJwt())
                .param("status", "ALL")
                .param("customerId", activeCustomerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[?(@.customerId == '%s')]".formatted(activeCustomerId)).exists());
  }

  // --- 205.3: DTO fields present in response ---

  @Test
  void shouldCreateProjectWithDueDateOnly() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Due Date Only", "description": "Has due date",
                     "dueDate": "2026-03-15"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dueDate").value("2026-03-15"))
        .andExpect(jsonPath("$.customerId").isEmpty());
  }

  // --- Helpers ---

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Test project"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createProjectWithCustomer(String name, String custId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Linked project", "customerId": "%s"}
                        """
                            .formatted(name, custId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createCustomerAndActivate(String name, String email) throws Exception {
    String custId = createCustomer(name, email);

    // Transition PROSPECT -> ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());

    // Complete all checklist items (auto-transitions ONBOARDING -> ACTIVE)
    TestChecklistHelper.completeChecklistItems(mockMvc, custId, ownerJwt());

    return custId;
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
        .jwt(j -> j.subject("user_pcl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
