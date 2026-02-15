package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Integration tests for project-side customer linking endpoints (POST/DELETE on
 * /api/projects/{projectId}/customers/{customerId}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectCustomerControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_projcust_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String leadMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "ProjCust Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember("user_pcc_owner", "pcc_owner@test.com", "Owner", "owner");
    syncMember("user_pcc_admin", "pcc_admin@test.com", "Admin", "admin");
    leadMemberId = syncMember("user_pcc_lead", "pcc_lead@test.com", "Lead", "member");
    syncMember("user_pcc_member", "pcc_member@test.com", "Member", "member");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "ProjCust Link Project", "description": "For project-side linking"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add lead member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"" + leadMemberId + "\"}"))
        .andExpect(status().isCreated());

    // Promote to lead
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/projects/" + projectId + "/members/" + leadMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldLinkCustomerToProjectViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("ProjLink Corp", "projlink@test.com");

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.linkedBy").exists())
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void shouldRejectDuplicateLinkViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("DupProjLink Corp", "dupprojlink@test.com");

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldUnlinkCustomerViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("UnlinkProj Corp", "unlinkproj@test.com");

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldReturn404WhenUnlinkingNonLinkedCustomer() throws Exception {
    var customerId = createCustomer("NeverLinkedProj Corp", "neverlinkedproj@test.com");

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void memberCannotLinkWithoutLeadRole() throws Exception {
    var customerId = createCustomer("NoLeadProjLink Corp", "noleadprojlink@test.com");

    // Create a second project where the member is NOT a lead
    var project2Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "No Lead Project 2", "description": "Member not lead here"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var project2Id = extractIdFromLocation(project2Result);

    mockMvc
        .perform(post("/api/projects/" + project2Id + "/customers/" + customerId).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void projectLeadCanLinkViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("LeadProjLink Corp", "leadprojlink@test.com");

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(leadJwt()))
        .andExpect(status().isCreated());
  }

  @Test
  void adminCanLinkViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("AdminProjLink Corp", "adminprojlink@test.com");

    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(adminJwt()))
        .andExpect(status().isCreated());
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
    var customerId = extractIdFromLocation(result);

    // Transition customer to ACTIVE for lifecycle guard compliance
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING", "notes": "test setup"}
                    """))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE", "notes": "test setup"}
                    """))
        .andExpect(status().isOk());

    return customerId;
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pcc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pcc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor leadJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pcc_lead").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pcc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
