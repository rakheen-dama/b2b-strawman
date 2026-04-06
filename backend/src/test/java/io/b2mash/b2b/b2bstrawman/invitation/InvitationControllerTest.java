package io.b2mash.b2b.b2bstrawman.invitation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.ProblemDetailAssertions;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationControllerTest {
  private static final String ORG_ID = "org_invitation_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PendingInvitationRepository invitationRepository;

  private String tenantSchema;
  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;
  private String adminRoleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Ctrl Test Org", null);

    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inv_owner", "inv_owner@test.com", "Inv Owner", "owner");
    adminMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inv_admin", "inv_admin@test.com", "Inv Admin", "admin");
    memberMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inv_member", "inv_member@test.com", "Inv Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Get the admin role ID for creating invitations
    adminRoleId =
        ScopedValue.where(
                io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> orgRoleRepository.findBySlug("admin").orElseThrow().getId().toString());
  }

  @Test
  void createInvitation_admin_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "newuser@example.com",
                      "orgRoleId": "%s"
                    }
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.roleName").exists())
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void createInvitation_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_inv_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "forbidden@example.com",
                      "orgRoleId": "%s"
                    }
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createInvitation_duplicateEmail_returns409() throws Exception {
    String email = "duplicate_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    // First invitation should succeed
    mockMvc
        .perform(
            post("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)))
        .andExpect(status().isCreated());

    // Second invitation for same email should fail with 409
    var result =
        mockMvc.perform(
            post("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)));
    ProblemDetailAssertions.assertProblem(result, HttpStatus.CONFLICT, "Pending invitation exists");
  }

  @Test
  void createInvitation_existingMember_returns409() throws Exception {
    // inv_owner@test.com is already a member
    mockMvc
        .perform(
            post("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "inv_owner@test.com", "orgRoleId": "%s"}
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isConflict());
  }

  @Test
  void listInvitations_admin_returnsList() throws Exception {
    String email = "list_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    // Create an invitation
    mockMvc
        .perform(
            post("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)))
        .andExpect(status().isCreated());

    // List invitations
    mockMvc
        .perform(get("/api/invitations").with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invitations").isArray());
  }

  @Test
  void listInvitations_withStatusFilter_returnsList() throws Exception {
    mockMvc
        .perform(
            get("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .param("status", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invitations").isArray());
  }

  @Test
  void listInvitations_invalidStatusFilter_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/invitations")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                .param("status", "INVALID_STATUS"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void revokeInvitation_admin_returns204() throws Exception {
    String email = "revoke_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "orgRoleId": "%s"}
                        """
                            .formatted(email, adminRoleId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invitationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            delete("/api/invitations/" + invitationId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin")))
        .andExpect(status().isNoContent());
  }

  @Test
  void revokeInvitation_alreadyRevoked_returns400() throws Exception {
    String email = "double_revoke_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "orgRoleId": "%s"}
                        """
                            .formatted(email, adminRoleId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invitationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // First revoke succeeds
    mockMvc
        .perform(
            delete("/api/invitations/" + invitationId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin")))
        .andExpect(status().isNoContent());

    // Second revoke fails (already revoked)
    mockMvc
        .perform(
            delete("/api/invitations/" + invitationId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void revokeInvitation_notFound_returns404() throws Exception {
    var result =
        mockMvc.perform(
            delete("/api/invitations/" + UUID.randomUUID())
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_admin")));
    ProblemDetailAssertions.assertProblem(
        result, HttpStatus.NOT_FOUND, "PendingInvitation not found");
  }
}
