package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for project-side customer linking endpoints (POST/DELETE on
 * /api/projects/{projectId}/customers/{customerId}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectCustomerControllerIntegrationTest {
  private static final String ORG_ID = "org_projcust_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;
  private String leadMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "ProjCust Ctrl Test Org", null);

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pcc_owner", "pcc_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pcc_admin", "pcc_admin@test.com", "Admin", "admin");
    leadMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pcc_lead", "pcc_lead@test.com", "Lead", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pcc_member", "pcc_member@test.com", "Member", "member");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "ProjCust Link Project", "description": "For project-side linking"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add lead member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"" + leadMemberId + "\"}"))
        .andExpect(status().isCreated());

    // Promote to lead
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/projects/" + projectId + "/members/" + leadMemberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldLinkCustomerToProjectViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("ProjLink Corp", "projlink@test.com");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
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
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldUnlinkCustomerViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("UnlinkProj Corp", "unlinkproj@test.com");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldReturn404WhenUnlinkingNonLinkedCustomer() throws Exception {
    var customerId = createCustomer("NeverLinkedProj Corp", "neverlinkedproj@test.com");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "No Lead Project 2", "description": "Member not lead here"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var project2Id = TestEntityHelper.extractIdFromLocation(project2Result);

    mockMvc
        .perform(
            post("/api/projects/" + project2Id + "/customers/" + customerId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pcc_member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void projectLeadCanLinkViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("LeadProjLink Corp", "leadprojlink@test.com");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pcc_lead")))
        .andExpect(status().isCreated());
  }

  @Test
  void adminCanLinkViaProjectEndpoint() throws Exception {
    var customerId = createCustomer("AdminProjLink Corp", "adminprojlink@test.com");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pcc_admin")))
        .andExpect(status().isCreated());
  }

  // --- Helpers ---

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    var customerId = TestEntityHelper.extractIdFromLocation(result);
    transitionCustomerToActive(customerId);
    return customerId;
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner"));
  }
}
